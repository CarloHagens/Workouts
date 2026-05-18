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
        val serverUrl = prefs.getString("server_url", null) ?: ApiService.BASE_URL
        initRepository(serverUrl)
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

    fun initRepository(serverUrl: String) {
        val url = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val api = ApiService.create(url)
        repository = FitnessRepository(api, db)
        getSharedPreferences("workouts_prefs", MODE_PRIVATE)
            .edit()
            .putString("server_url", serverUrl)
            .apply()
    }

    fun getServerUrl(): String {
        return getSharedPreferences("workouts_prefs", MODE_PRIVATE)
            .getString("server_url", ApiService.BASE_URL) ?: ApiService.BASE_URL
    }
}
