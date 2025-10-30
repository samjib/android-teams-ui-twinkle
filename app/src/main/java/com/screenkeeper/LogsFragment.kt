package com.screenkeeper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.screenkeeper.databinding.FragmentLogsBinding

/**
 * LogsFragment displays comprehensive debug logs for troubleshooting.
 */
class LogsFragment : Fragment() {
    
    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private var autoRefresh = true
    
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (autoRefresh && isAdded) {
                refreshLogs()
                handler.postDelayed(this, 2000) // Refresh every 2 seconds
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        refreshLogs()
        
        // Start auto-refresh
        handler.post(refreshRunnable)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Stop auto-refresh
        autoRefresh = false
        handler.removeCallbacks(refreshRunnable)
        
        _binding = null
    }
    
    private fun setupUI() {
        binding.refreshButton.setOnClickListener {
            refreshLogs()
        }
        
        binding.copyButton.setOnClickListener {
            copyLogsToClipboard()
        }
        
        binding.clearButton.setOnClickListener {
            clearLogs()
        }
    }
    
    private fun refreshLogs() {
        try {
            val logs = LogManager.getLogs()
            val count = logs.size
            
            // Update count
            binding.logsCount.text = "$count entries"
            
            // Update logs display
            if (logs.isEmpty()) {
                binding.logsTextView.text = getString(R.string.logs_empty)
            } else {
                val logsText = buildString {
                    logs.forEach { entry ->
                        appendLine(entry.format())
                    }
                }
                binding.logsTextView.text = logsText
                
                // Auto-scroll to bottom
                binding.logsScrollView.post {
                    binding.logsScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("LogsFragment", "Error refreshing logs", e)
            binding.logsTextView.text = "Error loading logs: ${e.message}"
        }
    }
    
    private fun copyLogsToClipboard() {
        try {
            val logsText = LogManager.getLogsAsText()
            
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Screen Keeper Logs", logsText)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(requireContext(), R.string.logs_copied, Toast.LENGTH_SHORT).show()
            
            DebugLogger.d("LogsFragment", "Logs copied to clipboard (${LogManager.getLogCount()} entries)")
        } catch (e: Exception) {
            DebugLogger.e("LogsFragment", "Error copying logs", e)
            Toast.makeText(requireContext(), "Error copying logs: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun clearLogs() {
        try {
            LogManager.clear()
            refreshLogs()
            Toast.makeText(requireContext(), "Logs cleared", Toast.LENGTH_SHORT).show()
            DebugLogger.d("LogsFragment", "Logs cleared by user")
        } catch (e: Exception) {
            DebugLogger.e("LogsFragment", "Error clearing logs", e)
        }
    }
}
