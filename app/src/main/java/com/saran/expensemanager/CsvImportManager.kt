package com.saran.expensemanager

/**
 * Parses the same CSV shape ExpenseListFragment exports (Title,Amount,Category,Date,Notes),
 * case-insensitive header matching, falling back to positional columns if there's no header row.
 * Never touches the database itself — the caller inserts what comes back after showing counts.
 */
object CsvImportManager {
    data class Result(val imported: List<Expense>, val skipped: Int)

    fun parse(csvText: String): Result {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return Result(emptyList(), 0)

        val header = splitCsvLine(lines[0]).map { it.trim().lowercase() }
        val titleCol = header.indexOf("title")
        val amountCol = header.indexOf("amount")
        val hasHeader = titleCol >= 0 && amountCol >= 0

        val titleIdx = if (hasHeader) titleCol else 0
        val amountIdx = if (hasHeader) amountCol else 1
        val categoryIdx = if (hasHeader) header.indexOf("category") else 2
        val dateIdx = if (hasHeader) header.indexOf("date") else 3
        val notesIdx = if (hasHeader) header.indexOf("notes") else 4

        val dataLines = if (hasHeader) lines.drop(1) else lines
        val imported = mutableListOf<Expense>()
        var skipped = 0

        for (line in dataLines) {
            val cols = splitCsvLine(line)
            val title = cols.getOrNull(titleIdx)?.trim().orEmpty()
            val amount = cols.getOrNull(amountIdx)?.trim()?.toDoubleOrNull()
            val category = cols.getOrNull(categoryIdx)?.trim().orEmpty().ifBlank { "Other" }
            val date = cols.getOrNull(dateIdx)?.trim().orEmpty()
            val notes = cols.getOrNull(notesIdx)?.trim().orEmpty()

            if (title.isEmpty() || amount == null || amount <= 0 || date.isEmpty()) {
                skipped++
                continue
            }
            imported += Expense(title = title, amount = amount, category = category, date = date, notes = notes)
        }
        return Result(imported, skipped)
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        result += sb.toString()
        return result
    }
}
