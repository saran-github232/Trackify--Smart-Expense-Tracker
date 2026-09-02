package com.saran.expensemanager

import android.content.Context

/** Thresholds/cooldown bookkeeping for budget-alert and recurring-reminder notifications. */
class ReminderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

    var recurringReminderDays: Int
        get() = prefs.getInt("recurring_days_before", 2)
        set(value) = prefs.edit().putInt("recurring_days_before", value).apply()

    var budgetAlertThreshold: Int
        get() = prefs.getInt("budget_alert_threshold", 90)
        set(value) = prefs.edit().putInt("budget_alert_threshold", value).apply()

    /** One-shot keys (e.g. "budget_2026-09", "recur_3_2026-09") so a given alert fires once. */
    fun wasAlerted(key: String): Boolean = prefs.getStringSet("alerted", emptySet())!!.contains(key)

    fun markAlerted(key: String) {
        val updated = (prefs.getStringSet("alerted", emptySet()) ?: emptySet()).toMutableSet().apply { add(key) }
        prefs.edit().putStringSet("alerted", updated).apply()
    }
}
