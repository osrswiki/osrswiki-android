package com.omiyawaki.osrswiki.activity

import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
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
        // was last created. If so, apply the new theme dynamically without recreation.
        val globalThemeId = (application as OSRSWikiApp).getCurrentTheme().resourceId
        if (currentThemeId != 0 && currentThemeId != globalThemeId) {
            android.util.Log.d("BaseActivity", "Theme changed during resume, applying dynamically")
            applyThemeDynamically()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Check if the UI mode has changed (e.g., system light/dark theme switch).
        // If the app's theme is set to "auto", apply the new system-driven theme dynamically.
        if (Prefs.appThemeMode == "auto") {
            android.util.Log.d("BaseActivity", "Configuration changed with auto theme, applying dynamically")
            // The theme is resolved in OSRSWikiApp.getCurrentTheme() which reads the system
            // state, so we can apply it dynamically without recreation.
            applyThemeDynamically()
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
        // Default implementation - refresh theme attributes for root view and children
        android.util.Log.d("BaseActivity", "Refreshing theme-dependent elements")
        
        // Apply theme attributes to all views in the activity
        val contentView = findViewById<ViewGroup>(android.R.id.content)
        contentView?.let { refreshViewThemeAttributes(it) }
    }
    
    /**
     * Recursively refreshes theme attributes for a view and all its children.
     * This forces views to re-read theme attributes after setTheme() is called.
     */
    protected fun refreshViewThemeAttributes(view: View) {
        try {
            // Force view to re-read theme attributes by applying common theme attributes
            refreshCommonThemeAttributes(view)
            
            // If this is a ViewGroup, recursively apply to all children
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    refreshViewThemeAttributes(child)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Error refreshing theme attributes for view", e)
        }
    }
    
    /**
     * Applies common theme attributes to a view based on current theme.
     */
    private fun refreshCommonThemeAttributes(view: View) {
        val typedValue = TypedValue()
        val theme = this.theme
        
        // Apply paper_color attribute to views that might use it
        if (theme.resolveAttribute(R.attr.paper_color, typedValue, true)) {
            try {
                // Only apply background if the view doesn't have a specific drawable background
                if (view.background == null || view.background.constantState == null) {
                    view.setBackgroundColor(typedValue.data)
                }
            } catch (e: Exception) {
                // Ignore errors - some views might not support background changes
            }
        }
        
        // Apply text color attributes to text views
        if (view is android.widget.TextView) {
            if (theme.resolveAttribute(R.attr.primary_text_color, typedValue, true)) {
                try {
                    view.setTextColor(typedValue.data)
                } catch (e: Exception) {
                    // Ignore errors
                }
            }
        }
    }
}