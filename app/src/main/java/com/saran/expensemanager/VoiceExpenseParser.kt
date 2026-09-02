package com.saran.expensemanager

data class ParsedVoiceExpense(val amount: Double?, val category: String, val title: String)

/**
 * Turns a spoken sentence like "spent 500 on groceries at D-Mart" into a best-effort guess at
 * amount/category/title. Deliberately simple keyword + regex matching — no cloud NLP — the result
 * always lands in AddExpenseActivity for the user to review and correct before saving.
 */
object VoiceExpenseParser {

    private val categoryKeywords = linkedMapOf(
        "Food" to listOf("lunch", "dinner", "breakfast", "food", "restaurant", "swiggy", "zomato", "snack", "coffee", "tea"),
        "Travel" to listOf("uber", "ola", "cab", "taxi", "auto", "bus", "train", "flight", "fuel", "petrol", "diesel", "travel", "airport"),
        "Shopping" to listOf("grocery", "groceries", "mart", "shopping", "amazon", "flipkart", "clothes", "shoes"),
        "Bills" to listOf("bill", "electricity", "recharge", "rent", "emi", "internet", "wifi"),
        "Health" to listOf("doctor", "medicine", "pharmacy", "hospital", "health"),
        "Entertainment" to listOf("movie", "netflix", "spotify", "cinema", "game", "entertainment"),
        "Education" to listOf("course", "book", "fee", "tuition", "school", "college", "education"),
    )

    private val amountRegex = Regex("\\d+(\\.\\d+)?")

    fun parse(text: String): ParsedVoiceExpense {
        val lower = text.lowercase()
        val amount = amountRegex.find(lower)?.value?.toDoubleOrNull()
        val category = categoryKeywords.entries
            .firstOrNull { (_, keywords) -> keywords.any { lower.contains(it) } }
            ?.key ?: "Other"
        val title = text.trim().replaceFirstChar { it.uppercase() }.take(60).ifBlank { category }
        return ParsedVoiceExpense(amount, category, title)
    }
}
