package com.abuzahra.admin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.abuzahra.admin.R
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScreenStreamService : Service() {
    
    companion object {
        private const val TAG = "ScreenStreamService"
        private const val CHANNEL_ID = "screen_stream"
        private const val NOTIFICATION_ID = 3
        
        var resultCode: Int = 0
        var resultData: Intent? = null
    }
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private var isStreaming = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenStreamService created")
        
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ScreenStreamService started: ${intent?.action}")
        
        when (intent?.action) {
            "screenshot" -> takeScreenshot()
            "start" -> startStreaming()
            "stop" -> stopStreaming()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "ScreenStreamService destroyed")
        stopStreaming()
        serviceJob.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_stream_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_stream_channel))
            .setContentText("جاري بث الشاشة")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun takeScreenshot() {
        try {
            if (resultCode != 0 && resultData != null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData!!)
                captureScreen()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot", e)
        }
    }
    
    private fun startStreaming() {
        if (isStreaming) return
        
        try {
            if (resultCode != 0 && resultData != null) {
                startForegroundWithNotification()
                
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData!!)
                
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                
                imageReader = ImageReader.newInstance(
                    metrics.widthPixels,
                    metrics.heightPixels,
                    PixelFormat.RGBA_8888,
                    2
                )
                
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenStream",
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    null
                )
                
                isStreaming = true
                Log.d(TAG, "Screen streaming started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting stream", e)
        }
    }
    
    private fun captureScreen() {
        // Capture and send screenshot
        Log.d(TAG, "Screen captured")
    }
    
    private fun stopStreaming() {
        isStreaming = false
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Screen streaming stopped")
    }
}
