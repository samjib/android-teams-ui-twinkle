package com.screenkeeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.screenkeeper.databinding.FragmentMainBinding

/**
 * MainFragment handles the main UI and service controls.
 */
class MainFragment : Fragment() {
    
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private var isServiceRunning = false
    
    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenUpdateService.ACTION_SERVICE_STATUS) {
                val running = intent.getBooleanExtra(ScreenUpdateService.EXTRA_IS_RUNNING, false)
                isServiceRunning = running
                updateStatusUI(running)
            }
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionAndUpdateUI()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            DebugLogger.logLifecycle("MainFragment", "onViewCreated")
            
            setupUI()
            checkPermissionAndUpdateUI()
            
            // Register broadcast receiver for service status updates
            val filter = IntentFilter(ScreenUpdateService.ACTION_SERVICE_STATUS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(serviceStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                requireContext().registerReceiver(serviceStatusReceiver, filter)
            }
            
            DebugLogger.d("MainFragment", "View created successfully")
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.onViewCreated", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        try {
            DebugLogger.logLifecycle("MainFragment", "onResume")
            
            checkPermissionAndUpdateUI()
            // Check actual service state from SharedPreferences
            isServiceRunning = ScreenUpdateService.isServiceRunning(requireContext())
            updateStatusUI(isServiceRunning)
            
            DebugLogger.d("MainFragment", "onResume completed - service running: $isServiceRunning")
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.onResume", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        try {
            DebugLogger.logLifecycle("MainFragment", "onDestroyView")
            
            // Unregister broadcast receiver
            try {
                requireContext().unregisterReceiver(serviceStatusReceiver)
                DebugLogger.d("MainFragment", "Broadcast receiver unregistered")
            } catch (e: Exception) {
                DebugLogger.w("MainFragment", "Receiver already unregistered or never registered: ${e.message}")
            }
            
            _binding = null
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.onDestroyView", e)
        }
    }
    
    private fun setupUI() {
        binding.toggleButton.setOnClickListener {
            if (canDrawOverlays()) {
                toggleService()
            } else {
                showPermissionCard()
            }
        }
        
        binding.grantPermissionButton.setOnClickListener {
            requestOverlayPermission()
        }
    }
    
    private fun canDrawOverlays(): Boolean {
        return try {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(requireContext())
            } else {
                true
            }
            DebugLogger.logPermission("Overlay permission check", hasPermission)
            hasPermission
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.canDrawOverlays", e)
            false
        }
    }
    
    private fun requestOverlayPermission() {
        try {
            DebugLogger.d("MainFragment", "Requesting overlay permission")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                overlayPermissionLauncher.launch(intent)
            }
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.requestOverlayPermission", e)
        }
    }
    
    private fun checkPermissionAndUpdateUI() {
        if (canDrawOverlays()) {
            hidePermissionCard()
            binding.toggleButton.isEnabled = true
        } else {
            showPermissionCard()
            binding.toggleButton.isEnabled = false
        }
    }
    
    private fun showPermissionCard() {
        binding.permissionCard.visibility = View.VISIBLE
    }
    
    private fun hidePermissionCard() {
        binding.permissionCard.visibility = View.GONE
    }
    
    private fun toggleService() {
        if (isServiceRunning) {
            stopScreenUpdateService()
        } else {
            startScreenUpdateService()
        }
    }
    
    private fun startScreenUpdateService() {
        try {
            DebugLogger.logService("Starting service")
            
            val intent = Intent(requireContext(), ScreenUpdateService::class.java)
            ContextCompat.startForegroundService(requireContext(), intent)
            isServiceRunning = true
            updateStatusUI(true)
            
            DebugLogger.d("MainFragment", "Service start command sent")
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.startScreenUpdateService", e)
            
            // Update UI to reflect failure
            isServiceRunning = false
            updateStatusUI(false)
        }
    }
    
    private fun stopScreenUpdateService() {
        try {
            DebugLogger.logService("Stopping service")
            
            val intent = Intent(requireContext(), ScreenUpdateService::class.java)
            requireContext().stopService(intent)
            isServiceRunning = false
            updateStatusUI(false)
            
            DebugLogger.d("MainFragment", "Service stop command sent")
        } catch (e: Exception) {
            DebugLogger.logCrash(requireContext(), "MainFragment.stopScreenUpdateService", e)
        }
    }
    
    private fun updateStatusUI(running: Boolean) {
        if (running) {
            binding.statusValue.text = getString(R.string.status_running)
            binding.statusValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_running)
            )
            binding.toggleButton.text = "Stop Service"
        } else {
            binding.statusValue.text = getString(R.string.status_stopped)
            binding.statusValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.status_stopped)
            )
            binding.toggleButton.text = getString(R.string.toggle_service)
        }
    }
}
