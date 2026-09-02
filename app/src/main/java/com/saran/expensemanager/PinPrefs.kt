package com.saran.expensemanager

import android.content.Context

class PinPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)

    val isPinEnabled: Boolean get() = prefs.getBoolean("pin_enabled", false)
    val hasPin: Boolean get() = prefs.contains("pin_hash")

    fun setPin(pin: String) {
        prefs.edit()
            .putString("pin_hash", sha256(pin))
            .putBoolean("pin_enabled", true)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString("pin_hash", "") ?: return false
        return stored.isNotEmpty() && stored == sha256(pin)
    }

    fun disablePin() {
        prefs.edit()
            .remove("pin_hash")
            .putBoolean("pin_enabled", false)
            .apply()
    }

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
