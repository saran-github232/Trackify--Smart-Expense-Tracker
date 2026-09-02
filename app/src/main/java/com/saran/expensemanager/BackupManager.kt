package com.saran.expensemanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Full local backup/restore as plain JSON — no cloud, no account, just a file the user picks. */
object BackupManager {
    private const val VERSION = 1

    data class Counts(val expenses: Int, val income: Int, val recurring: Int, val goals: Int)

    fun export(context: Context): JSONObject {
        val db = DatabaseHelper.getInstance(context)
        return JSONObject().apply {
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("expenses", JSONArray(db.getAllExpenses().map { e ->
                JSONObject().apply {
                    put("title", e.title); put("amount", e.amount); put("category", e.category)
                    put("date", e.date); put("notes", e.notes); put("paymentMethod", e.paymentMethod)
                }
            }))
            put("income", JSONArray(db.getAllIncome().map { i ->
                JSONObject().apply {
                    put("title", i.title); put("amount", i.amount); put("source", i.source)
                    put("date", i.date); put("notes", i.notes); put("type", i.type)
                }
            }))
            put("recurring", JSONArray(db.getAllRecurring().map { r ->
                JSONObject().apply {
                    put("title", r.title); put("amount", r.amount); put("category", r.category)
                    put("dayOfMonth", r.dayOfMonth); put("notes", r.notes); put("isSubscription", r.isSubscription)
                }
            }))
            put("goals", JSONArray(db.getAllGoals().map { g ->
                JSONObject().apply {
                    put("name", g.name); put("targetAmount", g.targetAmount); put("savedAmount", g.savedAmount)
                    put("color", g.color); put("deadline", g.deadline)
                }
            }))
        }
    }

    fun counts(json: JSONObject) = Counts(
        json.optJSONArray("expenses")?.length() ?: 0,
        json.optJSONArray("income")?.length() ?: 0,
        json.optJSONArray("recurring")?.length() ?: 0,
        json.optJSONArray("goals")?.length() ?: 0,
    )

    /** Replaces ALL current data — call only after the user confirms, counts() is meant for that dialog. */
    fun restore(context: Context, json: JSONObject) {
        val db = DatabaseHelper.getInstance(context)
        db.clearAllData()

        json.optJSONArray("expenses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.addExpense(
                    Expense(
                        title = o.optString("title"), amount = o.optDouble("amount"),
                        category = o.optString("category"), date = o.optString("date"),
                        notes = o.optString("notes"), paymentMethod = o.optString("paymentMethod"),
                    )
                )
            }
        }
        json.optJSONArray("income")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.addIncome(
                    Income(
                        title = o.optString("title"), amount = o.optDouble("amount"),
                        source = o.optString("source"), date = o.optString("date"),
                        notes = o.optString("notes"),
                        type = o.optString("type", DatabaseHelper.INCOME_TYPE_INCOME),
                    )
                )
            }
        }
        json.optJSONArray("recurring")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.addRecurring(
                    Recurring(
                        title = o.optString("title"), amount = o.optDouble("amount"),
                        category = o.optString("category"), dayOfMonth = o.optInt("dayOfMonth"),
                        notes = o.optString("notes"), isSubscription = o.optBoolean("isSubscription"),
                    )
                )
            }
        }
        json.optJSONArray("goals")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.addGoal(
                    Goal(
                        name = o.optString("name"), targetAmount = o.optDouble("targetAmount"),
                        savedAmount = o.optDouble("savedAmount", 0.0),
                        color = o.optString("color", "#22C55E"), deadline = o.optString("deadline"),
                    )
                )
            }
        }
    }
}
