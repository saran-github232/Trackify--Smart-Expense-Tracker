package com.saran.expensemanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.saran.expensemanager.databinding.ActivityRecurringBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecurringActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityRecurringBinding
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: RecurringAdapter

    private val categories = listOf(
        "Food", "Travel", "Shopping", "Bills", "Health", "Entertainment", "Education", "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecurringBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.rvRecurring.applyBottomSystemBarInsetPadding()
        binding.fabAddRecurring.applyBottomSystemBarInsetMargin()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        db = DatabaseHelper.getInstance(this)
        setupRecyclerView()
        setupSwipeToDelete()
        binding.fabAddRecurring.setOnClickListener { showAddDialog() }
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = RecurringAdapter { item -> deleteWithUndo(item) }
        binding.rvRecurring.layoutManager = LinearLayoutManager(this)
        binding.rvRecurring.adapter = adapter
    }

    private fun setupSwipeToDelete() {
        val icon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)!!
        val bg = ColorDrawable(Color.parseColor("#EF4444"))

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.adapterPosition
                if (pos < 0 || pos >= adapter.currentList.size) return
                deleteWithUndo(adapter.currentList[pos])
            }
            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isActive: Boolean) {
                val v = vh.itemView
                val margin = (v.height - icon.intrinsicHeight) / 2
                bg.setBounds(v.right + dX.toInt(), v.top, v.right, v.bottom)
                bg.draw(c)
                icon.setBounds(v.right - margin - icon.intrinsicWidth, v.top + margin, v.right - margin, v.top + margin + icon.intrinsicHeight)
                icon.draw(c)
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }
        ItemTouchHelper(swipe).attachToRecyclerView(binding.rvRecurring)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.getAllRecurring() }
            adapter.submitList(list)
            binding.llEmptyRecurring.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.rvRecurring.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun deleteWithUndo(item: Recurring) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.deleteRecurring(item.id) }
            loadData()
            Snackbar.make(binding.root, getString(R.string.recurring_deleted), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.undo)) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.addRecurring(item.copy(id = 0)) }
                        loadData()
                    }
                }.show()
        }
    }

    private fun showAddDialog() {
        val dp = resources.displayMetrics.density
        val px16 = (16 * dp).toInt()
        val px8 = (8 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px16 * 2, px16, px16 * 2, 0)
        }

        fun field(hint: String, inputType: Int): Pair<TextInputLayout, TextInputEditText> {
            val til = TextInputLayout(
                this, null, com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                this.hint = hint
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = px8 * 2 }
            }
            val et = TextInputEditText(til.context).apply { this.inputType = inputType }
            til.addView(et)
            return til to et
        }

        val (tilTitle, etTitle) = field(getString(R.string.hint_title), android.text.InputType.TYPE_CLASS_TEXT)
        val (tilAmount, etAmount) = field(
            getString(R.string.hint_amount),
            android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val (tilDay, etDay) = field(
            getString(R.string.hint_day_of_month),
            android.text.InputType.TYPE_CLASS_NUMBER
        )

        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val spinner = Spinner(this).apply {
            adapter = catAdapter
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()
            ).also { it.bottomMargin = px8 * 2 }
        }

        container.addView(tilTitle)
        container.addView(tilAmount)
        container.addView(spinner)
        container.addView(tilDay)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_recurring))
            .setView(container)
            .setPositiveButton(getString(R.string.budget_save)) { _, _ ->
                val title = etTitle.text.toString().trim()
                val amount = etAmount.text.toString().trim().toDoubleOrNull()
                val day = etDay.text.toString().trim().toIntOrNull()
                val category = categories[spinner.selectedItemPosition]
                if (title.isNotEmpty() && amount != null && amount > 0 && day != null && day in 1..28) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            db.addRecurring(Recurring(title = title, amount = amount, category = category, dayOfMonth = day))
                        }
                        loadData()
                    }
                } else {
                    Snackbar.make(binding.root, getString(R.string.error_fill_all_fields), Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

