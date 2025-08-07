package com.omiyawaki.osrswiki.activity

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.theme.ThemeAware

abstract class BaseActivity : AppCompatActivity() {

    private var currentThemeId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the selected theme BEFORE super.onCreate() and setContentView().
        applyCurrentTheme()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        // When resuming, check if the global theme has changed since this activity
        // was last created. If so, recreate it to apply the new theme.
        val globalThemeId = (application as OSRSWikiApp).getCurrentTheme().resourceId
        if (currentThemeId != 0 && currentThemeId != globalThemeId) {
            recreate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Check if the UI mode has changed (e.g., system light/dark theme switch).
        // If the app's theme is set to "auto", we need to recreate the activity
        // to apply the new system-driven theme.
        if (Prefs.appThemeMode == "auto") {
            // The theme is resolved in OSRSWikiApp.getCurrentTheme() which reads the system
            // state, so simply recreating is sufficient.
            recreate()
        }
    }

    private fun applyCurrentTheme() {
        val osrsWikiApp = applicationContext as OSRSWikiApp
        val selectedTheme = osrsWikiApp.getCurrentTheme()
        // Cache the ID of the theme being applied to this activity instance.
        currentThemeId = selectedTheme.resourceId
        setTheme(selectedTheme.resourceId)
    }

    /**
     * Notifies all ThemeAware fragments that the theme has changed.
     * Should be called after the activity has been recreated due to a theme change.
     */
    protected fun notifyFragmentsOfThemeChange() {
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is ThemeAware && fragment.isAdded && fragment.view != null) {
                try {
                    fragment.onThemeChanged()
                } catch (e: Exception) {
                    // Log the error but don't crash - theme changes should be graceful
                    android.util.Log.e("BaseActivity", "Error notifying fragment ${fragment::class.simpleName} of theme change", e)
                }
            }
        }
    }
    
    /**
     * Applies a new theme dynamically without recreating the activity.
     * This method updates the theme and refreshes UI elements to prevent FOUC.
     */
    fun applyThemeDynamically() {
        val osrsWikiApp = applicationContext as OSRSWikiApp
        val newTheme = osrsWikiApp.getCurrentTheme()
        val newThemeId = newTheme.resourceId
        
        // Only apply if theme actually changed
        if (currentThemeId != newThemeId) {
            android.util.Log.d("BaseActivity", "Applying theme dynamically from ${currentThemeId} to ${newThemeId}")
            
            // Update the current theme ID
            currentThemeId = newThemeId
            
            // Apply the new theme
            setTheme(newThemeId)
            
            // Refresh theme-dependent UI elements
            refreshThemeDependentElements()
            
            // Notify fragments of theme change
            notifyFragmentsOfThemeChange()
            
            android.util.Log.d("BaseActivity", "Dynamic theme application completed")
        }
    }
    
    /**
     * Refreshes UI elements that depend on theme attributes.
     * Override this method in subclasses to handle theme-specific UI updates.
     */
    protected open fun refreshThemeDependentElements() {
        // Default implementation - subclasses can override for specific behavior
        android.util.Log.d("BaseActivity", "Refreshing theme-dependent elements")
    }
}