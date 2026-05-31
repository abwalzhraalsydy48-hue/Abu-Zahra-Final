package com.abuzahra.admin.service

import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.network.ApiService
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "NotificationListener"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let { notification ->
            processNotification(notification, "posted")
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        
        sbn?.let { notification ->
            processNotification(notification, "removed")
        }
    }
    
    private fun processNotification(sbn: StatusBarNotification, action: String) {
        try {
            val packageName = sbn.packageName
            val notification = sbn.notification
            val extras = notification.extras
            
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
            
            val notificationData = JsonObject().apply {
                addProperty("action", action)
                addProperty("package_name", packageName)
                addProperty("title", title)
                addProperty("text", text)
                addProperty("big_text", bigText)
                addProperty("post_time", sbn.postTime)
                addProperty("id", sbn.id)
                addProperty("key", sbn.key)
            }
            
            sendNotification(notificationData)
            
            Log.d(TAG, "Notification: $packageName - $title")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
    
    private fun sendNotification(data: JsonObject) {
        serviceScope.launch {
            try {
                // Send to server
                Log.d(TAG, "Sending notification data")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending notification", e)
            }
        }
    }
}
