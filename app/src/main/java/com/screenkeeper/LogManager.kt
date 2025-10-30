package com.screenkeeper

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * LogManager collects and stores application logs for debugging.
 * 
 * Provides in-memory log storage that can be displayed in the UI
 * to help users diagnose service failures and other issues.
 */
object LogManager {
    
    private const val MAX_LOG_ENTRIES = 500
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = dateFormat.format(Date(timestamp))
            val levelStr = level.name.padEnd(5)
            return "$time [$levelStr] $tag: $message"
        }
    }
    
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR, CRASH
    }
    
    /**
     * Add a log entry
     */
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        logs.offer(entry)
        
        // Keep only the last MAX_LOG_ENTRIES
        while (logs.size > MAX_LOG_ENTRIES) {
            logs.poll()
        }
    }
    
    /**
     * Get all log entries
     */
    fun getLogs(): List<LogEntry> {
        return logs.toList()
    }
    
    /**
     * Get logs as formatted text
     */
    fun getLogsAsText(): String {
        return buildString {
            appendLine("=== Screen Keeper Debug Logs ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("=== Log Entries (${logs.size}/$MAX_LOG_ENTRIES) ===")
            
            if (logs.isEmpty()) {
                appendLine("(No logs captured)")
            } else {
                logs.forEach { entry ->
                    appendLine(entry.format())
                }
            }
            
            appendLine()
            appendLine("=== End of Logs ===")
        }
    }
    
    /**
     * Clear all logs
     */
    fun clear() {
        logs.clear()
    }
    
    /**
     * Get count of logs
     */
    fun getLogCount(): Int {
        return logs.size
    }
}
