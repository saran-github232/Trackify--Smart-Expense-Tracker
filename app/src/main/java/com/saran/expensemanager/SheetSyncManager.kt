package com.saran.expensemanager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pushes expenses to the user's own Google Apps Script web app (see GOOGLE_SHEETS_SETUP.md).
 * Every call is a no-op unless sync is enabled and a URL is configured, so this is safe to call
 * opportunistically (after save, on app resume) without the caller checking state first.
 */
object SheetSyncManager {

    private val monthFmt = SimpleDateFormat("MMM", Locale.ENGLISH)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Fire-and-forget: kicks off [syncPending] on its own background scope, independent of any UI lifecycle. */
    fun triggerSync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { syncPending(appContext) }
    }

    /** Fire-and-forget: kicks off [deleteRemote] on its own background scope. */
    fun triggerDelete(context: Context, expenseId: Long) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { deleteRemote(appContext, expenseId) }
    }

    /** Pushes every not-yet-synced expense, one request per row (upserted server-side by local id). */
    suspend fun syncPending(context: Context) = withContext(Dispatchers.IO) {
        val prefs = SheetSyncPrefs(context)
        if (!prefs.enabled || !prefs.isConfigured || !isOnline(context)) return@withContext

        val db = DatabaseHelper.getInstance(context)
        for (expense in db.getPendingSyncExpenses()) {
            val ok = runCatching { postRow(prefs.webAppUrl, prefs.secretToken, expense) }.getOrDefault(false)
            db.setExpenseSyncStatus(
                expense.id,
                if (ok) DatabaseHelper.SYNC_SYNCED else DatabaseHelper.SYNC_FAILED,
            )
        }
    }

    /** Best-effort removal of a row that had already synced; safe to call even if it hadn't. */
    suspend fun deleteRemote(context: Context, expenseId: Long) = withContext(Dispatchers.IO) {
        val prefs = SheetSyncPrefs(context)
        if (!prefs.enabled || !prefs.isConfigured || !isOnline(context)) return@withContext
        runCatching {
            postJson(
                prefs.webAppUrl,
                JSONObject().put("action", "delete").put("token", prefs.secretToken).put("localId", expenseId),
            )
        }
    }

    suspend fun testConnection(url: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(postJson(url, JSONObject().put("action", "ping").put("token", token)))
            if (json.optBoolean("success")) {
                json.optString("message").ifBlank { "Connected!" }
            } else {
                error(json.optString("message").ifBlank { "Rejected by script — check the URL and token" })
            }
        }
    }

    private fun postRow(url: String, token: String, expense: Expense): Boolean {
        val month = runCatching { monthFmt.format(dateFmt.parse(expense.date)!!) }.getOrDefault("")
        val body = JSONObject().apply {
            put("action", "sync")
            put("token", token)
            put("localId", expense.id)
            put("date", expense.date)
            put("month", month)
            put("category", expense.category)
            put("amount", expense.amount)
            put("remarks", expense.notes)
        }
        return JSONObject(postJson(url, body)).optBoolean("success")
    }

    private fun postJson(urlString: String, body: JSONObject): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            conn.disconnect()
        }
    }
}
