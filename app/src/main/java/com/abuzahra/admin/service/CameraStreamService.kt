package com.abuzahra.admin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.abuzahra.admin.R
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class CameraStreamService : Service() {
    
    companion object {
        private const val TAG = "CameraStreamService"
        private const val CHANNEL_ID = "camera_stream"
        private const val NOTIFICATION_ID = 4
    }
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private var isStreaming = false
    private var currentCamera = CameraCharacteristics.LENS_FACING_BACK
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CameraStreamService created")
        
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CameraStreamService started: ${intent?.action}")
        
        val camera = intent?.getStringExtra("camera") ?: "back"
        currentCamera = if (camera == "front") {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        
        when (intent?.action) {
            "photo" -> takePhoto()
            "start" -> startStreaming()
            "stop" -> stopStreaming()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "CameraStreamService destroyed")
        stopStreaming()
        serviceJob.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.camera_stream_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.camera_stream_channel))
            .setContentText("جاري بث الكاميرا")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }
    
    private fun takePhoto() {
        startBackgroundThread()
        openCamera()
    }
    
    private fun startStreaming() {
        if (isStreaming) return
        
        startForegroundWithNotification()
        startBackgroundThread()
        openCamera()
        
        isStreaming = true
        Log.d(TAG, "Camera streaming started")
    }
    
    @Suppress("MissingPermission")
    private fun openCamera() {
        try {
            val cameraId = findCameraId(currentCamera) ?: return
            
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    processImage(image)
                    image.close()
                }
            }, backgroundHandler)
            
            cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }
                
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "Camera error: $error")
                }
            }, backgroundHandler)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
        }
    }
    
    private fun findCameraId(facing: Int): String? {
        return cameraManager?.cameraIdList?.find { id ->
            val characteristics = cameraManager?.getCameraCharacteristics(id)
            characteristics?.get(CameraCharacteristics.LENS_FACING) == facing
        }
    }
    
    private fun createCaptureSession() {
        try {
            val surfaces = listOf(imageReader?.surface)
            
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder?.addTarget(imageReader?.surface!!)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    surfaces.map { OutputConfiguration(it!!) },
                    Executors.newSingleThreadExecutor(),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession = session
                            startPreview(builder!!)
                        }
                        
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Session configuration failed")
                        }
                    }
                )
                cameraDevice?.createCaptureSession(sessionConfig)
            } else {
                @Suppress("DEPRECATION")
                cameraDevice?.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        startPreview(builder!!)
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Session configuration failed")
                    }
                }, backgroundHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
        }
    }
    
    private fun startPreview(builder: CaptureRequest.Builder) {
        try {
            cameraCaptureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview", e)
        }
    }
    
    private fun processImage(image: Image) {
        // Process and send image
        Log.d(TAG, "Image captured: ${image.width}x${image.height}")
    }
    
    private fun stopStreaming() {
        isStreaming = false
        
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        imageReader?.close()
        imageReader = null
        
        stopBackgroundThread()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Camera streaming stopped")
    }
}
