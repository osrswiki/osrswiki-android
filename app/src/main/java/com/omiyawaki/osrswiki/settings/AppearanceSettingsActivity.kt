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
                    AppearanceSettingsFragment.newInstance(), 
                    AppearanceSettingsFragment.TAG
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
                if (intent?.action == AppearanceSettingsFragment.ACTION_THEME_CHANGED) {
                    L.d("AppearanceSettingsActivity: Received theme change broadcast")
                    // Apply theme dynamically without recreation
                    applyThemeDynamically()
                }
            }
        }
        
        val filter = IntentFilter(AppearanceSettingsFragment.ACTION_THEME_CHANGED)
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
            // Refresh the toolbar/action bar theme
            supportActionBar?.let { actionBar ->
                // Force toolbar to refresh its theme by invalidating the decorView
                window.decorView.invalidate()
                
                // Refresh the status bar to match new theme
                setupStatusBarTheming()
            }
            
            // Refresh any other UI elements specific to this activity
            val containerView = findViewById<android.view.View>(R.id.appearance_settings_container)
            containerView?.let { container ->
                refreshViewBackground(container)
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