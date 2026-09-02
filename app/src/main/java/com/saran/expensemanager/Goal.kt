package com.saran.expensemanager

data class Goal(
    val id: Long = 0,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val color: String = "#22C55E",
    val deadline: String = ""
) {
    val progress: Int
        get() = if (targetAmount > 0) ((savedAmount / targetAmount) * 100).toInt().coerceIn(0, 100) else 0

    val isCompleted: Boolean
        get() = savedAmount >= targetAmount && targetAmount > 0
}
