package com.saran.expensemanager

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Base Activity that opts every screen into edge-to-edge drawing explicitly, rather than relying
 * on Android 15's automatic enforcement for targetSdk 35+ apps. This keeps behavior identical on
 * every OS version and replaces the deprecated statusBarColor/windowLightStatusBar theme attributes
 * (ignored starting API 35) with the WindowInsetsController equivalent.
 */
abstract class EdgeToEdgeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
    }
}

private fun WindowInsetsCompat.systemBars() = getInsets(WindowInsetsCompat.Type.systemBars())

/** Pads the view by the status/nav bar inset on top of whatever padding it already had in XML. */
fun View.applyTopSystemBarInsetPadding() {
    val basePaddingTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(top = basePaddingTop + insets.systemBars().top)
        insets
    }
}

fun View.applyBottomSystemBarInsetPadding() {
    val basePaddingBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(bottom = basePaddingBottom + insets.systemBars().bottom)
        insets
    }
}

/** Same as [applyBottomSystemBarInsetPadding] but grows the bottom margin instead (for FABs etc). */
fun View.applyBottomSystemBarInsetMargin() {
    val baseMarginBottom = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = baseMarginBottom + insets.systemBars().bottom
        }
        insets
    }
}
