package com.screenkeeper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.screenkeeper.databinding.ActivityMainTabsBinding

/**
 * MainActivity handles the tabbed UI for Screen Keeper.
 * 
 * This activity:
 * - Provides tabbed interface for Main controls and Logs
 * - Manages ViewPager2 for fragment navigation
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainTabsBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            DebugLogger.logLifecycle("MainActivity", "onCreate")
            
            binding = ActivityMainTabsBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupViewPager()
            
            DebugLogger.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "MainActivity.onCreate", e)
            throw e
        }
    }
    
    private fun setupViewPager() {
        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter
        
        // Setup TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_main)
                1 -> getString(R.string.tab_logs)
                else -> ""
            }
        }.attach()
    }
    
    override fun onResume() {
        super.onResume()
        
        try {
            DebugLogger.logLifecycle("MainActivity", "onResume")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "MainActivity.onResume", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            DebugLogger.logLifecycle("MainActivity", "onDestroy")
        } catch (e: Exception) {
            DebugLogger.logCrash(this, "MainActivity.onDestroy", e)
        }
    }
}
