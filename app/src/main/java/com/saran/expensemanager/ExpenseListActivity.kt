package com.saran.expensemanager

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.saran.expensemanager.databinding.ActivityExpenseListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class ExpenseListActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityExpenseListBinding
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: ExpenseAdapter
    private var allExpenses = listOf<Expense>()

    private var fromDate = ""
    private var toDate = ""

    private val filterCategories = listOf(
        "All", "Food", "Travel", "Shopping", "Bills",
        "Health", "Entertainment", "Education", "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.rvExpenses.applyBottomSystemBarInsetPadding()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        db = DatabaseHelper.getInstance(this)
        setupRecyclerView()
        setupSwipeToDelete()
        setupSearch()
        setupCategoryFilter()
        setupSortButtons()
        setupDateRangeFilter()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) { db.getAllExpenses() }
            allExpenses = expenses
            applyFilters()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_expense_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { exportToCSV(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        adapter = ExpenseAdapter(
            onEdit = { expense ->
                startActivity(Intent(this, EditExpenseActivity::class.java).apply {
                    putExtra("expense_id", expense.id)
                    putExtra("expense_title", expense.title)
                    putExtra("expense_amount", expense.amount)
                    putExtra("expense_category", expense.category)
                    putExtra("expense_date", expense.date)
                    putExtra("expense_notes", expense.notes)
                })
            },
            onDelete = { expense -> confirmDelete(expense) }
        )
        binding.rvExpenses.layoutManager = LinearLayoutManager(this)
        binding.rvExpenses.adapter = adapter
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_delete)!!
        val background = ColorDrawable(Color.parseColor("#EF4444"))

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val list = adapter.currentList
                val pos = vh.adapterPosition
                if (pos < 0 || pos >= list.size) return
                val expense = list[pos]

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.deleteExpense(expense.id) }
                    reload()

                    Snackbar.make(binding.root, getString(R.string.expense_deleted), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.undo)) {
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) { db.addExpense(expense.copy(id = 0)) }
                                reload()
                            }
                        }
                        .setAnchorView(binding.root)
                        .show()
                }
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isActive: Boolean
            ) {
                val item = vh.itemView
                val iconMargin = (item.height - deleteIcon.intrinsicHeight) / 2
                background.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
                background.draw(c)
                val iconLeft = item.right - iconMargin - deleteIcon.intrinsicWidth
                val iconTop = item.top + iconMargin
                deleteIcon.setBounds(iconLeft, iconTop, item.right - iconMargin, iconTop + deleteIcon.intrinsicHeight)
                deleteIcon.draw(c)
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive)
            }
        }
        ItemTouchHelper(swipe).attachToRecyclerView(binding.rvExpenses)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilters()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupCategoryFilter() {
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterCategories)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = catAdapter
        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = applyFilters()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupSortButtons() {
        binding.btnSortDate.setOnClickListener {
            adapter.submitList(getFilteredList().sortedByDescending { it.date })
        }
        binding.btnSortAmount.setOnClickListener {
            adapter.submitList(getFilteredList().sortedByDescending { it.amount })
        }
        binding.btnClearFilters.setOnClickListener {
            fromDate = ""
            toDate = ""
            binding.etFromDate.setText("")
            binding.etToDate.setText("")
            binding.etSearch.setText("")
            binding.spinnerCategory.setSelection(0)
            applyFilters()
        }
    }

    private fun setupDateRangeFilter() {
        binding.etFromDate.setOnClickListener { showDatePicker(isFrom = true) }
        binding.etToDate.setOnClickListener { showDatePicker(isFrom = false) }
    }

    private fun showDatePicker(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val dateStr = "%04d-%02d-%02d".format(y, m + 1, d)
            if (isFrom) {
                fromDate = dateStr
                binding.etFromDate.setText(dateStr)
            } else {
                toDate = dateStr
                binding.etToDate.setText(dateStr)
            }
            applyFilters()
        }, cal[Calendar.YEAR], cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH]).show()
    }

    private fun applyFilters() {
        val list = getFilteredList()
        adapter.submitList(list)
        val empty = list.isEmpty()
        binding.llEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvExpenses.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun getFilteredList(): List<Expense> {
        val query = binding.etSearch.text.toString().trim().lowercase()
        val selectedCat = filterCategories[binding.spinnerCategory.selectedItemPosition]
        return allExpenses.filter { e ->
            val catOk = selectedCat == "All" || e.category == selectedCat
            val queryOk = query.isEmpty() ||
                    e.title.lowercase().contains(query) ||
                    e.category.lowercase().contains(query) ||
                    e.notes.lowercase().contains(query)
            val dateOk = when {
                fromDate.isNotEmpty() && toDate.isNotEmpty() -> e.date >= fromDate && e.date <= toDate
                fromDate.isNotEmpty() -> e.date >= fromDate
                toDate.isNotEmpty() -> e.date <= toDate
                else -> true
            }
            catOk && queryOk && dateOk
        }
    }

    private fun exportToCSV() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val expenses = db.getAllExpenses()
                if (expenses.isEmpty()) return@withContext null

                val csv = buildString {
                    appendLine("Title,Amount,Category,Date,Notes")
                    expenses.forEach { e ->
                        val title = "\"${e.title.replace("\"", "\"\"")}\""
                        val notes = "\"${e.notes.replace("\"", "\"\"")}\""
                        appendLine("$title,${e.amount},${e.category},${e.date},$notes")
                    }
                }

                try {
                    val file = File(cacheDir, "expenses_export.csv")
                    file.writeText(csv)
                    FileProvider.getUriForFile(this@ExpenseListActivity, "${packageName}.provider", file)
                } catch (e: Exception) {
                    e.message ?: "Export failed"
                }
            }

            when (result) {
                null -> Snackbar.make(binding.root, getString(R.string.export_no_data), Snackbar.LENGTH_SHORT).show()
                is Uri -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, result)
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.export_csv)))
                }
                is String -> Snackbar.make(binding.root, result, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(expense: Expense) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.deleteExpense(expense.id) }
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

