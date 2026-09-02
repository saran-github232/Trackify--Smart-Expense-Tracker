package com.saran.expensemanager

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.saran.expensemanager.databinding.FragmentIncomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

class IncomeFragment : Fragment() {

    private var _binding: FragmentIncomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: DatabaseHelper
    private lateinit var adapter: IncomeAdapter
    private lateinit var fmt: NumberFormat

    private val sourceColors = mapOf(
        "Salary" to "#4CAF50",
        "Freelance" to "#2196F3",
        "Business" to "#FF9800",
        "Investment" to "#9C27B0",
        "Rental" to "#00BCD4",
        "Gift" to "#E91E63",
        "Other" to "#9E9E9E"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIncomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appBar.applyTopSystemBarInsetPadding()
        binding.toolbar.navigationIcon = null
        db = DatabaseHelper.getInstance(requireContext())
        fmt = CurrencyFormatter.currencyInstance(requireContext())

        adapter = IncomeAdapter(
            onDelete = { income -> confirmDelete(income) },
            onEdit = { income -> launchEditIncome(income) }
        )
        binding.rvIncome.layoutManager = LinearLayoutManager(requireContext())
        binding.rvIncome.adapter = adapter
        binding.rvIncome.isNestedScrollingEnabled = false

        setupSwipeToDelete()

        binding.fabAddIncome.setOnClickListener {
            startActivity(Intent(requireContext(), AddIncomeActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete)!!
        val background = ColorDrawable(Color.parseColor("#EF4444"))

        val swipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val list = adapter.currentList
                val pos = vh.adapterPosition
                if (pos < 0 || pos >= list.size) return
                val income = list[pos]

                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.deleteIncome(income.id) }
                    loadData()

                    Snackbar.make(binding.root, getString(R.string.income_deleted), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.undo)) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                withContext(Dispatchers.IO) { db.addIncome(income.copy(id = 0)) }
                                loadData()
                            }
                        }.show()
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
        ItemTouchHelper(swipe).attachToRecyclerView(binding.rvIncome)
    }

    private data class IncomeScreenData(
        val list: List<Income>,
        val totalAll: Double,
        val thisMonth: Double,
        val sources: Map<String, Double>
    )

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                IncomeScreenData(
                    list = db.getAllIncome(),
                    totalAll = db.getTotalIncome(),
                    thisMonth = db.getCurrentMonthIncome(),
                    sources = db.getIncomeBySource()
                )
            }
            if (_binding == null) return@launch
            bindIncomeData(data)
        }
    }

    private fun bindIncomeData(data: IncomeScreenData) {
        binding.tvIncomeTotalAll.text = fmt.format(data.totalAll)
        binding.tvIncomeThisMonth.text = fmt.format(data.thisMonth)
        binding.tvIncomeCount.text = getString(R.string.income_count, data.list.size)
        binding.tvIncomeListCount.text = "${data.list.size} records"

        adapter.submitList(data.list)

        val empty = data.list.isEmpty()
        binding.llIncomeEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvIncome.visibility = if (empty) View.GONE else View.VISIBLE

        bindSourceBreakdown(data.sources)
    }

    private fun bindSourceBreakdown(sources: Map<String, Double>) {
        val grandTotal = sources.values.sum()
        binding.llSourceBreakdown.removeAllViews()

        if (sources.isEmpty()) {
            binding.tvSourceEmpty.visibility = View.VISIBLE
            binding.llSourceBreakdown.visibility = View.GONE
            return
        }
        binding.tvSourceEmpty.visibility = View.GONE
        binding.llSourceBreakdown.visibility = View.VISIBLE

        sources.entries.forEach { (source, amount) ->
            val pct = if (grandTotal > 0) (amount / grandTotal * 100).toInt() else 0
            val colorHex = sourceColors[source] ?: "#9E9E9E"
            binding.llSourceBreakdown.addView(buildSourceRow(source, amount, pct, Color.parseColor(colorHex)))
        }
    }

    private fun buildSourceRow(source: String, amount: Double, pct: Int, color: Int): View {
        val dp = resources.displayMetrics.density
        val px8 = (8 * dp).toInt()
        val px12 = (12 * dp).toInt()

        val row = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, px8, 0, px8)
        }

        val badge = View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                (10 * dp).toInt(), (10 * dp).toInt()
            ).also { it.marginEnd = px12 }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }

        val labelLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val tvSource = android.widget.TextView(requireContext()).apply {
            text = source
            setTextColor(ContextCompat.getColor(requireContext(), R.color.md3_on_surface))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val progressBar = android.widget.ProgressBar(
            requireContext(), null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (6 * dp).toInt()
            ).also { it.topMargin = (3 * dp).toInt() }
            max = 100
            progress = pct
            progressTintList = android.content.res.ColorStateList.valueOf(color)
        }

        labelLayout.addView(tvSource)
        labelLayout.addView(progressBar)

        val tvAmount = android.widget.TextView(requireContext()).apply {
            text = "${fmt.format(amount)}\n$pct%"
            gravity = android.view.Gravity.END
            textSize = 12f
            setTextColor(color)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        row.addView(badge)
        row.addView(labelLayout)
        row.addView(tvAmount)
        return row
    }

    private fun launchEditIncome(income: Income) {
        startActivity(
            android.content.Intent(requireContext(), EditIncomeActivity::class.java).apply {
                putExtra(EditIncomeActivity.EXTRA_ID, income.id)
                putExtra(EditIncomeActivity.EXTRA_TITLE, income.title)
                putExtra(EditIncomeActivity.EXTRA_AMOUNT, income.amount)
                putExtra(EditIncomeActivity.EXTRA_SOURCE, income.source)
                putExtra(EditIncomeActivity.EXTRA_DATE, income.date)
                putExtra(EditIncomeActivity.EXTRA_NOTES, income.notes)
                putExtra(EditIncomeActivity.EXTRA_TYPE, income.type)
            }
        )
    }

    private fun confirmDelete(income: Income) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Income")
            .setMessage("Remove \"${income.title}\"? This cannot be undone.")
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.deleteIncome(income.id) }
                    loadData()
                    Snackbar.make(binding.root, getString(R.string.income_deleted), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.undo)) {
                            viewLifecycleOwner.lifecycleScope.launch {
                                withContext(Dispatchers.IO) { db.addIncome(income.copy(id = 0)) }
                                loadData()
                            }
                        }.show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
