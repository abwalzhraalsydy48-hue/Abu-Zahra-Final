package com.abuzahra.admin.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.google.gson.JsonObject

class AdminAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AccessibilityService"
        
        var instance: AdminAccessibilityService? = null
            private set
        
        fun isRunning(): Boolean = instance != null
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            // Process accessibility events
            val packageName = it.packageName?.toString() ?: ""
            val className = it.className?.toString() ?: ""
            val text = it.text?.toString() ?: ""
            
            if (packageName.isNotEmpty()) {
                Log.d(TAG, "Event: $packageName - $className")
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
    
    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
    
    fun performTap(x: Float, y: Float, duration: Long = 100): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    fun performType(text: String) {
        val nodeInfo = rootInActiveWindow ?: return
        
        // Find focused editable node and type
        val focusNode = nodeInfo.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        if (focusNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (focusNode.isEditable) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            focusNode.recycle()
        }
        
        nodeInfo.recycle()
    }
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val nodeInfo = rootInActiveWindow ?: return null
        val nodes = nodeInfo.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }
    
    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val nodeInfo = rootInActiveWindow ?: return null
        val nodes = nodeInfo.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }
    
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    
    fun scrollNode(node: AccessibilityNodeInfo, direction: Int): Boolean {
        return node.performAction(direction)
    }
    
    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
    
    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }
    
    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }
    
    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }
}
