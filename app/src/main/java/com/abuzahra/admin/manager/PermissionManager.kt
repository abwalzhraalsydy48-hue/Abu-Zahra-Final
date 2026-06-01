package com.abuzahra.admin.manager

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: Activity) {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
        
        // All 42 permissions
        val ALL_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                // Camera & Audio
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                
                // Location
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                
                // Storage
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                
                // Contacts
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                
                // Call Logs
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                
                // Phone
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                
                // SMS
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                
                // Calendar
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                
                // Network
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                
                // Other
                Manifest.permission.VIBRATE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Manifest.permission.USE_FINGERPRINT,
                Manifest.permission.USE_BIOMETRIC,
                Manifest.permission.REQUEST_INSTALL_PACKAGES
            )
        } else {
            arrayOf(
                // Camera & Audio
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                
                // Location
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                
                // Storage
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                
                // Contacts
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS,
                
                // Call Logs
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                
                // Phone
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.CALL_PHONE,
                
                // SMS
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                
                // Calendar
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                
                // Network
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                
                // Other
                Manifest.permission.VIBRATE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Manifest.permission.USE_FINGERPRINT,
                Manifest.permission.USE_BIOMETRIC,
                Manifest.permission.REQUEST_INSTALL_PACKAGES
            )
        }
    }
    
    fun requestAllPermissions() {
        val permissionsToRequest = ALL_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    fun hasAllPermissions(): Boolean {
        return ALL_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        // Handle permission results
    }
}
