package com.saran.expensemanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps [ShakeDetector] running while the app isn't in the foreground, so shake-to-add also works
 * from the home screen, lock screen, or inside other apps. Android only allows continuous sensor
 * access outside your own foreground Activity through a foreground service, which is why this
 * needs a permanent low-priority notification — that's a platform requirement, not a choice.
 */
class ShakeOverlayService : Service() {

    private lateinit var shakePrefs: ShakePrefs
    private lateinit var shakeDetector: ShakeDetector

    override fun onCreate() {
        super.onCreate()
        shakePrefs = ShakePrefs(this)
        shakeDetector = ShakeDetector(this) { onShake() }
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
        startActivity(
            Intent(this, QuickAddOverlayActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
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
    }

    companion object {
        private const val CHANNEL_ID = "shake_service"
        private const val NOTIF_ID = 3001

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
