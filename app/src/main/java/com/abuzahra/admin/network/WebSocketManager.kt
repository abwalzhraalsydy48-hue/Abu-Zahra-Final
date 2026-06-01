package com.abuzahra.admin.network

import android.content.Context
import android.content.Intent
import android.util.Log
import com.abuzahra.admin.manager.PreferenceManager
import com.abuzahra.admin.service.CommandService
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY = 5000L
        
        // WebSocket server port (from mini-services/device-ws)
        private const val WS_PORT = 3004
        
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
    private var deviceId: String = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected successfully")
            isConnected = true
            
            // Send device registration
            deviceId = PreferenceManager.getInstance().getDeviceId()
            val message = JsonObject().apply {
                addProperty("type", "register")
                addProperty("device_id", deviceId)
            }
            webSocket.send(gson.toJson(message))
            Log.d(TAG, "Sent registration for device: $deviceId")
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
            Log.e(TAG, "WebSocket failure: ${t.message}")
            isConnected = false
            scheduleReconnect()
        }
    }
    
    fun connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected, skipping")
            return
        }
        
        val serverUrl = PreferenceManager.getInstance().getServerUrl()
        
        // Build WebSocket URL - try to connect to the device-ws server
        // The device-ws server runs on port 3004
        val wsUrl = buildWsUrl(serverUrl)
        
        Log.d(TAG, "Connecting to WebSocket: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket = client.newWebSocket(request, listener)
    }
    
    private fun buildWsUrl(serverUrl: String): String {
        // Remove trailing slash if present
        var baseUrl = serverUrl.trimEnd('/')
        
        // Extract host from URL
        val host = when {
            baseUrl.startsWith("https://") -> baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> baseUrl.removePrefix("http://")
            else -> baseUrl
        }
        
        // Remove any port or path from host
        val cleanHost = host.split("/").first().split(":").first()
        
        // Build WebSocket URL with the device-ws port
        // Try wss first, fall back to ws
        return if (baseUrl.startsWith("https://")) {
            "wss://$cleanHost:$WS_PORT"
        } else {
            "ws://$cleanHost:$WS_PORT"
        }
    }
    
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        webSocket?.close(1000, "Disconnecting")
        webSocket = null
        isConnected = false
    }
    
    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY)
            if (!isConnected) {
                Log.d(TAG, "Attempting to reconnect...")
                connect()
            }
        }
    }
    
    private fun handleMessage(message: JsonObject) {
        val type = message.get("type")?.asString ?: return
        
        Log.d(TAG, "Handling message type: $type")
        
        when (type) {
            "registered" -> {
                Log.d(TAG, "Device registered successfully")
            }
            "command" -> handleCommand(message)
            "ping" -> sendPong()
            "connected" -> {
                Log.d(TAG, "Server acknowledged connection")
            }
            "error" -> {
                val error = message.get("error")?.asString ?: "Unknown error"
                Log.e(TAG, "Server error: $error")
            }
            else -> Log.d(TAG, "Unknown message type: $type")
        }
    }
    
    private fun handleCommand(message: JsonObject) {
        Log.d(TAG, "Received command via WebSocket")
        
        // Forward command to CommandService via broadcast
        val intent = Intent("com.abuzahra.admin.COMMAND_RECEIVED")
        intent.putExtra("command", message.toString())
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
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
        } else {
            Log.w(TAG, "Cannot send message - not connected")
        }
    }
    
    fun sendCommandResult(commandId: String, result: JsonObject) {
        val message = JsonObject().apply {
            addProperty("type", "command_result")
            addProperty("command_id", commandId)
            add("result", result)
        }
        sendMessage(message)
    }
    
    fun isWebSocketConnected(): Boolean = isConnected
}
