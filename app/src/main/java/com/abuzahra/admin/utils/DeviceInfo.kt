package com.abuzahra.admin.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.util.UUID

object DeviceInfo {
    
    @SuppressLint("HardwareIds", "MissingPermission")
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("id", null)
        
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("id", deviceId).apply()
        }
        
        return deviceId
    }
    
    fun getDeviceName(): String {
        return Build.MODEL ?: "Unknown"
    }
    
    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    fun getAndroidVersion(): String {
        return Build.VERSION.RELEASE ?: "Unknown"
    }
    
    fun getSdkVersion(): Int {
        return Build.VERSION.SDK_INT
    }
    
    fun getBrand(): String {
        return Build.BRAND ?: "Unknown"
    }
    
    fun getManufacturer(): String {
        return Build.MANUFACTURER ?: "Unknown"
    }
    
    fun getBoard(): String {
        return Build.BOARD ?: "Unknown"
    }
    
    fun getDevice(): String {
        return Build.DEVICE ?: "Unknown"
    }
    
    fun getProduct(): String {
        return Build.PRODUCT ?: "Unknown"
    }
    
    fun getFingerprint(): String {
        return Build.FINGERPRINT ?: "Unknown"
    }
    
    @SuppressLint("MissingPermission")
    fun getIMEI(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.imei ?: "Unknown"
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.deviceId ?: "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    @SuppressLint("MissingPermission")
    fun getPhoneNumber(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.line1Number ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    @SuppressLint("MissingPermission")
    fun getCarrier(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.networkOperatorName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0
        }
    }
    
    fun getAllDeviceInfo(context: Context): Map<String, String> {
        return mapOf(
            "device_id" to getDeviceId(context),
            "name" to getDeviceName(),
            "model" to getDeviceModel(),
            "brand" to getBrand(),
            "manufacturer" to getManufacturer(),
            "android_version" to getAndroidVersion(),
            "sdk_version" to getSdkVersion().toString(),
            "board" to getBoard(),
            "device" to getDevice(),
            "product" to getProduct(),
            "carrier" to getCarrier(context),
            "app_version" to getAppVersion(context),
            "app_version_code" to getAppVersionCode(context).toString()
        )
    }
}
