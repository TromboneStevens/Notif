package com.personal.keywordalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Lightweight foreground service that runs while monitoring is enabled. Its only
 * job is to show a persistent, low-priority banner with a "Stop monitoring"
 * action so monitoring can be turned off from the notification shade.
 *
 * This is separate from [AlarmService], which only runs while an alarm is firing.
 */
class MonitoringService : Service() {

    companion object {
        const val ACTION_START = "com.personal.keywordalarm.MONITOR_START"
        const val ACTION_STOP = "com.personal.keywordalarm.MONITOR_STOP"

        private const val CHANNEL_ID = "keyword_monitoring_channel"
        private const val NOTIF_ID = 7

        /** Start the banner service (monitoring on). */
        fun start(ctx: Context) {
            val intent = Intent(ctx, MonitoringService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        /** Stop the banner service (monitoring off). */
        fun stop(ctx: Context) {
            val intent = Intent(ctx, MonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            ctx.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // User tapped "Stop monitoring" (or the app requested a stop).
                Prefs.setEnabled(this, false)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification())
            }
        }
        // Restart if killed so the banner persists while monitoring is on.
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        // Tapping the banner opens the app.
        val openPending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // The action button stops monitoring.
        val stopPending = PendingIntent.getService(
            this, 0,
            Intent(this, MonitoringService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.monitoring_title))
            .setContentText(getString(R.string.monitoring_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_monitoring),
                stopPending
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.monitoring_channel_name),
                NotificationManager.IMPORTANCE_LOW // silent, no sound/vibration
            ).apply {
                description = getString(R.string.monitoring_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
