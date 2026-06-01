package com.abuzahra.admin.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.service.CommandService
import com.abuzahra.admin.service.LocationService

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot received: ${intent.action}")
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            
            // Check if device is registered
            PreferenceManager.init(context)
            if (PreferenceManager.getInstance().isRegistered()) {
                startServices(context)
            }
        }
    }
    
    private fun startServices(context: Context) {
        try {
            // Start command service
            val commandIntent = Intent(context, CommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(commandIntent)
            } else {
                context.startService(commandIntent)
            }
            
            Log.d(TAG, "Services started after boot")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services", e)
        }
    }
}
