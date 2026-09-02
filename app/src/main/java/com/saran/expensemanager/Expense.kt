package com.saran.expensemanager

data class Expense(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val date: String,
    val notes: String = "",
    val syncStatus: String = DatabaseHelper.SYNC_PENDING,
)
