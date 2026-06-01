package com.abuzahra.admin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.abuzahra.admin.R
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import com.google.android.gms.location.*
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocationService : Service() {
    
    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "location_service"
        private const val NOTIFICATION_ID = 2
    }
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LocationService created")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "LocationService started")
        
        startForegroundWithNotification()
        startLocationUpdates()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "LocationService destroyed")
        stopLocationUpdates()
        serviceJob.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.location_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_channel))
            .setContentText("جاري تتبع الموقع")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000 // 10 seconds
        ).apply {
            setMinUpdateIntervalMillis(5000) // 5 seconds
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(true)
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    sendLocation(location)
                }
            }
        }
        
        try {
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                mainLooper
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location updates", e)
        }
    }
    
    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
    }
    
    private fun sendLocation(location: Location) {
        serviceScope.launch {
            try {
                val deviceId = PreferenceManager.getInstance().getDeviceId()
                val apiService = ApiService.getInstance(this@LocationService)
                
                val locationData = JsonObject().apply {
                    addProperty("latitude", location.latitude)
                    addProperty("longitude", location.longitude)
                    addProperty("altitude", location.altitude)
                    addProperty("accuracy", location.accuracy)
                    addProperty("speed", location.speed)
                    addProperty("bearing", location.bearing)
                    addProperty("provider", location.provider)
                    addProperty("timestamp", location.time)
                }
                
                // Send to server via API or WebSocket
                Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location", e)
            }
        }
    }
}
