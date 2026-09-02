package com.saran.expensemanager

import android.content.Context

class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("name", "") ?: ""
        set(value) { prefs.edit().putString("name", value).apply() }

    val isOnboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_done", false)

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_done", true).apply()
    }
}
