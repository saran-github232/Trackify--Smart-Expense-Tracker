package com.saran.expensemanager

data class ParsedReceipt(val amount: Double?, val date: String?, val rawText: String)

/**
 * Best-effort extraction from OCR'd receipt text. Never auto-saves — the result only pre-fills
 * Add Expense, which the user reviews before saving (low-confidence fields are simply left blank
 * so the required-field validation forces the user to fill them in).
 */
object ReceiptParser {
    private val totalLineRegex = Regex("(?i)(total|grand\\s*total|amount\\s*due|net\\s*payable)[^0-9]{0,10}([0-9]+(?:[.,][0-9]{2})?)")
    private val anyAmountRegex = Regex("[0-9]+(?:[.,][0-9]{2})?")
    private val dateRegex = Regex("(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})")

    fun parse(text: String): ParsedReceipt {
        val amount = totalLineRegex.find(text)?.groupValues?.get(2)?.replace(",", "")?.toDoubleOrNull()
            ?: anyAmountRegex.findAll(text)
                .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
                .filter { it in 1.0..500000.0 }
                .maxOrNull()

        val date = dateRegex.find(text)?.let { m ->
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) "20$y" else y
            runCatching { "%04d-%02d-%02d".format(year.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }

        return ParsedReceipt(amount, date, text)
    }
}
