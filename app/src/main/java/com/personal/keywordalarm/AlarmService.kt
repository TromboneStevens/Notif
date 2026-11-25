package com.personal.keywordalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Foreground service that performs the actual notification: vibrate + alarm sound,
 * and holds a wake lock so the screen stays on and detection keeps working.
 */
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_START = "com.personal.keywordalarm.START"
        const val ACTION_STOP = "com.personal.keywordalarm.STOP"
        const val EXTRA_KEYWORD = "keyword"

        private const val CHANNEL_ID = "keyword_alarm_channel"
        private const val NOTIF_ID = 42
        private const val WAKELOCK_TAG = "KeywordAlarm::ScreenWakeLock"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vibrator = getVibrator()
        acquireScreenWakeLockIfEnabled()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val keyword = intent?.getStringExtra(EXTRA_KEYWORD) ?: ""
                startForeground(NOTIF_ID, buildNotification(keyword))
                startAlarm()
            }
        }
        // Keep the service alive so the wake lock + alarm persist.
        return START_STICKY
    }

    /**
     * A partial wake lock keeps the CPU running; combined with the foreground
     * notification's full-screen behaviour and the keep-awake flag, the screen
     * is turned on and kept on so detection is not missed.
     */
    private fun acquireScreenWakeLockIfEnabled() {
        if (!Prefs.isKeepAwake(this)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            WAKELOCK_TAG
        )
        wl.setReferenceCounted(false)
        // Safety timeout so a stuck lock can't drain the battery forever (30 min).
        wl.acquire(30 * 60 * 1000L)
        screenWakeLock = wl
        Log.i(TAG, "Screen wake lock acquired")
    }

    private fun startAlarm() {
        startVibration()
        startSound()
    }

    private fun startVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0, 600, 400) // wait, buzz, pause -> repeats
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    private fun startSound() {
        try {
            var alarmUri: Uri? =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            // Make sure the alarm stream is audible.
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound", e)
        }
    }

    private fun stopAlarm() {
        vibrator?.cancel()
        mediaPlayer?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        mediaPlayer = null
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        screenWakeLock?.let { if (it.isHeld) it.release() }
        screenWakeLock = null
    }

    private fun buildNotification(keyword: String): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (keyword.isNotEmpty())
            getString(R.string.notif_detected, keyword)
        else
            getString(R.string.notif_detected_generic)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openPending)
            .setFullScreenIntent(openPending, true) // turns the screen on
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_alarm),
                stopPending
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarm_channel_desc)
                enableVibration(true)
                setBypassDnd(true)
                // Sound handled by MediaPlayer so we can loop; silence the channel.
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun getVibrator(): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
