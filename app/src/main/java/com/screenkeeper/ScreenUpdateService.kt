package com.screenkeeper

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.random.Random

/**
 * ScreenUpdateService is a foreground service that displays a nearly invisible
 * overlay and continuously updates it to prevent screen sharing optimization.
 * 
 * The service:
 * - Creates a small, nearly transparent overlay at the top-left corner
 * - Updates the overlay color at 10 fps (every 100ms)
 * - Runs as a foreground service for reliability
 * - Uses minimal CPU and memory resources
 */
class ScreenUpdateService : Service() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var updateThread: Thread? = null
    private var isRunning = false
    private var colorValue = 0
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "screen_keeper_channel"
        private const val UPDATE_INTERVAL_MS = 100L // 10 fps
        private const val OVERLAY_ALPHA = 10 // Nearly invisible (0-255 scale)
        private const val OVERLAY_TEXT_SIZE = 5f // Very small font size
        private const val PREFS_NAME = "screen_keeper_prefs"
        private const val KEY_SERVICE_RUNNING = "service_running"
        const val ACTION_SERVICE_STATUS = "com.screenkeeper.SERVICE_STATUS"
        const val ACTION_STOP_SERVICE = "com.screenkeeper.STOP_SERVICE"
        const val EXTRA_IS_RUNNING = "is_running"
        
        /**
         * Check if the service is actually running by querying ActivityManager
         * This provides the true running state, not just the stored preference
         */
        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context): Boolean {
            try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Check if the service is actually running (limit to 100 services for efficiency)
                for (service in manager.getRunningServices(100)) {
                    if (ScreenUpdateService::class.java.name == service.service.className) {
                        // Service is actually running
                        return true
                    }
                }
                
                // Service is not running, clear the SharedPreferences flag if it was set
                if (prefs.getBoolean(KEY_SERVICE_RUNNING, false)) {
                    prefs.edit().putBoolean(KEY_SERVICE_RUNNING, false).apply()
                    DebugLogger.d("Service", "Service not running but flag was set - cleared flag")
                }
                
                return false
            } catch (e: Exception) {
                DebugLogger.e("Service", "Error checking service state", e)
                // Fallback to SharedPreferences if ActivityManager fails
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                return prefs.getBoolean(KEY_SERVICE_RUNNING, false)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            DebugLogger.logLifecycle("ScreenUpdateService", "onCreate")
            
            createNotificationChannel()
            
            // Start foreground service with proper type for Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            
            setServiceRunning(true)
            
            DebugLogger.d("Service", "Service created successfully")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.onCreate", e)
            // Try to stop service gracefully if creation fails
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action ?: "null"
            DebugLogger.logService("onStartCommand", "flags=$flags, startId=$startId, action=$action")
            
            // Log system info
            DebugLogger.d("Service", "Battery optimized: ${checkBatteryOptimization()}")
            DebugLogger.d("Service", "Foreground service: true")
            
            // Handle stop action from notification
            if (intent?.action == ACTION_STOP_SERVICE) {
                DebugLogger.d("Service", "Stop action received from notification")
                stopSelf()
                return START_NOT_STICKY
            }
            
            if (!isRunning) {
                DebugLogger.d("Service", "Starting overlay updates (service not running yet)")
                startOverlayUpdates()
            } else {
                DebugLogger.d("Service", "Service already running, skipping overlay creation")
            }
            
            DebugLogger.d("Service", "Returning START_STICKY")
            return START_STICKY
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.onStartCommand", e)
            DebugLogger.e("Service", "Failed to start service, returning START_NOT_STICKY", e)
            return START_NOT_STICKY
        }
    }
    
    /**
     * Check if battery optimization is enabled for this app
     */
    private fun checkBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            DebugLogger.logLifecycle("ScreenUpdateService", "onDestroy")
            DebugLogger.w("Service", "Service is being destroyed - this may be due to system killing the service or user stopping it")
            
            stopOverlayUpdates()
            setServiceRunning(false)
            
            DebugLogger.d("Service", "Service destroyed successfully")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.onDestroy", e)
        }
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        try {
            DebugLogger.w("Service", "Task removed - app was swiped away from recent apps")
            DebugLogger.d("Service", "Service will continue running (START_STICKY)")
            // Service will be restarted automatically due to START_STICKY
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.onTaskRemoved", e)
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        
        try {
            DebugLogger.w("Service", "Low memory warning received - service may be killed soon")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.onLowMemory", e)
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        try {
            val levelName = when (level) {
                TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
                TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
                TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
                TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
                TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
                TRIM_MEMORY_MODERATE -> "MODERATE"
                TRIM_MEMORY_COMPLETE -> "COMPLETE"
                else -> "UNKNOWN($level)"
            }
            DebugLogger.w("Service", "Memory trim requested: $levelName")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.onTrimMemory", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Creates notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates the notification for the foreground service
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, pendingIntentFlags
        )
        
        // Create stop action intent
        val stopIntent = Intent(this, ScreenUpdateService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, pendingIntentFlags
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }
    
    /**
     * Starts the overlay and begins updating it
     */
    private fun startOverlayUpdates() {
        try {
            DebugLogger.d("Service", "Starting overlay updates")
            DebugLogger.d("Service", "Checking overlay permission...")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val hasPermission = Settings.canDrawOverlays(this)
                DebugLogger.logPermission("Overlay permission in service", hasPermission)
                if (!hasPermission) {
                    DebugLogger.e("Service", "Overlay permission not granted - cannot create overlay")
                    stopSelf()
                    return
                }
            }
            
            createOverlay()
            isRunning = true
            DebugLogger.d("Service", "isRunning flag set to true")
            
            // Start update thread
            updateThread = Thread {
                try {
                    DebugLogger.d("Service", "Update thread started")
                    var updateCount = 0
                    
                    while (isRunning) {
                        try {
                            updateOverlay()
                            updateCount++
                            
                            // Log every 100 updates (10 seconds)
                            if (updateCount % 100 == 0) {
                                DebugLogger.d("Service", "Still running - $updateCount updates completed")
                            }
                            
                            Thread.sleep(UPDATE_INTERVAL_MS)
                        } catch (e: InterruptedException) {
                            DebugLogger.d("Service", "Update thread interrupted")
                            break
                        } catch (e: Exception) {
                            // Log error but continue running
                            DebugLogger.e("Service", "Error in update loop", e)
                        }
                    }
                    
                    DebugLogger.d("Service", "Update thread stopped after $updateCount updates")
                } catch (e: Exception) {
                    DebugLogger.logCrash(this, "ScreenUpdateService.updateThread", e)
                }
            }
            updateThread?.start()
            
            DebugLogger.d("Service", "Overlay updates started successfully")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.startOverlayUpdates", e)
            DebugLogger.e("Service", "Failed to start overlay updates - stopping service", e)
            isRunning = false
            stopSelf()
        }
    }
    
    /**
     * Stops overlay updates and removes the overlay
     */
    private fun stopOverlayUpdates() {
        try {
            DebugLogger.d("Service", "Stopping overlay updates")
            
            isRunning = false
            updateThread?.interrupt()
            updateThread = null
            removeOverlay()
            
            DebugLogger.d("Service", "Overlay updates stopped successfully")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.stopOverlayUpdates", e)
        }
    }
    
    /**
     * Creates the overlay view and adds it to the window manager
     */
    private fun createOverlay() {
        try {
            DebugLogger.d("Service", "Creating overlay")
            
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            overlayView = TextView(this).apply {
                text = "•"
                textSize = OVERLAY_TEXT_SIZE
                setTextColor(Color.argb(OVERLAY_ALPHA, 255, 255, 255))
            }
            
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            DebugLogger.d("Service", "Using layout type: $layoutType")
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            
            try {
                windowManager?.addView(overlayView, params)
                DebugLogger.d("Service", "Overlay created and added to window manager")
            } catch (e: Exception) {
                DebugLogger.logCrash(this, "ScreenUpdateService.createOverlay.addView", e)
                throw e
            }
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "ScreenUpdateService.createOverlay", e)
            throw e
        }
    }
    
    /**
     * Updates the overlay with a new color to trigger screen updates
     */
    private fun updateOverlay() {
        overlayView?.post {
            // Cycle through color values
            colorValue = (colorValue + 1) % 256
            
            // Optional: Slightly vary position for additional detection
            val xPos = if (Random.nextBoolean()) 0 else 1
            
            overlayView?.apply {
                setTextColor(Color.argb(OVERLAY_ALPHA, colorValue, colorValue, colorValue))
                
                // Update position if needed
                val params = layoutParams as? WindowManager.LayoutParams
                params?.x = xPos
                if (params != null) {
                    try {
                        windowManager?.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    /**
     * Removes the overlay from the window manager
     */
    private fun removeOverlay() {
        try {
            DebugLogger.d("Service", "Removing overlay")
            
            overlayView?.let {
                windowManager?.removeView(it)
                DebugLogger.d("Service", "Overlay removed successfully")
            }
        } catch (e: Exception) {
            DebugLogger.e("Service", "Error removing overlay", e)
        }
        overlayView = null
        windowManager = null
    }
    
    /**
     * Sets the service running state in SharedPreferences and broadcasts the change
     */
    private fun setServiceRunning(running: Boolean) {
        // Save state to SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()

        // Broadcast the state change to MainActivity
        val intent = Intent(ACTION_SERVICE_STATUS)
        intent.putExtra(EXTRA_IS_RUNNING, running)
        sendBroadcast(intent)

        // Update home screen widget
        StatusWidgetProvider.updateAllWidgets(this)
    }
}
