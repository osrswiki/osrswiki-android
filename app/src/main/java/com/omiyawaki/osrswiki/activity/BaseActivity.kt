package com.omiyawaki.osrswiki.activity

import android.content.res.ColorStateList
import android.content.res.Configuration
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
    private var isApplyingTheme: Boolean = false

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
        // Guard against multiple simultaneous theme applications
        if (isApplyingTheme) {
            android.util.Log.d("BaseActivity", "Theme application already in progress, skipping duplicate call")
            return
        }
        
        val osrsWikiApp = applicationContext as OSRSWikiApp
        val newTheme = osrsWikiApp.getCurrentTheme()
        val newThemeId = newTheme.resourceId
        
        // Only apply if theme actually changed
        if (currentThemeId != newThemeId) {
            // Set guard flag
            isApplyingTheme = true
            
            try {
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
                        try {
                            // Refresh theme-dependent UI elements
                            refreshThemeDependentElements()
                            
                            // Notify fragments of theme change
                            notifyFragmentsOfThemeChange()
                            
                            android.util.Log.d("BaseActivity", "Dynamic theme application completed")
                        } finally {
                            // Clear guard flag after theme application is complete
                            isApplyingTheme = false
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BaseActivity", "Error during theme application", e)
                // Clear guard flag if exception occurs
                isApplyingTheme = false
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
        android.util.Log.d("BaseActivity", "Refreshing theme-dependent elements comprehensively")
        
        // Apply theme attributes to all views in the activity
        val contentView = findViewById<ViewGroup>(android.R.id.content)
        contentView?.let { 
            refreshViewThemeAttributes(it)
            
            // CRITICAL: Force comprehensive invalidation for cached UI elements
            forceCachedUIElementRefresh(it)
        }
        
        // Force refresh of the window and decorView for system UI elements
        try {
            window.decorView.invalidate()
            window.decorView.requestLayout()
            android.util.Log.d("BaseActivity", "Forced window decorView refresh")
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Failed to refresh window decorView", e)
        }
    }
    
    /**
     * Forces refresh of cached UI elements that might not respond to normal theme updates.
     * This is a comprehensive approach to handle edge cases and stubborn cached elements.
     */
    private fun forceCachedUIElementRefresh(view: View) {
        try {
            // Force invalidate the view and all its children recursively
            invalidateViewRecursively(view)
            
            // Handle specific view types that are known to cache styling
            when (view) {
                is androidx.recyclerview.widget.RecyclerView -> {
                    // Force adapter refresh for RecyclerViews
                    view.adapter?.notifyDataSetChanged()
                    view.invalidateItemDecorations()
                    android.util.Log.d("BaseActivity", "Forced RecyclerView refresh")
                }
                is androidx.viewpager2.widget.ViewPager2 -> {
                    // Force ViewPager2 refresh
                    view.adapter?.notifyDataSetChanged()
                    android.util.Log.d("BaseActivity", "Forced ViewPager2 refresh")
                }
                is androidx.fragment.app.FragmentContainerView -> {
                    // Handle fragment container views
                    view.invalidate()
                    view.requestLayout()
                    android.util.Log.d("BaseActivity", "Forced FragmentContainerView refresh")
                }
            }
            
            // If this is a ViewGroup, also handle special ViewGroup types
            if (view is ViewGroup) {
                when (view.javaClass.simpleName) {
                    "ConstraintLayout", "LinearLayout", "FrameLayout", "RelativeLayout" -> {
                        // Force layout refresh for common layout types
                        view.requestLayout()
                    }
                    "AppBarLayout", "CollapsingToolbarLayout" -> {
                        // Force refresh for Material Design layout components
                        view.invalidate()
                        view.requestLayout()
                        android.util.Log.d("BaseActivity", "Forced Material layout refresh: ${view.javaClass.simpleName}")
                    }
                }
                
                // Recursively apply to all children
                for (i in 0 until view.childCount) {
                    forceCachedUIElementRefresh(view.getChildAt(i))
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Error in forceCachedUIElementRefresh for ${view.javaClass.simpleName}", e)
        }
    }
    
    /**
     * Recursively invalidates a view and all its children to force complete redraw.
     */
    private fun invalidateViewRecursively(view: View) {
        try {
            view.invalidate()
            
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    invalidateViewRecursively(view.getChildAt(i))
                }
            }
        } catch (e: Exception) {
            // Ignore errors during invalidation - better to continue than crash
        }
    }
    
    /**
     * Recursively refreshes theme attributes for a view and all its children.
     * This forces views to re-read theme attributes after setTheme() is called.
     */
    protected fun refreshViewThemeAttributes(view: View) {
        try {
            // Force view to re-read theme attributes by applying common theme attributes
            refreshCommonThemeAttributes(view)
            
            // CRITICAL: Also refresh theme-dependent drawables
            refreshThemeDependentDrawables(view)
            
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
        
        // Preserve original hint text and colors
        val originalHint = textView.hint
        val originalHintColors = textView.hintTextColors
        
        // Check if this TextView has specific styling that should be preserved
        val isPreferenceText = isPreferenceTextView(textView)
        
        // Choose appropriate color attributes based on TextView type and context
        val textColorAttrs = when {
            isPreferenceText -> arrayOf(
                com.google.android.material.R.attr.colorOnSurface,      // Primary preference text
                android.R.attr.textColorPrimary,
                R.attr.primary_text_color
            )
            else -> arrayOf(
                // Comprehensive text color attribute list for general TextViews
                com.google.android.material.R.attr.colorOnSurface,          // Used in styles.xml, bottom nav, toolbar
                com.google.android.material.R.attr.colorOnSurfaceVariant,   // Used in activity_request_feature.xml, feedback
                
                // App-specific text colors
                R.attr.primary_text_color,              // Used in view_history_search_card.xml, map fragment
                R.attr.secondary_text_color,            // Used extensively for secondary text
                
                // Error and special states  
                com.google.android.material.R.attr.colorError,              // Used in fragment_search_results.xml
                com.google.android.material.R.attr.colorOnError,            // Used for error text
                com.google.android.material.R.attr.colorOnPrimary,          // Used for text on primary backgrounds
                com.google.android.material.R.attr.colorOnSecondary,        // Used for text on secondary backgrounds  
                com.google.android.material.R.attr.colorOnSecondaryContainer, // Used in activity_page.xml
                
                // Standard fallbacks
                android.R.attr.textColorPrimary,
                android.R.attr.textColor
            )
        }
        
        for (attr in textColorAttrs) {
            if (theme.resolveAttribute(attr, typedValue, true)) {
                try {
                    textView.setTextColor(typedValue.data)
                    
                    // Handle hint text and colors based on TextView type
                    // Check if this is the search bar EditText first
                    val isSearchBarEditText = textView.id == com.omiyawaki.osrswiki.R.id.toolbar_search_container
                    
                    if (isSearchBarEditText) {
                        // ALWAYS ensure search bar has hint text, regardless of originalHint
                        textView.hint = textView.context.getString(com.omiyawaki.osrswiki.R.string.page_toolbar_search_hint)
                        // Re-resolve hint color from current theme
                        val hintTypedValue = TypedValue()
                        if (theme.resolveAttribute(android.R.attr.textColorSecondary, hintTypedValue, true)) {
                            textView.setHintTextColor(hintTypedValue.data)
                            android.util.Log.d("BaseActivity", "Set search bar hint text and color from theme")
                        }
                    } else if (originalHint != null) {
                        // Handle regular TextViews only if they had original hints
                        textView.hint = originalHint
                        if (originalHintColors != null) {
                            textView.setHintTextColor(originalHintColors)
                        }
                    }
                    
                    android.util.Log.d("BaseActivity", "Applied ${getAttributeName(attr)} color to ${textView.javaClass.simpleName}")
                    break // Use the first one that resolves
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }
    
    
    private fun isPreferenceTextView(textView: TextView): Boolean {
        // Check if this is a preference TextView (usually has android.R.id.title or android.R.id.summary)
        return textView.id == android.R.id.title || 
               textView.id == android.R.id.summary ||
               textView.parent?.parent?.javaClass?.simpleName?.contains("Preference") == true
    }
    
    private fun getAttributeName(attr: Int): String {
        return when (attr) {
            android.R.attr.textColorPrimary -> "textColorPrimary"
            android.R.attr.textColorSecondary -> "textColorSecondary"
            android.R.attr.textColor -> "textColor"
            com.google.android.material.R.attr.colorOnSurface -> "colorOnSurface"
            com.google.android.material.R.attr.colorOnSurfaceVariant -> "colorOnSurfaceVariant"
            R.attr.primary_text_color -> "primary_text_color"
            R.attr.secondary_text_color -> "secondary_text_color"
            else -> "unknown_attr_$attr"
        }
    }
    
    private fun refreshImageViewTheme(imageView: ImageView, theme: android.content.res.Resources.Theme) {
        val typedValue = TypedValue()
        
        // Comprehensive tint color attribute list based on actual usage analysis
        val tintColorAttrs = arrayOf(
            // Most common tint colors from layout analysis
            com.google.android.material.R.attr.colorOnSurface,          // Used in view_unified_toolbar.xml
            com.google.android.material.R.attr.colorOnSurfaceVariant,   // Used in view_tab_header_with_search.xml, activity_page.xml
            R.attr.placeholder_color,               // Used in view_main_feed_search.xml, activity_search.xml
            R.attr.secondary_text_color,            // Used extensively for icon tints
            R.attr.primary_text_color,              // Used in fragment_map.xml
            
            // Material 3 surface colors
            com.google.android.material.R.attr.colorOnPrimary,          // Used in fragment_page.xml for FAB
            com.google.android.material.R.attr.colorOnSecondary,        // Used for icons on secondary backgrounds
            com.google.android.material.R.attr.colorOnError,            // Used for error state icons
            
            // Standard fallbacks
            android.R.attr.textColorSecondary,
            android.R.attr.textColorPrimary
        )
        
        for (attr in tintColorAttrs) {
            if (theme.resolveAttribute(attr, typedValue, true)) {
                try {
                    // Clear previous tint first to prevent color mixing
                    ImageViewCompat.setImageTintList(imageView, null)
                    // Apply new tint using ImageViewCompat for better compatibility
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
        
        // Comprehensive background color attribute list based on actual usage analysis
        val backgroundColorAttrs = arrayOf(
            // Material 3 surface colors (used extensively)
            com.google.android.material.R.attr.colorSurface,            // Used in activity_page.xml, activity_request_feature.xml, view_page_action_bar.xml
            com.google.android.material.R.attr.colorSurfaceVariant,     // Used in activity_request_feature.xml cards, OSRSWikiCardView
            com.google.android.material.R.attr.colorSecondaryContainer, // Used in activity_page.xml
            com.google.android.material.R.attr.colorError,              // Used in fragment_search_results.xml
            
            // App-specific background colors
            R.attr.paper_color,                     // Used extensively in fragments and layouts
            R.attr.base_color,                      // Used in view_history_date_header.xml
            R.attr.border_color,                    // Used in activity_main.xml
            R.attr.bottom_nav_background_color,     // Used for navigation
            
            // Standard fallbacks
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
            android.util.Log.d("BaseActivity", "Refreshing BottomNavigationView theme comprehensively")
            
            // Method 1: Force active indicator style refresh (CRITICAL for pill background)
            try {
                val typedValue = TypedValue()
                
                // Get the new active indicator color from theme
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
                    val indicatorColor = typedValue.data
                    android.util.Log.d("BaseActivity", "Setting new indicator color: ${Integer.toHexString(indicatorColor)}")
                    
                    // Try to programmatically update the active indicator color
                    // This is tricky because Material components cache the style internally
                    bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(indicatorColor)
                }
            } catch (e: Exception) {
                android.util.Log.w("BaseActivity", "Failed to refresh active indicator color", e)
            }
            
            // Method 2: Update icon and text color state lists with comprehensive attribute resolution
            try {
                val typedValue = TypedValue()
                
                // Get new colors from theme using comprehensive attribute list
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true)) {
                    val selectedColor = typedValue.data
                    // Try multiple attributes for unselected color based on layout analysis
                    val unselectedColorAttrs = arrayOf(
                        com.google.android.material.R.attr.colorOnSurface,          // Used in bottom nav selectors
                        com.google.android.material.R.attr.colorOnSurfaceVariant,   // Alternative for unselected state
                        R.attr.primary_text_color,              // App fallback
                        android.R.attr.textColorPrimary         // System fallback
                    )
                    
                    for (unselectedAttr in unselectedColorAttrs) {
                        if (theme.resolveAttribute(unselectedAttr, typedValue, true)) {
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
                            android.util.Log.d("BaseActivity", "Updated nav colors (selected: ${Integer.toHexString(selectedColor)}, unselected: ${Integer.toHexString(unselectedColor)})")
                            break // Use the first unselected color that resolves
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BaseActivity", "Method 2 failed for BottomNavigationView refresh", e)
            }
            
            // Method 3: Force Material component internal refresh via selection cycling
            try {
                val currentSelection = bottomNav.selectedItemId
                val menu = bottomNav.menu
                
                if (menu.size() > 1) {
                    // Find a different item to temporarily select
                    for (i in 0 until menu.size()) {
                        val item = menu.getItem(i)
                        if (item.itemId != currentSelection) {
                            // Temporarily change selection to force internal style refresh
                            bottomNav.selectedItemId = item.itemId
                            bottomNav.post {
                                // Restore original selection
                                bottomNav.selectedItemId = currentSelection
                                android.util.Log.d("BaseActivity", "Forced navigation refresh via selection cycling")
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("BaseActivity", "Method 3 failed for BottomNavigationView refresh", e)
            }
            
            // Method 4: Force complete view system refresh
            try {
                bottomNav.invalidate()
                bottomNav.requestLayout()
                
                // Also try to refresh the background
                val bgTypedValue = TypedValue()
                val backgroundColorAttrs = arrayOf(
                    com.google.android.material.R.attr.colorSurface,
                    R.attr.paper_color,
                    android.R.attr.colorBackground
                )
                
                for (backgroundAttr in backgroundColorAttrs) {
                    if (theme.resolveAttribute(backgroundAttr, bgTypedValue, true)) {
                        bottomNav.setBackgroundColor(bgTypedValue.data)
                        android.util.Log.d("BaseActivity", "Updated nav background: ${Integer.toHexString(bgTypedValue.data)}")
                        break
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.w("BaseActivity", "Method 4 failed for BottomNavigationView refresh", e)
            }
            
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
            
            // Handle specific custom views that use programmatic theme resolution
            when (view.javaClass.simpleName) {
                "OSRSWikiCardView" -> {
                    // This view programmatically resolves colorSurfaceVariant
                    refreshOSRSWikiCardView(view, theme)
                }
                "DottedLineView" -> {
                    // This view programmatically resolves colorOnSurfaceVariant
                    refreshDottedLineView(view, theme)
                }
            }
            
            // Handle Material Design components that need special refresh
            when (view) {
                is com.google.android.material.card.MaterialCardView -> {
                    refreshMaterialCardView(view, theme)
                }
                is com.google.android.material.button.MaterialButton -> {
                    refreshMaterialButton(view, theme)
                }
            }
            
            // Handle views with selectable backgrounds (ripple effects)
            refreshSelectableBackground(view, theme)
            
            // Force invalidation for views that might cache colors
            view.invalidate()
            
        } catch (e: Exception) {
            // Ignore errors for complex view refresh attempts
            android.util.Log.w("BaseActivity", "Error in refreshComplexViewTypes for ${view.javaClass.simpleName}", e)
        }
    }
    
    private fun refreshOSRSWikiCardView(view: View, theme: android.content.res.Resources.Theme) {
        try {
            val typedValue = TypedValue()
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
                // Force the custom view to refresh its background color
                view.setBackgroundColor(typedValue.data)
                android.util.Log.d("BaseActivity", "Refreshed OSRSWikiCardView with colorSurfaceVariant")
            }
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Failed to refresh OSRSWikiCardView", e)
        }
    }
    
    private fun refreshDottedLineView(view: View, theme: android.content.res.Resources.Theme) {
        try {
            val typedValue = TypedValue()
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)) {
                // Force the custom view to refresh - it needs to re-read the theme
                view.invalidate()
                android.util.Log.d("BaseActivity", "Refreshed DottedLineView invalidation for colorOnSurfaceVariant")
            }
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Failed to refresh DottedLineView", e)
        }
    }
    
    private fun refreshMaterialCardView(cardView: com.google.android.material.card.MaterialCardView, theme: android.content.res.Resources.Theme) {
        try {
            val typedValue = TypedValue()
            // Try different card background attributes
            val cardBackgroundAttrs = arrayOf(
                com.google.android.material.R.attr.colorSurfaceVariant,     // Most common for cards
                com.google.android.material.R.attr.colorSurface,            // Alternative
                R.attr.paper_color                      // App-specific fallback
            )
            
            for (attr in cardBackgroundAttrs) {
                if (theme.resolveAttribute(attr, typedValue, true)) {
                    cardView.setCardBackgroundColor(typedValue.data)
                    break
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Failed to refresh MaterialCardView", e)
        }
    }
    
    private fun refreshMaterialButton(button: com.google.android.material.button.MaterialButton, theme: android.content.res.Resources.Theme) {
        try {
            // Force button to re-read theme attributes by triggering internal refresh
            button.invalidate()
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Failed to refresh MaterialButton", e)
        }
    }
    
    private fun refreshSelectableBackground(view: View, theme: android.content.res.Resources.Theme) {
        try {
            val typedValue = TypedValue()
            val background = view.background
            
            // Check if this view uses selectable item backgrounds (common in layouts)
            val selectableAttrs = arrayOf(
                android.R.attr.selectableItemBackground,
                android.R.attr.selectableItemBackgroundBorderless
            )
            
            for (attr in selectableAttrs) {
                if (theme.resolveAttribute(attr, typedValue, true)) {
                    // Only apply if the view doesn't already have a complex background
                    if (background == null || shouldUpdateBackground(background)) {
                        val drawable = resources.getDrawable(typedValue.resourceId, theme)
                        view.background = drawable
                        break
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors for selectable background refresh
        }
    }
    
    private fun shouldUpdateBackground(background: Drawable?): Boolean {
        // Allow background updates for most cases, but be more selective
        // This prevents overriding important custom drawables while still updating theme colors
        return background?.constantState == null || 
               background.javaClass.simpleName.contains("ColorDrawable") ||
               background.javaClass.simpleName.contains("GradientDrawable") ||
               background.javaClass.simpleName.contains("RippleDrawable")
    }
    
    /**
     * Forces refresh of theme-dependent drawables by re-inflating them.
     * This is critical for views using drawable resources with theme attributes.
     */
    private fun refreshThemeDependentDrawables(view: View) {
        try {
            // List of known drawable resources that use theme attributes
            val themeDrawableIds = arrayOf(
                R.drawable.shape_search_box,              // Uses ?attr/colorSurfaceVariant
                R.drawable.search_text_view_rounded_background, // Likely uses theme colors
                android.R.attr.selectableItemBackground,  // System drawable with theme colors
                android.R.attr.selectableItemBackgroundBorderless // System drawable with theme colors
            )
            
            val currentBackground = view.background
            
            // Check if the view's background might be one of our theme-dependent drawables
            if (currentBackground != null && shouldForceDrawableRefresh(currentBackground)) {
                android.util.Log.d("BaseActivity", "Attempting to refresh theme drawable for view ${view.javaClass.simpleName}")
                
                // Try to determine the original drawable resource and re-apply it
                refreshDrawableFromTag(view) || refreshCommonThemeDrawables(view)
            }
            
        } catch (e: Exception) {
            android.util.Log.w("BaseActivity", "Error refreshing theme-dependent drawables", e)
        }
    }
    
    /**
     * Checks if a drawable should be force-refreshed for theme changes.
     */
    private fun shouldForceDrawableRefresh(drawable: Drawable): Boolean {
        val drawableClass = drawable.javaClass.simpleName
        return drawableClass.contains("RippleDrawable") ||
               drawableClass.contains("StateListDrawable") ||
               drawableClass.contains("GradientDrawable") ||
               drawable.constantState != null // Drawable from resources
    }
    
    /**
     * Attempts to refresh drawable using view tags (if available).
     */
    private fun refreshDrawableFromTag(view: View): Boolean {
        return try {
            val tag = view.tag
            if (tag is Int) {
                // Tag contains drawable resource ID
                val drawable = resources.getDrawable(tag, theme)
                view.background = drawable
                android.util.Log.d("BaseActivity", "Refreshed drawable from tag: $tag")
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Attempts to refresh common theme-dependent drawables by resource ID.
     */
    private fun refreshCommonThemeDrawables(view: View): Boolean {
        return try {
            // Check if this looks like a search box (common case mentioned by user)
            val parentClass = view.parent?.javaClass?.simpleName ?: ""
            val viewId = try { 
                resources.getResourceEntryName(view.id)
            } catch (e: Exception) { 
                "" 
            }
            
            when {
                viewId.contains("search", ignoreCase = true) -> {
                    // Try to apply search box drawable
                    val drawable = resources.getDrawable(R.drawable.shape_search_box, theme)
                    view.background = drawable
                    android.util.Log.d("BaseActivity", "Applied search box drawable to view: $viewId")
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}