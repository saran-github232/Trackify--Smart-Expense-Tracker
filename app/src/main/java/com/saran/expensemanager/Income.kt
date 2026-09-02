package com.saran.expensemanager

data class Income(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val source: String,
    val date: String,
    val notes: String = "",
    val type: String = DatabaseHelper.INCOME_TYPE_INCOME,
)
