package com.fitness.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

class BreakTimerService : Service() {

    companion object {
        val elapsedSeconds = MutableStateFlow(0)
        val targetDuration = MutableStateFlow(0)
        val isRunning = MutableStateFlow(false)

        var exerciseName: String = ""
        var nextSetNumber: Int = 0
        var totalSets: Int = 0
        var setWeight: Double = 0.0
        var setReps: Int = 0

        const val ACTION_START = "com.fitness.app.BREAK_START"
        const val ACTION_SKIP = "com.fitness.app.BREAK_SKIP"
        const val ACTION_PASS = "com.fitness.app.SET_PASS"
        const val ACTION_FAIL = "com.fitness.app.SET_FAIL"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_EXERCISE = "exercise"
        const val EXTRA_NEXT_SET = "next_set"
        const val EXTRA_TOTAL_SETS = "total_sets"
        const val EXTRA_WEIGHT = "weight"
        const val EXTRA_REPS = "reps"

        const val CHANNEL_ID = "break_timer"
        const val NOTIFICATION_ID = 1001
    }

    private var startedAtMillis: Long = 0
    private var target: Int = 90
    private var hasVibrated = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var handler: Handler? = null
    private var tickRunnable: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                target = intent.getIntExtra(EXTRA_DURATION, 90)
                exerciseName = intent.getStringExtra(EXTRA_EXERCISE) ?: ""
                nextSetNumber = intent.getIntExtra(EXTRA_NEXT_SET, 0)
                totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 0)
                setWeight = intent.getDoubleExtra(EXTRA_WEIGHT, 0.0)
                setReps = intent.getIntExtra(EXTRA_REPS, 0)
                startTimer()
            }
            ACTION_SKIP -> {
                stopTimer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTimer() {
        stopTickHandler()
        stopMediaPlayer()
        startedAtMillis = System.currentTimeMillis()
        hasVibrated = false
        elapsedSeconds.value = 0
        targetDuration.value = target
        isRunning.value = true

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fitness:break_timer").apply {
            acquire(10 * 60 * 1000L)
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        // Silent audio loop — keeps process at media priority, prevents Android from throttling
        startMediaPlayer()

        // Handler for updating in-app StateFlow + vibrate at target
        startTickHandler()
    }

    private fun startMediaPlayer() {
        stopMediaPlayer()
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.silence).apply {
                isLooping = true
                setVolume(0f, 0f)
                start()
            }
        } catch (_: Exception) {}
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startTickHandler() {
        stopTickHandler()
        handler = Handler(Looper.getMainLooper())
        tickRunnable = object : Runnable {
            override fun run() {
                if (!isRunning.value) return
                val elapsed = ((System.currentTimeMillis() - startedAtMillis) / 1000).toInt()
                elapsedSeconds.value = elapsed
                if (!hasVibrated && elapsed >= target) {
                    hasVibrated = true
                    vibrate()
                    // Update notification
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification())
                }
                handler?.postDelayed(this, 1000L)
            }
        }
        handler?.postDelayed(tickRunnable!!, 1000L)
    }

    private fun stopTickHandler() {
        tickRunnable?.let { handler?.removeCallbacks(it) }
        handler = null
        tickRunnable = null
    }

    private fun vibrate() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(effect)
        }
    }

    private fun stopTimer() {
        stopTickHandler()
        stopMediaPlayer()
        isRunning.value = false
        elapsedSeconds.value = 0
        targetDuration.value = 0
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val targetMin = target / 60
        val targetSec = target % 60
        val durText = if (targetSec == 0) "${targetMin} min" else "$targetMin.${targetSec * 10 / 60} min"
        val setInfo = if (exerciseName.isNotEmpty()) {
            "Rest $durText \u2022 $exerciseName \u2022 Set $nextSetNumber/$totalSets"
        } else "Rest $durText"

        val passIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("set_action", ACTION_PASS)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val failIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("set_action", ACTION_FAIL)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(setInfo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(getOpenAppIntent())
            .setUsesChronometer(true)
            .setWhen(startedAtMillis)

        builder.addAction(0, "\u2705 Log Set", passIntent)
        builder.addAction(0, "\u274C Fail Set", failIntent)

        return builder.build()
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
}
