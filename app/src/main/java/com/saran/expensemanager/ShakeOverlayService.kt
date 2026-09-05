package com.saran.expensemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps [ShakeDetector] running while the app isn't in the foreground, so shake-to-add also works
 * from the home screen, lock screen, or inside other apps. On a shake it pops the
 * [QuickAddBannerView] as a system overlay window (via [QuickAddOverlay]) — startActivity() from
 * a background service is blocked by Android 10+ background-activity-start restrictions, which is
 * why the old version only ever showed its notification. Android only allows continuous sensor
 * access outside your own foreground Activity through a foreground service, which is why this
 * needs a permanent low-priority notification — that's a platform requirement, not a choice.
 */
class ShakeOverlayService : Service() {

    private lateinit var shakePrefs: ShakePrefs
    private lateinit var shakeDetector: ShakeDetector
    private var permissionNudgeShown = false

    override fun onCreate() {
        super.onCreate()
        shakePrefs = ShakePrefs(this)
        shakeDetector = ShakeDetector(this) { onShake() }
        permissionNudgeShown = false
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!shakePrefs.enabled || !shakeDetector.isAvailable) {
            stopSelf()
            return START_NOT_STICKY
        }
        shakeDetector.thresholdG = shakePrefs.thresholdG
        shakeDetector.start()
        return START_STICKY
    }

    override fun onDestroy() {
        shakeDetector.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onShake() {
        if (ShakeSuppressor.suppressed) return
        if (shakePrefs.vibrate) vibrate()
        // The card pops as a system overlay window — never startActivity(), which Android 10+
        // blocks from background services and which would yank the user into the app. Without
        // the overlay permission, nudge once per service start instead of nagging every shake.
        if (QuickAddOverlay.show(this)) return
        Toast.makeText(this, R.string.shake_overlay_missing_toast, Toast.LENGTH_SHORT).show()
        if (!permissionNudgeShown) {
            permissionNudgeShown = true
            notifyGrantOverlayPermission()
        }
    }

    /**
     * One-time heads-up nudge (per service start) for when the "Display over other apps"
     * permission is missing. It opens the permission toggle, NOT the app — a shake should never
     * launch an activity.
     */
    private fun notifyGrantOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasPermission(this)
        ) return
        val openPermissionSettings = PendingIntent.getActivity(
            this, 1,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, FALLBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(getString(R.string.shake_overlay_permission_title))
            .setContentText(getString(R.string.shake_notif_overlay_body))
            .setContentIntent(openPermissionSettings)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(FALLBACK_NOTIF_ID, notif)
    }

    private fun vibrate() {
        val vibrator = getSystemService(android.os.Vibrator::class.java) ?: return
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(getString(R.string.shake_service_notif_title))
            .setContentText(getString(R.string.shake_service_notif_body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.shake_service_notif_title), NotificationManager.IMPORTANCE_MIN)
            )
        }
        if (manager.getNotificationChannel(FALLBACK_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(FALLBACK_CHANNEL_ID, getString(R.string.shake_overlay_permission_title), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "shake_service"
        private const val FALLBACK_CHANNEL_ID = "shake_alert"
        private const val NOTIF_ID = 3001
        private const val FALLBACK_NOTIF_ID = 3002

        /** Starts (or updates the running config of) the background shake listener. */
        fun start(context: Context) {
            val intent = Intent(context, ShakeOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeOverlayService::class.java))
        }
    }
}
