package com.saran.expensemanager

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.saran.expensemanager.databinding.ActivityDashboardBinding
import com.saran.expensemanager.databinding.ItemRecentTransactionBinding
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var db: DatabaseHelper
    private lateinit var budgetPrefs: BudgetPrefs
    private val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    private val categoryColors = mapOf(
        "Food" to "#FF6B6B",
        "Travel" to "#4ECDC4",
        "Shopping" to "#45B7D1",
        "Bills" to "#FFA07A",
        "Health" to "#66BB6A",
        "Entertainment" to "#BA68C8",
        "Education" to "#42A5F5",
        "Other" to "#90A4AE"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper.getInstance(this)
        budgetPrefs = BudgetPrefs(this)
        binding.tvGreeting.text = getGreeting()

        binding.fabAddExpense.setOnClickListener {
            startActivity(Intent(this, AddExpenseActivity::class.java))
        }
        binding.btnViewExpenses.setOnClickListener {
            startActivity(Intent(this, ExpenseListActivity::class.java))
        }
        binding.btnAnalytics.setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        binding.btnAddIncome.setOnClickListener {
            startActivity(Intent(this, AddIncomeActivity::class.java))
        }
        binding.btnRecurring.setOnClickListener {
            startActivity(Intent(this, RecurringActivity::class.java))
        }
        binding.cardBudget.setOnClickListener { showBudgetDialog() }
        binding.btnSetBudget.setOnClickListener { showBudgetDialog() }
    }

    override fun onResume() {
        super.onResume()
        applyRecurringExpenses()
        refreshStats()
    }

    private fun applyRecurringExpenses() {
        val recurring = db.getAllRecurring()
        if (recurring.isEmpty()) return
        val cal = Calendar.getInstance()
        val today = cal[Calendar.DAY_OF_MONTH]
        val yearMonth = "%04d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1)
        recurring.forEach { rec ->
            if (today >= rec.dayOfMonth && !db.hasExpenseForMonth(rec.title, yearMonth)) {
                val date = "$yearMonth-%02d".format(rec.dayOfMonth)
                db.addExpense(
                    Expense(title = rec.title, amount = rec.amount, category = rec.category, date = date, notes = rec.notes)
                )
            }
        }
    }

    private fun refreshStats() {
        val totalExpenses = db.getTotalAmount()
        val totalIncome = db.getTotalIncome()
        val net = totalIncome - totalExpenses

        binding.tvTotalAmount.text = fmt.format(totalExpenses)
        binding.tvTotalTransactions.text = db.getTotalCount().toString()
        binding.tvMonthAmount.text = fmt.format(db.getCurrentMonthTotal())

        // Financial overview
        binding.tvTotalIncome.text = fmt.format(totalIncome)
        binding.tvOverviewExpenses.text = fmt.format(totalExpenses)
        binding.tvNetBalance.text = fmt.format(abs(net))
        val netColor = if (net >= 0) Color.parseColor("#22C55E")
                       else ContextCompat.getColor(this, R.color.md3_error)
        binding.tvNetBalance.setTextColor(netColor)

        val monthIncome = db.getCurrentMonthIncome()
        val monthExpenses = db.getCurrentMonthTotal()
        if (monthIncome > 0) {
            val rate = ((monthIncome - monthExpenses) / monthIncome * 100).toInt().coerceAtLeast(0)
            binding.tvSavingsInfo.text = getString(R.string.savings_rate_info, rate)
            binding.tvSavingsInfo.visibility = View.VISIBLE
        } else {
            binding.tvSavingsInfo.visibility = View.GONE
        }

        // Smart insight
        binding.tvInsight.text = generateInsight()

        // Category breakdown
        val cats = db.getCategoryTotals()
        binding.tvCategorySummary.text = if (cats.isEmpty()) {
            getString(R.string.no_expenses_yet)
        } else {
            cats.entries.joinToString("\n") { (cat, amt) -> "• $cat:  ${fmt.format(amt)}" }
        }

        updateBudgetCard()
        loadRecentTransactions()
    }

    private fun generateInsight(): String {
        val total = db.getTotalCount()
        if (total == 0) return getString(R.string.insight_welcome)

        val thisMonth = db.getCurrentMonthTotal()
        val lastMonth = db.getLastMonthTotal()

        if (lastMonth > 0 && thisMonth > 0) {
            val pct = ((thisMonth - lastMonth) / lastMonth * 100).toInt()
            if (pct >= 20) return getString(R.string.insight_spending_up, pct)
            if (pct <= -20) return getString(R.string.insight_spending_down, -pct)
        }

        val monthIncome = db.getCurrentMonthIncome()
        if (monthIncome > 0) {
            return if (thisMonth > monthIncome) getString(R.string.insight_over_income)
            else getString(R.string.insight_savings_rate, ((monthIncome - thisMonth) / monthIncome * 100).toInt())
        }

        val topCat = db.getCategoryTotals().keys.firstOrNull()
        return if (topCat != null) getString(R.string.insight_top_category, topCat)
        else getString(R.string.insight_on_track)
    }

    private fun updateBudgetCard() {
        val budget = budgetPrefs.monthlyBudget
        val spent = db.getCurrentMonthTotal()

        if (!budgetPrefs.hasBudget) {
            binding.llBudgetInfo.visibility = View.GONE
            binding.progressBudget.visibility = View.GONE
            binding.tvBudgetStatus.text = getString(R.string.budget_not_set)
            binding.btnSetBudget.text = getString(R.string.set_budget)
            return
        }

        val pct = ((spent / budget) * 100).toInt().coerceAtLeast(0)
        binding.llBudgetInfo.visibility = View.VISIBLE
        binding.progressBudget.visibility = View.VISIBLE
        binding.tvBudgetSpent.text = fmt.format(spent)
        binding.tvBudgetLimit.text = fmt.format(budget)
        binding.progressBudget.progress = pct.coerceAtMost(100)
        binding.btnSetBudget.text = getString(R.string.edit_budget)

        val (color, statusText) = when {
            pct > 100 -> Pair(
                ContextCompat.getColor(this, R.color.md3_error),
                getString(R.string.budget_exceeded, fmt.format(spent - budget))
            )
            pct >= 80 -> Pair(Color.parseColor("#F59E0B"), getString(R.string.budget_warning, pct))
            else -> Pair(Color.parseColor("#22C55E"), getString(R.string.budget_on_track, pct))
        }
        binding.progressBudget.setIndicatorColor(color)
        binding.tvBudgetStatus.setTextColor(color)
        binding.tvBudgetStatus.text = statusText
    }

    private fun showBudgetDialog() {
        val inputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = getString(R.string.budget_hint)
            setPadding(56, 24, 56, 8)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (budgetPrefs.hasBudget) {
                val v = budgetPrefs.monthlyBudget
                setText(if (v % 1.0 == 0.0) v.toLong().toString() else v.toString())
                selectAll()
            }
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.budget_dialog_title)
            .setView(inputLayout)
            .setPositiveButton(R.string.budget_save) { _, _ ->
                val amount = editText.text.toString().trim().toDoubleOrNull()
                if (amount != null && amount > 0) {
                    budgetPrefs.monthlyBudget = amount
                    updateBudgetCard()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadRecentTransactions() {
        val recent = db.getRecentExpenses(5)
        binding.llRecentTransactions.removeAllViews()

        if (recent.isEmpty()) {
            binding.tvNoRecent.visibility = View.VISIBLE
            return
        }
        binding.tvNoRecent.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        recent.forEach { expense ->
            val row = ItemRecentTransactionBinding.inflate(inflater, binding.llRecentTransactions, false)
            row.tvRecentTitle.text = expense.title
            row.tvRecentAmount.text = fmt.format(expense.amount)

            val color = categoryColors[expense.category] ?: "#90A4AE"
            row.vDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
            }
            binding.llRecentTransactions.addView(row.root)

            if (expense != recent.last()) {
                val divider = View(this)
                divider.layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1
                )
                divider.setBackgroundColor("#12000000".toColorInt())
                binding.llRecentTransactions.addView(divider)
            }
        }
    }

    private fun getGreeting(): String = when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
        in 0..11 -> getString(R.string.greeting_morning)
        in 12..17 -> getString(R.string.greeting_afternoon)
        else -> getString(R.string.greeting_evening)
    }
}
