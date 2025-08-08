package com.omiyawaki.osrswiki.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity

/**
 * Activity to host the AppearanceSettingsFragment for appearance-specific settings.
 * Shows only theme, color, and display options - no offline/storage settings.
 */
class AppearanceSettingsActivity : BaseActivity() {

    private var themeChangeReceiver: BroadcastReceiver? = null

    companion object {
        /**
         * Creates an Intent to start AppearanceSettingsActivity.
         * @param context The Context to use.
         * @return An Intent to start AppearanceSettingsActivity.
         */
        fun newIntent(context: Context): Intent {
            return Intent(context, AppearanceSettingsActivity::class.java)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.appearance_settings_container, 
                    CustomAppearanceSettingsFragment.newInstance(), 
                    CustomAppearanceSettingsFragment.TAG
                )
                .commit()
        }

        setupToolbar()
        setupThemeChangeReceiver()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = getString(R.string.settings_category_appearance)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    private fun setupThemeChangeReceiver() {
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == CustomAppearanceSettingsFragment.ACTION_THEME_CHANGED) {
                    L.d("AppearanceSettingsActivity: Received theme change broadcast")
                    // For AppearanceSettings specifically, recreate the activity for complete refresh
                    // This ensures all preference items and backgrounds are properly themed
                    recreate()
                }
            }
        }
        
        val filter = IntentFilter(CustomAppearanceSettingsFragment.ACTION_THEME_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(themeChangeReceiver!!, filter)
        L.d("AppearanceSettingsActivity: Theme change receiver registered")
    }
    
    private fun unregisterThemeChangeReceiver() {
        themeChangeReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            themeChangeReceiver = null
            L.d("AppearanceSettingsActivity: Theme change receiver unregistered")
        }
    }
    
    override fun refreshThemeDependentElements() {
        super.refreshThemeDependentElements()
        L.d("AppearanceSettingsActivity: Refreshing theme-dependent elements")
        
        try {
            // CRITICAL: Refresh status bar theming IMMEDIATELY and with proper timing
            // Status bar theming must happen early to avoid visual glitches
            setupStatusBarTheming()
            
            // Force a complete window refresh to apply status bar changes immediately
            window.decorView.invalidate()
            
            // Refresh the toolbar/action bar theme
            supportActionBar?.let { actionBar ->
                // Force toolbar colors to refresh
                val typedValue = android.util.TypedValue()
                val theme = this.theme
                
                // Apply toolbar background color
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                    // The toolbar should pick up the new theme automatically, but force refresh if needed
                    actionBar.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(typedValue.data))
                }
                
                // Apply toolbar text color 
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                    // Unfortunately, ActionBar doesn't have a direct setTitleColor method
                    // The theme should handle this, but we can try to find and refresh the title TextView
                    try {
                        val titleId = resources.getIdentifier("action_bar_title", "id", "android")
                        if (titleId > 0) {
                            findViewById<android.widget.TextView>(titleId)?.setTextColor(typedValue.data)
                        }
                    } catch (e: Exception) {
                        L.d("AppearanceSettingsActivity: Could not manually set action bar title color: ${e.message}")
                    }
                }
            }
            
            // Refresh any other UI elements specific to this activity
            val containerView = findViewById<android.view.View>(R.id.appearance_settings_container)
            containerView?.let { container ->
                refreshViewBackground(container)
            }
            
            // CRITICAL: Force a second status bar refresh after other elements
            // This ensures status bar theming overrides any other conflicting settings
            window.decorView.post {
                setupStatusBarTheming()
            }
            
            L.d("AppearanceSettingsActivity: Theme-dependent elements refresh completed")
            
        } catch (e: Exception) {
            L.e("AppearanceSettingsActivity: Error refreshing theme elements: ${e.message}")
        }
    }
    
    private fun setupStatusBarTheming() {
        try {
            // Get the current theme's windowLightStatusBar setting
            val typedValue = android.util.TypedValue()
            val theme = this.theme
            val hasLightStatusBar = theme.resolveAttribute(android.R.attr.windowLightStatusBar, typedValue, true) && typedValue.data != 0
            
            // Apply the theme's status bar settings
            val windowInsetsController = androidx.core.view.ViewCompat.getWindowInsetsController(window.decorView)
            windowInsetsController?.let { controller ->
                controller.isAppearanceLightStatusBars = hasLightStatusBar
                L.d("AppearanceSettingsActivity: Set status bar light mode: $hasLightStatusBar")
            }
            
            // Set status bar color from theme if available
            val statusBarColorTypedValue = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.statusBarColor, statusBarColorTypedValue, true)) {
                window.statusBarColor = statusBarColorTypedValue.data
                L.d("AppearanceSettingsActivity: Applied theme status bar color")
            }
        } catch (e: Exception) {
            L.e("AppearanceSettingsActivity: Error setting up status bar theming: ${e.message}")
        }
    }
    
    private fun refreshViewBackground(view: android.view.View) {
        try {
            val typedValue = android.util.TypedValue()
            val theme = this.theme
            
            // Try to apply background color from theme
            val backgroundAttrs = arrayOf(
                R.attr.paper_color,
                com.google.android.material.R.attr.colorSurface,
                android.R.attr.colorBackground
            )
            
            for (attr in backgroundAttrs) {
                if (theme.resolveAttribute(attr, typedValue, true)) {
                    view.setBackgroundColor(typedValue.data)
                    L.d("AppearanceSettingsActivity: Applied background color from theme")
                    break
                }
            }
        } catch (e: Exception) {
            L.w("AppearanceSettingsActivity: Error refreshing view background: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        unregisterThemeChangeReceiver()
        super.onDestroy()
    }
}