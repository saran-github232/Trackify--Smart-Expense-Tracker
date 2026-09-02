package com.saran.expensemanager

import android.content.Context

/** Most-recently-used categories, newest first, for the quick-select chips in Add Expense. */
object RecentCategories {
    private const val PREFS = "recent_categories"
    private const val KEY = "recent"
    private const val MAX = 5

    fun get(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")!!
            .split(",")
            .filter { it.isNotBlank() }

    fun record(context: Context, category: String) {
        if (category.isBlank()) return
        val updated = (listOf(category) + get(context).filterNot { it == category }).take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, updated.joinToString(",")).apply()
    }
}
