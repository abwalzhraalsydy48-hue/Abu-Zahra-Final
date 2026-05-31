package com.abuzahra.admin.network

import android.content.Context
import android.util.Log
import com.abuzahra.admin.manager.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY = 5000L
        
        @Volatile
        private var instance: WebSocketManager? = null
        
        fun getInstance(context: Context): WebSocketManager {
            return instance ?: synchronized(this) {
                instance ?: WebSocketManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            isConnected = true
            
            // Send device registration
            val deviceId = PreferenceManager.getInstance().getDeviceId()
            val message = JsonObject().apply {
                addProperty("type", "register")
                addProperty("device_id", deviceId)
            }
            webSocket.send(gson.toJson(message))
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received: $text")
            try {
                val message = gson.fromJson(text, JsonObject::class.java)
                handleMessage(message)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message", e)
            }
        }
        
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code - $reason")
            webSocket.close(1000, null)
            isConnected = false
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code - $reason")
            isConnected = false
            scheduleReconnect()
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            isConnected = false
            scheduleReconnect()
        }
    }
    
    fun connect() {
        val serverUrl = PreferenceManager.getInstance().getServerUrl()
        val wsUrl = serverUrl.replace("https://", "wss://").replace("http://", "ws://") + "/ws/device"
        
        Log.d(TAG, "Connecting to: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket = client.newWebSocket(request, listener)
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
        isConnected = false
    }
    
    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY)
            if (!isConnected) {
                connect()
            }
        }
    }
    
    private fun handleMessage(message: JsonObject) {
        val type = message.get("type")?.asString ?: return
        
        when (type) {
            "command" -> handleCommand(message)
            "ping" -> sendPong()
        }
    }
    
    private fun handleCommand(message: JsonObject) {
        // Forward to CommandService
    }
    
    private fun sendPong() {
        val message = JsonObject().apply {
            addProperty("type", "pong")
        }
        webSocket?.send(gson.toJson(message))
    }
    
    fun sendMessage(message: JsonObject) {
        if (isConnected) {
            webSocket?.send(gson.toJson(message))
        }
    }
}
