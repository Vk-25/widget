package com.example.yearprogresswidget

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * TickerService — drives the flip-clock at exactly 1 Hz.
 *
 * Also refreshes the Year Progress widget at midnight (day rollover)
 * so the dot grid and day count update exactly when the new day starts,
 * without waiting for the 30-minute periodic update.
 */
class TickerService : Service() {

    private lateinit var bgThread: HandlerThread
    private lateinit var bg: Handler
    private var running = false
    private var lastRenderedSecond = -1L
    private var lastRenderedDay    = -1   // tracks day so we catch midnight rollover

    companion object {
        private const val NOTIF_ID     = 1
        private const val CHANNEL_ID   = "flip_clock_ch"
        private const val CHANNEL_NAME = "Flip Clock"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tryStartForeground()
        bgThread = HandlerThread("TickerBg", Process.THREAD_PRIORITY_BACKGROUND)
        bgThread.start()
        bg = Handler(bgThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tryStartForeground()
        if (!running) { running = true; scheduleNextTick() }
        return START_STICKY
    }

    private fun scheduleNextTick() {
        if (!running) return
        val nowEpoch  = System.currentTimeMillis()
        val nowUptime = SystemClock.uptimeMillis()
        val nextEpoch = ((nowEpoch / 1_000L) + 1L) * 1_000L
        val delta     = (nextEpoch - nowEpoch).coerceIn(50, 1100)
        bg.postAtTime(tick, nowUptime + delta)
    }

    private val tick = Runnable {
        if (!running) return@Runnable
        try {
            val nowSec = System.currentTimeMillis() / 1_000L
            if (nowSec != lastRenderedSecond) {
                lastRenderedSecond = nowSec

                // ── Flip Clock: update every second ──────────────────────────
                val mgr      = AppWidgetManager.getInstance(this@TickerService)
                val clockComp = ComponentName(this@TickerService, FlipClockWidget::class.java)
                for (id in mgr.getAppWidgetIds(clockComp))
                    FlipClockWidget.pushFrame(this@TickerService, mgr, id)

                // ── Year Progress: update only when the day changes ──────────
                val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (today != lastRenderedDay) {
                    lastRenderedDay = today
                    val ypComp = ComponentName(this@TickerService, YearProgressWidget::class.java)
                    for (id in mgr.getAppWidgetIds(ypComp))
                        YearProgressWidget.update(this@TickerService, mgr, id)
                }
            }
        } catch (_: Exception) {}
        scheduleNextTick()
    }

    override fun onDestroy() {
        running = false
        if (::bg.isInitialized)       bg.removeCallbacksAndMessages(null)
        if (::bgThread.isInitialized) bgThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tryStartForeground() {
        try { startForeground(NOTIF_ID, buildNotification()) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flip Clock")
            .setContentText("Updating every second")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true).setOngoing(true).build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
                .apply { description = "Keeps Flip Clock widget current"; setShowBadge(false); setSound(null,null) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(chan)
        }
    }
}