package com.example.yearprogresswidget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * BootReceiver
 *
 * Listens for BOOT_COMPLETED and MY_PACKAGE_REPLACED so the TickerService
 * is restarted automatically after a device reboot or app update, without
 * requiring the user to open the app.
 *
 * Also triggers a fresh frame push for both widget types so they show
 * accurate data immediately after boot (before the first tick fires).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        // Push fresh frames for all Year Progress widget instances
        runCatching {
            val mgr   = AppWidgetManager.getInstance(ctx)
            val ypIds = mgr.getAppWidgetIds(ComponentName(ctx, YearProgressWidget::class.java))
            for (id in ypIds) YearProgressWidget.update(ctx, mgr, id)
        }

        // Start the ticker (it will push Flip Clock frames itself)
        runCatching {
            FlipClockWidget.startTicker(ctx)
        }
    }
}