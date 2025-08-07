package com.omiyawaki.osrswiki.activity

import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
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
            
            // PHASE 1: Clear previous theme state
            clearPreviousThemeState()
            
            // PHASE 2: Update theme ID and apply new theme
            currentThemeId = newThemeId
            setTheme(newThemeId)
            
            // PHASE 3: Allow theme to take effect before refreshing views
            runOnUiThread {
                // Post to ensure setTheme() has taken effect
                window.decorView.post {
                    // Refresh theme-dependent UI elements
                    refreshThemeDependentElements()
                    
                    // Notify fragments of theme change
                    notifyFragmentsOfThemeChange()
                    
                    android.util.Log.d("BaseActivity", "Dynamic theme application completed")
                }
            }
        }
    }
    
    /**
     * Clears previous theme state to prevent color mixing between themes.
     * This ensures clean transitions from dark to light theme and vice versa.
     */
    private fun clearPreviousThemeState() {
        try {
            android.util.Log.d("BaseActivity", "Clearing previous theme state")
            
            // Clear any cached theme attribute values
            val contentView = findViewById<ViewGroup>(android.R.id.content)
            contentView?.let { clearViewThemeState(it) }
            
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Error clearing previous theme state", e)
        }
    }
    
    /**
     * Recursively clears theme-related state from views to prevent theme mixing.
     */
    private fun clearViewThemeState(view: View) {
        try {
            // Clear specific view type states
            when (view) {
                is TextView -> {
                    // Don't clear text colors here as it might break the UI
                    // The new theme will override them properly
                }
                is ImageView -> {
                    // Clear any previously applied tints
                    ImageViewCompat.setImageTintList(view, null)
                }
            }
            
            // If this is a ViewGroup, recursively clear children
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    clearViewThemeState(child)
                }
            }
            
        } catch (e: Exception) {
            // Ignore errors during state clearing - it's better to continue than crash
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
     * Applies comprehensive theme attributes to a view based on current theme.
     * This method handles all major theme attributes used throughout the app.
     */
    private fun refreshCommonThemeAttributes(view: View) {
        try {
            val theme = this.theme
            
            // Handle specific view types first
            when (view) {
                is BottomNavigationView -> refreshBottomNavigationTheme(view, theme)
                is TextView -> refreshTextViewTheme(view, theme)
                is ImageView -> refreshImageViewTheme(view, theme)
            }
            
            // Apply background theme attributes to all views
            refreshViewBackground(view, theme)
            
            // Handle special view types that need forced refresh
            refreshComplexViewTypes(view, theme)
            
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Error refreshing theme attributes for view ${view.javaClass.simpleName}", e)
        }
    }
    
    private fun refreshTextViewTheme(textView: TextView, theme: android.content.res.Resources.Theme) {
        val typedValue = TypedValue()
        
        // Try different text color attributes in order of preference
        val textColorAttrs = arrayOf(
            R.attr.primary_text_color,
            R.attr.secondary_text_color,
            android.R.attr.textColorPrimary,
            android.R.attr.textColor
        )
        
        for (attr in textColorAttrs) {
            if (theme.resolveAttribute(attr, typedValue, true)) {
                try {
                    textView.setTextColor(typedValue.data)
                    break // Use the first one that resolves
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }
    
    private fun refreshImageViewTheme(imageView: ImageView, theme: android.content.res.Resources.Theme) {
        val typedValue = TypedValue()
        
        // Try different tint color attributes
        val tintColorAttrs = arrayOf(
            R.attr.placeholder_color,
            R.attr.secondary_text_color,
            R.attr.primary_text_color,
            android.R.attr.textColorSecondary
        )
        
        for (attr in tintColorAttrs) {
            if (theme.resolveAttribute(attr, typedValue, true)) {
                try {
                    // Apply tint using ImageViewCompat for better compatibility
                    ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(typedValue.data))
                    break
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }
    
    private fun refreshViewBackground(view: View, theme: android.content.res.Resources.Theme) {
        val typedValue = TypedValue()
        
        // Try different background color attributes
        val backgroundColorAttrs = arrayOf(
            R.attr.paper_color,
            R.attr.base_color,
            android.R.attr.colorBackground,
            android.R.attr.windowBackground
        )
        
        for (attr in backgroundColorAttrs) {
            if (theme.resolveAttribute(attr, typedValue, true)) {
                try {
                    // Clear any previous background color state and apply new one
                    val currentBackground = view.background
                    if (currentBackground == null || shouldUpdateBackground(currentBackground)) {
                        // Clear previous background state
                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        // Apply new theme color
                        view.setBackgroundColor(typedValue.data)
                        break
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }
    
    private fun refreshBottomNavigationTheme(bottomNav: BottomNavigationView, theme: android.content.res.Resources.Theme) {
        try {
            android.util.Log.d("BaseActivity", "Refreshing BottomNavigationView theme")
            
            // Method 1: Try to update color state lists if they reference theme attributes
            try {
                val typedValue = TypedValue()
                
                // Get new colors from theme
                if (theme.resolveAttribute(android.R.attr.colorSecondary, typedValue, true)) {
                    val selectedColor = typedValue.data
                    if (theme.resolveAttribute(R.attr.primary_text_color, typedValue, true)) {
                        val unselectedColor = typedValue.data
                        
                        // Create new color state list
                        val states = arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        )
                        val colors = intArrayOf(selectedColor, unselectedColor)
                        val colorStateList = ColorStateList(states, colors)
                        
                        bottomNav.itemIconTintList = colorStateList
                        bottomNav.itemTextColor = colorStateList
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BaseActivity", "Method 1 failed for BottomNavigationView refresh", e)
            }
            
            // Method 2: Force re-inflation by temporarily changing and restoring selection
            try {
                val currentSelection = bottomNav.selectedItemId
                // Briefly select a different item to force refresh
                val menu = bottomNav.menu
                if (menu.size() > 1) {
                    for (i in 0 until menu.size()) {
                        val item = menu.getItem(i)
                        if (item.itemId != currentSelection) {
                            bottomNav.selectedItemId = item.itemId
                            bottomNav.post {
                                bottomNav.selectedItemId = currentSelection
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BaseActivity", "Method 2 failed for BottomNavigationView refresh", e)
            }
            
            // Method 3: Force complete invalidation
            bottomNav.invalidate()
            bottomNav.requestLayout()
            
        } catch (e: Exception) {
            android.util.Log.e("BaseActivity", "Error refreshing BottomNavigationView theme", e)
        }
    }
    
    private fun refreshComplexViewTypes(view: View, theme: android.content.res.Resources.Theme) {
        try {
            // Force RecyclerViews to refresh their adapters
            if (view is androidx.recyclerview.widget.RecyclerView) {
                view.adapter?.notifyDataSetChanged()
            }
            
            // Force invalidation for views that might cache colors
            view.invalidate()
            
        } catch (e: Exception) {
            // Ignore errors for complex view refresh attempts
        }
    }
    
    private fun shouldUpdateBackground(background: Drawable?): Boolean {
        // Allow background updates for most cases, but be more selective
        // This prevents overriding important custom drawables while still updating theme colors
        return background?.constantState == null || 
               background.javaClass.simpleName.contains("ColorDrawable") ||
               background.javaClass.simpleName.contains("GradientDrawable")
    }
}