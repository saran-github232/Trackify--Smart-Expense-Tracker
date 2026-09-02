package com.saran.expensemanager

import android.content.Context

class SheetSyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sheet_sync_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var webAppUrl: String
        get() = prefs.getString("web_app_url", "") ?: ""
        set(value) = prefs.edit().putString("web_app_url", value.trim()).apply()

    var secretToken: String
        get() = prefs.getString("secret_token", "") ?: ""
        set(value) = prefs.edit().putString("secret_token", value.trim()).apply()

    val isConfigured: Boolean get() = webAppUrl.isNotBlank()
}
