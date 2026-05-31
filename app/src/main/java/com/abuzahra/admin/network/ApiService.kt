package com.abuzahra.admin.network

import android.content.Context
import com.abuzahra.admin.BuildConfig
import com.abuzahra.admin.manager.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class LinkResult(
    val success: Boolean,
    val deviceId: String = "",
    val error: String = ""
)

class ApiService private constructor(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    companion object {
        @Volatile
        private var instance: ApiService? = null
        
        fun getInstance(context: Context): ApiService {
            return instance ?: synchronized(this) {
                instance ?: ApiService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private fun getBaseUrl(): String {
        return PreferenceManager.getInstance().getServerUrl()
    }
    
    suspend fun verifyLinkCode(code: String): LinkResult = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/v1/link/verify"
            val json = JsonObject().apply {
                addProperty("code", code)
            }
            
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                
                if (jsonResponse.get("success")?.asBoolean == true) {
                    val deviceId = jsonResponse.get("device_id")?.asString ?: ""
                    LinkResult(success = true, deviceId = deviceId)
                } else {
                    val error = jsonResponse.get("error")?.asString ?: "Unknown error"
                    LinkResult(success = false, error = error)
                }
            } else {
                LinkResult(success = false, error = "Server error: ${response.code}")
            }
        } catch (e: Exception) {
            LinkResult(success = false, error = e.message ?: "Connection error")
        }
    }
    
    suspend fun sendHeartbeat(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/v1/device/$deviceId/heartbeat"
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun getCommands(deviceId: String): List<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/v1/device/$deviceId/commands"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val commandsArray = jsonResponse.getAsJsonArray("commands") ?: return@withContext emptyList()
                
                commandsArray.map { it.asJsonObject }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun sendCommandResult(deviceId: String, commandId: String, result: JsonObject): Boolean = 
        withContext(Dispatchers.IO) {
        try {
            val url = "${getBaseUrl()}/api/v1/device/$deviceId/command/$commandId/result"
            val body = result.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun uploadFile(deviceId: String, filePath: String, fileType: String): Boolean = 
        withContext(Dispatchers.IO) {
        // Implement file upload
        true
    }
}
