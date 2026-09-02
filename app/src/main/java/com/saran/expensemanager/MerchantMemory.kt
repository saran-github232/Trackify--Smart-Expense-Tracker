package com.saran.expensemanager

import android.content.Context

/**
 * Lightweight local "you typed this title before, here's what category you picked" memory.
 * No cloud AI — just a title(lowercase) -> category SharedPreferences map, updated on every save.
 */
object MerchantMemory {
    private const val PREFS = "merchant_memory"

    fun remember(context: Context, title: String, category: String) {
        val key = title.trim().lowercase()
        if (key.isEmpty() || category.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, category).apply()
    }

    fun suggest(context: Context, title: String): String? {
        val key = title.trim().lowercase()
        if (key.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(key, null)?.let { return it }
        return prefs.all.entries.firstOrNull { (k, _) -> key.contains(k) || k.contains(key) }?.value as? String
    }
}
