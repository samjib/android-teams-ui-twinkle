package com.screenkeeper

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Debug logging utility for Screen Keeper application.
 * 
 * Provides comprehensive error logging with device and app state information
 * to help diagnose crashes and issues.
 */
object DebugLogger {
    
    private const val TAG = "ScreenKeeper"
    private const val CRASH_TAG = "ScreenKeeper_CRASH"
    
    /**
     * Log general debug information
     */
    fun d(tag: String, message: String) {
        Log.d("$TAG:$tag", message)
        LogManager.log(LogManager.LogLevel.DEBUG, tag, message)
    }
    
    /**
     * Log error information
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG:$tag", message, throwable)
        val fullMessage = if (throwable != null) {
            "$message: ${throwable.message}"
        } else {
            message
        }
        LogManager.log(LogManager.LogLevel.ERROR, tag, fullMessage)
    }
    
    /**
     * Log warning information
     */
    fun w(tag: String, message: String) {
        Log.w("$TAG:$tag", message)
        LogManager.log(LogManager.LogLevel.WARN, tag, message)
    }
    
    /**
     * Log info information
     */
    fun i(tag: String, message: String) {
        Log.i("$TAG:$tag", message)
        LogManager.log(LogManager.LogLevel.INFO, tag, message)
    }
    
    /**
     * Log a crash with comprehensive debugging information
     */
    fun logCrash(context: Context?, location: String, error: Throwable) {
        val crashInfo = buildString {
            appendLine("==================== CRASH REPORT ====================")
            appendLine("Location: $location")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine()
            
            // Device information
            appendLine("--- Device Info ---")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine()
            
            // App state information
            if (context != null) {
                appendLine("--- App State ---")
                try {
                    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else {
                        true
                    }
                    appendLine("Overlay Permission: $hasOverlayPermission")
                    
                    val isServiceRunning = ScreenUpdateService.isServiceRunning(context)
                    appendLine("Service Running: $isServiceRunning")
                } catch (e: Exception) {
                    appendLine("Error getting app state: ${e.message}")
                }
                appendLine()
            }
            
            // Error details
            appendLine("--- Error Details ---")
            appendLine("Error Type: ${error.javaClass.simpleName}")
            appendLine("Message: ${error.message ?: "No message"}")
            appendLine()
            
            // Stack trace
            appendLine("--- Stack Trace ---")
            val stackTrace = StringWriter()
            error.printStackTrace(PrintWriter(stackTrace))
            appendLine(stackTrace.toString())
            
            appendLine("=====================================================")
        }
        
        Log.e(CRASH_TAG, crashInfo)
        LogManager.log(LogManager.LogLevel.CRASH, location, crashInfo)
    }
    
    /**
     * Log app lifecycle event
     */
    fun logLifecycle(location: String, event: String) {
        d("Lifecycle", "$location: $event")
    }
    
    /**
     * Log permission event
     */
    fun logPermission(event: String, granted: Boolean) {
        i("Permission", "$event: ${if (granted) "GRANTED" else "DENIED"}")
    }
    
    /**
     * Log service event
     */
    fun logService(event: String, details: String = "") {
        i("Service", "$event${if (details.isNotEmpty()) ": $details" else ""}")
    }
    
    /**
     * Get device and app information for debugging
     */
    fun getDebugInfo(context: Context): String {
        return buildString {
            appendLine("=== Debug Information ===")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            
            try {
                val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }
                appendLine("Overlay Permission: $hasOverlay")
                
                val serviceRunning = ScreenUpdateService.isServiceRunning(context)
                appendLine("Service Status: ${if (serviceRunning) "Running" else "Stopped"}")
            } catch (e: Exception) {
                appendLine("Error: ${e.message}")
            }
            
            appendLine("========================")
        }
    }
}
