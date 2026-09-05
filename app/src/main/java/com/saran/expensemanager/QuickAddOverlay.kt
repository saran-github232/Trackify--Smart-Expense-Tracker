package com.saran.expensemanager

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

/**
 * Shows the [QuickAddBannerView] as a real system overlay window (TYPE_APPLICATION_OVERLAY), so
 * the shake-to-add card can pop over the home screen, the lock screen, or any other app — the
 * thing startActivity() can no longer do from a background service on Android 10+.
 *
 * The window is deliberately NOT focusable while on the amount/category steps (so it doesn't
 * steal focus or break the lock screen); the banner flips the flag itself when the remarks step
 * needs the system keyboard. Requires the "Display over other apps" permission — callers must
 * check [hasPermission] first and fall back to a notification if it's missing.
 */
object QuickAddOverlay {

    private var bannerView: QuickAddBannerView? = null
    private var containerView: FrameLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Pops the banner over whatever is on screen. Returns false (and does nothing) when the
     * overlay permission is missing or a banner is already showing.
     */
    fun show(context: Context): Boolean {
        if (bannerView != null) return true // already showing
        if (!hasPermission(context)) return false

        mainHandler.post { showInternal(context.applicationContext) }
        return true
    }

    private fun showInternal(context: Context) {
        if (bannerView != null || !hasPermission(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        wakeScreenIfLocked(context)

        // Wrapper frame gives the card side margins, which WindowManager params can't do.
        val density = context.resources.displayMetrics.density
        val container = FrameLayout(context).apply {
            setPadding((12 * density).toInt(), (24 * density).toInt(), (12 * density).toInt(), 0)
        }
        val banner = QuickAddBannerView(context).apply {
            host = object : QuickAddBannerView.Host {
                override fun onBannerFinished(saved: Boolean) {
                    dismiss(context)
                }
            }
        }
        container.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_APPLICATION_OVERLAY needs API 26; TYPE_PHONE covers older devices — all pre-8.0
            // builds predate the background-activity-start restrictions anyway.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            dimAmount = 0.25f
        }

        runCatching { wm.addView(container, params) }
            .onFailure { return }

        // Tap outside the card dismisses it (FLAG_WATCH_OUTSIDE_TOUCH delivers ACTION_OUTSIDE).
        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                dismiss(context)
                true
            } else {
                false
            }
        }

        containerView = container
        bannerView = banner
        animateIn(container)
        // Safety net: never let an abandoned banner sit on the lock screen forever.
        mainHandler.postDelayed({ dismiss(context) }, AUTO_DISMISS_TIMEOUT_MS)
    }

    /** Slides the card down from the top edge, like a notification sheet. */
    private fun animateIn(container: FrameLayout) {
        val distance = container.resources.displayMetrics.density * 48f
        container.translationY = -distance
        container.alpha = 0f
        container.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun dismiss(context: Context) {
        mainHandler.removeCallbacksAndMessages(null)
        val container = containerView ?: return
        // Clear references first so a re-shake during the exit animation can start fresh.
        containerView = null
        bannerView = null
        if (!container.isAttachedToWindow) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val distance = container.resources.displayMetrics.density * 48f
        container.animate()
            .translationY(-distance)
            .alpha(0f)
            .setDuration(160L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { runCatching { wm.removeView(container) } }
            .start()
    }

    /** Lightly wakes the display when the shake happens with the screen off (lock screen pop). */
    private fun wakeScreenIfLocked(context: Context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (power.isInteractive) return
        @Suppress("DEPRECATION")
        val wakeLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "expensemanager:QuickAddOverlayWake",
        )
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private const val WAKE_LOCK_TIMEOUT_MS = 5_000L
    private const val AUTO_DISMISS_TIMEOUT_MS = 3 * 60_000L
}
