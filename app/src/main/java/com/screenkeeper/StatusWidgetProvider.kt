package com.screenkeeper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * Home screen widget that displays the Screen Keeper service status
 * and provides a toggle button to start/stop the service.
 */
class StatusWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_SERVICE = "com.screenkeeper.widget.TOGGLE_SERVICE"
        const val ACTION_WIDGET_UPDATE = "com.screenkeeper.widget.UPDATE"

        /**
         * Updates all instances of the widget
         */
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, StatusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, StatusWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_TOGGLE_SERVICE -> {
                handleToggleAction(context)
            }
            ACTION_WIDGET_UPDATE, ScreenUpdateService.ACTION_SERVICE_STATUS -> {
                // Update all widgets when service status changes
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, StatusWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }

    override fun onEnabled(context: Context) {
        // Called when the first widget is added
        super.onEnabled(context)
        DebugLogger.d("StatusWidget", "Widget enabled - first instance added")
    }

    override fun onDisabled(context: Context) {
        // Called when the last widget is removed
        super.onDisabled(context)
        DebugLogger.d("StatusWidget", "Widget disabled - last instance removed")
    }

    private fun handleToggleAction(context: Context) {
        try {
            val isRunning = ScreenUpdateService.isServiceRunning(context)
            DebugLogger.d("StatusWidget", "Toggle clicked - current state: $isRunning")

            if (isRunning) {
                // Stop the service
                val stopIntent = Intent(context, ScreenUpdateService::class.java)
                context.stopService(stopIntent)
                DebugLogger.d("StatusWidget", "Stopping service from widget")
            } else {
                // Start the service
                val startIntent = Intent(context, ScreenUpdateService::class.java)
                ContextCompat.startForegroundService(context, startIntent)
                DebugLogger.d("StatusWidget", "Starting service from widget")
            }

            // Update the widget immediately
            updateAllWidgets(context)
        } catch (e: Exception) {
            DebugLogger.logCrash(context, "StatusWidget.handleToggleAction", e)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val isRunning = ScreenUpdateService.isServiceRunning(context)
            val views = RemoteViews(context.packageName, R.layout.widget_status_toggle)

            // Update status text and color
            if (isRunning) {
                views.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_running))
                views.setTextColor(
                    R.id.widgetStatusText,
                    ContextCompat.getColor(context, R.color.status_running)
                )
                views.setTextViewText(
                    R.id.widgetStatusSubtitle,
                    context.getString(R.string.widget_status_subtitle_running)
                )
                views.setInt(
                    R.id.widgetStatusIndicator,
                    "setColorFilter",
                    ContextCompat.getColor(context, R.color.status_running)
                )
                views.setTextViewText(R.id.widgetToggleButton, "Stop")
            } else {
                views.setTextViewText(R.id.widgetStatusText, context.getString(R.string.status_stopped))
                views.setTextColor(
                    R.id.widgetStatusText,
                    ContextCompat.getColor(context, R.color.status_stopped)
                )
                views.setTextViewText(
                    R.id.widgetStatusSubtitle,
                    context.getString(R.string.widget_status_subtitle_stopped)
                )
                views.setInt(
                    R.id.widgetStatusIndicator,
                    "setColorFilter",
                    ContextCompat.getColor(context, R.color.status_stopped)
                )
                views.setTextViewText(R.id.widgetToggleButton, "Start")
            }

            // Check if overlay permission is granted
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }

            if (!hasPermission) {
                // Show permission warning
                views.setViewVisibility(R.id.widgetPermissionWarning, View.VISIBLE)
                views.setViewVisibility(R.id.widgetToggleButton, View.GONE)
            } else {
                // Hide permission warning
                views.setViewVisibility(R.id.widgetPermissionWarning, View.GONE)
                views.setViewVisibility(R.id.widgetToggleButton, View.VISIBLE)

                // Set up toggle button click
                val toggleIntent = Intent(context, StatusWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_SERVICE
                }
                val togglePendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widgetToggleButton, togglePendingIntent)
            }

            // Set up click on widget to open app
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetContainer, openAppPendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            DebugLogger.d("StatusWidget", "Widget updated - running: $isRunning")
        } catch (e: Exception) {
            DebugLogger.logCrash(context, "StatusWidget.updateAppWidget", e)
        }
    }
}
