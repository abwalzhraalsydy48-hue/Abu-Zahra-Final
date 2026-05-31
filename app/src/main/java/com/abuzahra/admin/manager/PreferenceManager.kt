package com.abuzahra.admin.manager

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "abu_zahra_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_REGISTERED = "is_registered"
        private const val KEY_SERVER_URL = "server_url"
        
        @Volatile
        private var instance: PreferenceManager? = null
        
        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = PreferenceManager(context.applicationContext)
                    }
                }
            }
        }
        
        fun getInstance(): PreferenceManager {
            return instance ?: throw IllegalStateException("PreferenceManager not initialized")
        }
    }
    
    fun setDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }
    
    fun getDeviceId(): String {
        return prefs.getString(KEY_DEVICE_ID, "") ?: ""
    }
    
    fun setRegistered(registered: Boolean) {
        prefs.edit().putBoolean(KEY_REGISTERED, registered).apply()
    }
    
    fun isRegistered(): Boolean {
        return prefs.getBoolean(KEY_REGISTERED, false)
    }
    
    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, "https://alsydyabwalzhra.online") ?: "https://alsydyabwalzhra.online"
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
