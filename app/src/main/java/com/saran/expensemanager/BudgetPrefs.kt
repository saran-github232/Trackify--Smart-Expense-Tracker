package com.saran.expensemanager

import android.content.Context

class BudgetPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)

    var monthlyBudget: Double
        get() = prefs.getFloat("monthly_budget", 0f).toDouble()
        set(value) = prefs.edit().putFloat("monthly_budget", value.toFloat()).apply()

    val hasBudget: Boolean get() = monthlyBudget > 0
}
