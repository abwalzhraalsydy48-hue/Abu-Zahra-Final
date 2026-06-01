package com.abuzahra.admin

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    "abu_zahra_service",
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    "screen_stream",
                    getString(R.string.screen_stream_channel),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    "camera_stream",
                    getString(R.string.camera_stream_channel),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    "audio_stream",
                    getString(R.string.audio_stream_channel),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    "location",
                    getString(R.string.location_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(channels)
        }
    }
}
