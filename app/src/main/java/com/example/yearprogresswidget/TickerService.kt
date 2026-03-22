package com.example.yearprogresswidget

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat

class TickerService : Service() {

    private lateinit var bgThread: HandlerThread
    private lateinit var bg: Handler
    private var running = false

    companion object {
        private const val NOTIF_ID     = 1
        private const val CHANNEL_ID   = "flip_clock_ch"
        private const val CHANNEL_NAME = "Flip Clock"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // Wrap startForeground in try/catch — on Android 14+
        // starting from background throws ForegroundServiceStartNotAllowedException.
        // If that happens we degrade gracefully: service runs without foreground
        // status (may be killed eventually, but won't crash the app).
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            // Cannot start foreground right now — continue without it
            e.printStackTrace()
        }

        bgThread = HandlerThread("TickerBg")
        bgThread.start()
        bg = Handler(bgThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            // Try promoting to foreground again in case onCreate failed
            try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
            postNextTick()
        }
        return START_STICKY
    }

    private fun postNextTick() {
        if (!running) return
        val now  = SystemClock.uptimeMillis()
        val next = ((now / 1000L) + 1L) * 1000L
        bg.postAtTime(tick, next)
    }

    private val tick = Runnable {
        if (!running) return@Runnable
        try {
            val mgr  = AppWidgetManager.getInstance(this@TickerService)
            val comp = ComponentName(this@TickerService, FlipClockWidget::class.java)
            val ids  = mgr.getAppWidgetIds(comp)
            for (id in ids) FlipClockWidget.pushFrame(this@TickerService, mgr, id)
        } catch (_: Exception) {}
        postNextTick()
    }

    override fun onDestroy() {
        running = false
        if (::bg.isInitialized)       bg.removeCallbacksAndMessages(null)
        if (::bgThread.isInitialized) bgThread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flip Clock")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false); setSound(null, null) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(chan)
        }
    }
}