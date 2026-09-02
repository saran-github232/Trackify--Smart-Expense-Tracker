package com.saran.expensemanager

import android.content.Context
import java.util.Locale

class CurrencyPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("currency_prefs", Context.MODE_PRIVATE)

    var currencyCode: String
        get() = prefs.getString("code", "INR") ?: "INR"
        set(value) = prefs.edit().putString("code", value).apply()

    val locale: Locale get() = SUPPORTED[currencyCode] ?: SUPPORTED.getValue("INR")

    companion object {
        /** Currency code -> a Locale whose NumberFormat produces that currency's symbol/grouping. */
        val SUPPORTED: LinkedHashMap<String, Locale> = linkedMapOf(
            "INR" to Locale.forLanguageTag("en-IN"),
            "USD" to Locale.US,
            "EUR" to Locale.GERMANY,
            "GBP" to Locale.UK,
            "AED" to Locale.forLanguageTag("ar-AE"),
            "SGD" to Locale.forLanguageTag("en-SG"),
        )
    }
}
