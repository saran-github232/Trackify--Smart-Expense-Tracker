package com.saran.expensemanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Low-noise local notifications for budget threshold alerts and recurring-expense reminders. */
object NotificationHelper {
    private const val CHANNEL_ID = "reminders"
    private const val NOTIF_BUDGET = 1001
    private const val NOTIF_RECURRING_BASE = 2000

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders & Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission") // hasPermission() is checked by every caller before this runs
    fun postBudgetAlert(context: Context, percentUsed: Int) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(context.getString(R.string.budget_alert_title))
            .setContentText(context.getString(R.string.budget_alert_body, percentUsed))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_BUDGET, notification)
    }

    @SuppressLint("MissingPermission")
    fun postRecurringReminder(context: Context, id: Long, title: String, daysUntil: Int) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val body = if (daysUntil == 0) {
            context.getString(R.string.recurring_reminder_today, title)
        } else {
            context.getString(R.string.recurring_reminder_body, title, daysUntil)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wallet)
            .setContentTitle(context.getString(R.string.recurring_reminder_title))
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify((NOTIF_RECURRING_BASE + id).toInt(), notification)
    }
}
