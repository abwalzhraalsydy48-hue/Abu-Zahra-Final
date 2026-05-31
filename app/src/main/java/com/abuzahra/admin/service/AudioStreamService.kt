package com.abuzahra.admin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abuzahra.admin.R
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioStreamService : Service() {
    
    companion object {
        private const val TAG = "AudioStreamService"
        private const val CHANNEL_ID = "audio_stream"
        private const val NOTIFICATION_ID = 5
        
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioStreamService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AudioStreamService started: ${intent?.action}")
        
        when (intent?.action) {
            "start" -> startRecording()
            "stop" -> stopRecording()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "AudioStreamService destroyed")
        stopRecording()
        serviceJob.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.audio_stream_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.audio_stream_channel))
            .setContentText("جاري تسجيل الصوت")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    @Suppress("MissingPermission")
    private fun startRecording() {
        if (isRecording) return
        
        startForegroundWithNotification()
        
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        audioRecord?.startRecording()
        isRecording = true
        
        serviceScope.launch {
            val buffer = ByteArray(bufferSize)
            
            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: -1
                
                if (bytesRead > 0) {
                    // Send audio data to server
                    sendAudioData(buffer, bytesRead)
                }
            }
        }
        
        Log.d(TAG, "Audio recording started")
    }
    
    private fun sendAudioData(data: ByteArray, size: Int) {
        // Send audio data via WebSocket or API
    }
    
    private fun stopRecording() {
        isRecording = false
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Audio recording stopped")
    }
}
