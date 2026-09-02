package com.saran.expensemanager

import android.content.Context
import java.text.NumberFormat

/**
 * Display-only currency formatting driven by [CurrencyPrefs]. This changes how amounts are
 * *shown* app-wide; it does not convert or re-price historical transactions, and there is no
 * per-transaction currency/FX-rate tracking — every amount in the database is still just a number.
 */
object CurrencyFormatter {
    fun currencyInstance(context: Context): NumberFormat =
        NumberFormat.getCurrencyInstance(CurrencyPrefs(context).locale)

    fun format(context: Context, amount: Double): String = currencyInstance(context).format(amount)
}
