package com.workouts.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.workouts.app.data.ApiService
import com.workouts.app.data.AppDatabase
import com.workouts.app.data.FitnessRepository
import com.workouts.app.ui.activeTheme
import com.workouts.app.ui.themePresets

class WorkoutsApp : Application() {
    lateinit var repository: FitnessRepository
    private lateinit var db: AppDatabase

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        db = AppDatabase.create(this)
        val prefs = getSharedPreferences("workouts_prefs", MODE_PRIVATE)
        // Restore saved theme
        val savedTheme = prefs.getString("theme", null)
        if (savedTheme != null) {
            themePresets.find { it.name == savedTheme }?.let { activeTheme.value = it }
        }
        // Drop the server-URL override left behind by older app versions
        prefs.edit().remove("server_url").apply()
        repository = FitnessRepository(ApiService.create(deviceToken = getDeviceToken()), db)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            BreakTimerService.CHANNEL_ID,
            "Rest Timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Countdown between sets"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null) // Silent ticking; alarm plays separately at zero
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Random ID generated on first launch; identifies this device's data on the server. */
    fun getDeviceToken(): String {
        val prefs = getSharedPreferences("workouts_prefs", MODE_PRIVATE)
        prefs.getString("device_token", null)?.let { return it }
        val token = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("device_token", token).apply()
        return token
    }
}
