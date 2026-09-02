package com.saran.expensemanager

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: today/this-month totals + a direct "Add Expense" button. */
class ExpenseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ExpenseWidgetProvider::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val db = DatabaseHelper.getInstance(context)

            val views = RemoteViews(context.packageName, R.layout.widget_expense)
            views.setTextViewText(R.id.tvWidgetToday, CurrencyFormatter.format(context, db.getTodayTotal()))
            views.setTextViewText(R.id.tvWidgetMonth, CurrencyFormatter.format(context, db.getCurrentMonthTotal()))

            val addIntent = Intent(context, AddExpenseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.btnWidgetAdd,
                PendingIntent.getActivity(context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            val openIntent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widgetRoot,
                PendingIntent.getActivity(context, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )

            manager.updateAppWidget(widgetId, views)
        }
    }
}
