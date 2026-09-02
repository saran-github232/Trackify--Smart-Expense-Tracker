package com.saran.expensemanager

import android.content.Context

class ShakePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shake_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var sensitivity: Int
        get() = prefs.getInt("sensitivity", SENSITIVITY_MEDIUM)
        set(value) = prefs.edit().putInt("sensitivity", value).apply()

    var vibrate: Boolean
        get() = prefs.getBoolean("vibrate", true)
        set(value) = prefs.edit().putBoolean("vibrate", value).apply()

    val thresholdG: Float
        get() = when (sensitivity) {
            SENSITIVITY_LOW -> ShakeDetector.SENSITIVITY_LOW
            SENSITIVITY_HIGH -> ShakeDetector.SENSITIVITY_HIGH
            else -> ShakeDetector.SENSITIVITY_MEDIUM
        }

    companion object {
        const val SENSITIVITY_LOW = 0
        const val SENSITIVITY_MEDIUM = 1
        const val SENSITIVITY_HIGH = 2
    }
}
