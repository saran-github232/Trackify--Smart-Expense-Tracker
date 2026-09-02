package com.saran.expensemanager

data class Recurring(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val dayOfMonth: Int,
    val notes: String = "",
    val isSubscription: Boolean = false,
)
