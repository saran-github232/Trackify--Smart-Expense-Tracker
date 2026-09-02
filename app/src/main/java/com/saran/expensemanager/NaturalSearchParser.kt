package com.saran.expensemanager

import java.util.Calendar

/**
 * Lifts simple filters (amount thresholds, a category, a month/relative date) out of a plain
 * search string — e.g. "food above 5000 in august" — and hands back what's left for the existing
 * substring search to match against title/category/notes. Every field is optional; a query with
 * none of these patterns behaves exactly like plain substring search (remainingText == input).
 */
object NaturalSearchParser {
    data class Filter(
        val amountAbove: Double? = null,
        val amountBelow: Double? = null,
        val category: String? = null,
        val datePrefix: String? = null, // yyyy-MM
        val dateExact: String? = null,  // yyyy-MM-dd
        val remainingText: String,
    )

    private val categories = listOf("Food", "Travel", "Shopping", "Bills", "Health", "Entertainment", "Education", "Other")

    private val monthNames = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )

    private val aboveRegex = Regex("(above|over|more than|greater than)\\s*(\\d+(?:\\.\\d+)?)")
    private val belowRegex = Regex("(below|under|less than)\\s*(\\d+(?:\\.\\d+)?)")
    private val yearRegex = Regex("\\b(20\\d{2})\\b")

    fun parse(rawQuery: String): Filter {
        var text = " ${rawQuery.lowercase().trim()} "

        val above = aboveRegex.find(text)?.also { text = text.replace(it.value, " ") }
            ?.groupValues?.get(2)?.toDoubleOrNull()
        val below = belowRegex.find(text)?.also { text = text.replace(it.value, " ") }
            ?.groupValues?.get(2)?.toDoubleOrNull()

        var category: String? = null
        for (cat in categories) {
            if (Regex("\\b${cat.lowercase()}\\b").containsMatchIn(text)) {
                category = cat
                text = text.replace(cat.lowercase(), " ")
                break
            }
        }

        var datePrefix: String? = null
        var dateExact: String? = null
        val cal = Calendar.getInstance()
        when {
            Regex("\\btoday\\b").containsMatchIn(text) -> {
                dateExact = "%04d-%02d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
                text = text.replace("today", " ")
            }
            Regex("\\byesterday\\b").containsMatchIn(text) -> {
                cal.add(Calendar.DAY_OF_MONTH, -1)
                dateExact = "%04d-%02d-%02d".format(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
                text = text.replace("yesterday", " ")
            }
            else -> {
                for ((name, monthNum) in monthNames) {
                    if (Regex("\\b$name\\b").containsMatchIn(text)) {
                        val yearMatch = yearRegex.find(text)
                        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull() ?: cal[Calendar.YEAR]
                        datePrefix = "%04d-%02d".format(year, monthNum)
                        text = text.replace(name, " ")
                        yearMatch?.let { text = text.replace(it.value, " ") }
                        break
                    }
                }
            }
        }

        return Filter(above, below, category, datePrefix, dateExact, text.trim().replace(Regex("\\s+"), " "))
    }
}
