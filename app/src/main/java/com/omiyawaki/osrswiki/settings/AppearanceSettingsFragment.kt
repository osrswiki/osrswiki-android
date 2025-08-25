package com.omiyawaki.osrswiki.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.log.L

class AppearanceSettingsFragment : PreferenceFragmentCompat(), ThemeAware {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Debug: Verify preference theme resolution
        L.d("ThemeCheck: --- Verifying Preference Theme ---")
        val typedValue = android.util.TypedValue()
        val themeResolved = requireContext().theme.resolveAttribute(
            androidx.preference.R.attr.preferenceTheme,
            typedValue,
            true
        )
        
        if (themeResolved) {
            val themeResId = typedValue.resourceId
            val themeName = try {
                resources.getResourceName(themeResId)
            } catch (e: Exception) {
                "ID: $themeResId (name not found)"
            }
            L.d("ThemeCheck: Preference theme is RESOLVED: $themeName (ID: $themeResId)")
        } else {
            L.d("ThemeCheck: Preference theme is NOT RESOLVED. Using default theme.")
        }
        
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey)

        val appThemeModePref = findPreference<ListPreference>(Prefs.KEY_APP_THEME_MODE)

        // Theme change listener for app theme mode preference.
        val themeChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            L.d("AppearanceSettingsFragment: Theme change requested to: $newValue")
            
            // Post the theme change to ensure preference is persisted first
            view?.post {
                // Send broadcast to notify all activities about theme change
                // This will trigger broadcast receivers in all activities (including current one)
                // which will call BaseActivity.applyThemeDynamically() and trigger onThemeChanged() on ThemeAware fragments
                notifyGlobalThemeChange()
            }
            
            // Return true to allow the preference change to be saved.
            true
        }

        // Assign the listener to the app theme mode preference.
        appThemeModePref?.onPreferenceChangeListener = themeChangeListener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // setupFonts() // DISABLED: Testing theme-based styling approach
    }

    private fun setupFonts() {
        L.d("AppearanceSettingsFragment: Setting up fonts...")
        
        // Apply fonts to preference TextViews by traversing the RecyclerView
        val recyclerView = listView as? RecyclerView
        recyclerView?.post {
            applyFontsToPreferences(recyclerView)
        }
    }

    private fun applyFontsToPreferences(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val itemView = recyclerView.getChildAt(i)
            applyFontsToPreferenceItem(itemView)
        }
        
        // Also set up a scroll listener to apply fonts to newly visible items
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                for (i in 0 until recyclerView.childCount) {
                    val itemView = recyclerView.getChildAt(i)
                    applyFontsToPreferenceItem(itemView)
                }
            }
        })
        
        L.d("AppearanceSettingsFragment: Fonts applied to preference items")
    }

    private fun applyFontsToPreferenceItem(itemView: View) {
        // Let preference text use system fonts - no custom font application needed
        // This aligns with the app's migration to system fonts for non-heading text
    }

    override fun onThemeChanged() {
        if (!isAdded || view == null) {
            return
        }
        
        L.d("AppearanceSettingsFragment: onThemeChanged called - refreshing preferences UI")
        
        // refreshPreferencesThemeDirectly() // DISABLED: Testing theme-based styling approach
    }
    
    private fun refreshPreferencesThemeDirectly() {
        try {
            L.d("AppearanceSettingsFragment: Refreshing preferences theme directly")
            
            // Refresh the fragment's root view background
            view?.let { fragmentView ->
                val typedValue = android.util.TypedValue()
                val theme = requireContext().theme
                
                // Apply new background color
                val backgroundAttrs = arrayOf(
                    com.omiyawaki.osrswiki.R.attr.paper_color,
                    com.google.android.material.R.attr.colorSurface,
                    android.R.attr.colorBackground
                )
                
                for (attr in backgroundAttrs) {
                    if (theme.resolveAttribute(attr, typedValue, true)) {
                        fragmentView.setBackgroundColor(typedValue.data)
                        L.d("AppearanceSettingsFragment: Applied fragment background color")
                        break
                    }
                }
            }
            
            // Refresh the RecyclerView and preference items using proper preference refresh
            val recyclerView = listView as? RecyclerView
            recyclerView?.let { rv ->
                // Apply new theme background to the RecyclerView
                val typedValue = android.util.TypedValue()
                val theme = requireContext().theme
                
                if (theme.resolveAttribute(com.omiyawaki.osrswiki.R.attr.paper_color, typedValue, true)) {
                    rv.setBackgroundColor(typedValue.data)
                }
                
                // Force adapter refresh to pick up new theme colors - this is critical for preferences
                rv.adapter?.notifyDataSetChanged()
                
                // Force complete refresh
                rv.invalidate()
                rv.requestLayout()
                
                L.d("AppearanceSettingsFragment: RecyclerView refreshed with new theme")
                
                // CRITICAL: Apply theme colors to preference text views
                // PreferenceFragmentCompat uses complex view hierarchies that generic theme refresh misses
                refreshPreferenceTextViews(rv, theme)
            }
            
            L.d("AppearanceSettingsFragment: Preferences theme refresh completed successfully")
            
        } catch (e: Exception) {
            L.e("AppearanceSettingsFragment: Error refreshing preferences theme directly: ${e.message}")
        }
    }
    
    private fun refreshPreferenceTextViews(recyclerView: RecyclerView, theme: android.content.res.Resources.Theme) {
        try {
            L.d("AppearanceSettingsFragment: Refreshing preference text views with new theme")
            
            val typedValue = android.util.TypedValue()
            
            // Resolve theme colors for preferences
            val primaryTextColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                typedValue.data
            } else if (theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)) {
                typedValue.data
            } else {
                null
            }
            
            val secondaryTextColor = if (theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) {
                typedValue.data
            } else if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)) {
                typedValue.data
            } else {
                null
            }
            
            // Traverse all visible preference items
            for (i in 0 until recyclerView.childCount) {
                val itemView = recyclerView.getChildAt(i)
                refreshPreferenceItemTextViews(itemView, primaryTextColor, secondaryTextColor)
            }
            
            // Also set up a scroll listener to refresh newly visible items
            // Remove existing listeners to avoid duplicates
            val existingListeners = mutableListOf<RecyclerView.OnScrollListener>()
            try {
                // Unfortunately there's no clean way to remove specific listeners, so we'll add ours
                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        // Refresh any newly visible preference items
                        for (j in 0 until recyclerView.childCount) {
                            val itemView = recyclerView.getChildAt(j)
                            refreshPreferenceItemTextViews(itemView, primaryTextColor, secondaryTextColor)
                        }
                    }
                })
            } catch (e: Exception) {
                L.w("AppearanceSettingsFragment: Could not set scroll listener: ${e.message}")
            }
            
            L.d("AppearanceSettingsFragment: Preference text views refreshed")
            
        } catch (e: Exception) {
            L.e("AppearanceSettingsFragment: Error refreshing preference text views: ${e.message}")
        }
    }
    
    private fun refreshPreferenceItemTextViews(itemView: android.view.View, primaryTextColor: Int?, secondaryTextColor: Int?) {
        try {
            // Find preference title (primary text) - usually android.R.id.title
            val titleView = itemView.findViewById<android.widget.TextView>(android.R.id.title)
            titleView?.let { textView ->
                primaryTextColor?.let { color ->
                    textView.setTextColor(color)
                    L.d("AppearanceSettingsFragment: Applied primary color to preference title: ${textView.text}")
                }
            }
            
            // Find preference summary (secondary text) - usually android.R.id.summary
            val summaryView = itemView.findViewById<android.widget.TextView>(android.R.id.summary)
            summaryView?.let { textView ->
                secondaryTextColor?.let { color ->
                    textView.setTextColor(color)
                    L.d("AppearanceSettingsFragment: Applied secondary color to preference summary: ${textView.text}")
                }
            }
            
            // Also check for any other TextViews that might be in the preference layout
            if (itemView is android.view.ViewGroup) {
                refreshTextViewsInViewGroup(itemView, primaryTextColor, secondaryTextColor)
            }
            
        } catch (e: Exception) {
            L.w("AppearanceSettingsFragment: Error refreshing preference item: ${e.message}")
        }
    }
    
    private fun refreshTextViewsInViewGroup(viewGroup: android.view.ViewGroup, primaryTextColor: Int?, secondaryTextColor: Int?) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is android.widget.TextView -> {
                    // Skip if we already handled this TextView by ID
                    if (child.id != android.R.id.title && child.id != android.R.id.summary) {
                        // Apply primary color to other text views by default
                        primaryTextColor?.let { color ->
                            child.setTextColor(color)
                        }
                    }
                }
                is android.view.ViewGroup -> {
                    refreshTextViewsInViewGroup(child, primaryTextColor, secondaryTextColor)
                }
            }
        }
    }
    
    private fun refreshPreferencesUI() {
        // Refresh the preference list to apply new theme colors
        try {
            preferenceScreen?.let {
                // Force complete refresh of the preference RecyclerView
                val recyclerView = listView as? RecyclerView
                recyclerView?.let { rv ->
                    // Invalidate and request layout to force theme color refresh
                    rv.invalidate()
                    rv.requestLayout()
                    
                    // Force adapter refresh to pick up new theme colors
                    rv.adapter?.notifyDataSetChanged()
                    
                    // Apply new theme background color to RecyclerView with comprehensive fallback
                    applyNewBackgroundColor(rv)
                    
                    // Force refresh of all visible preference items
                    forceRefreshVisiblePreferences(rv)
                    
                    // Re-apply fonts with new theme colors
                    setupFonts()
                }
                
                L.d("AppearanceSettingsFragment: Comprehensive preferences UI refresh completed")
            }
        } catch (e: Exception) {
            L.e("AppearanceSettingsFragment: Error refreshing preferences UI: ${e.message}")
        }
    }
    
    private fun applyNewBackgroundColor(recyclerView: RecyclerView) {
        try {
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme
            
            // Try multiple background color attributes in order of preference
            val backgroundAttrs = arrayOf(
                com.omiyawaki.osrswiki.R.attr.paper_color,
                com.google.android.material.R.attr.colorSurface,
                android.R.attr.colorBackground
            )
            
            for (attr in backgroundAttrs) {
                if (theme.resolveAttribute(attr, typedValue, true)) {
                    // Apply to RecyclerView
                    recyclerView.setBackgroundColor(typedValue.data)
                    
                    // CRITICAL: Also apply to parent views to ensure complete background coverage
                    applyBackgroundToParentViews(recyclerView, typedValue.data)
                    
                    L.d("AppearanceSettingsFragment: Applied background color from ${getAttributeName(attr)}: ${Integer.toHexString(typedValue.data)}")
                    break
                }
            }
        } catch (e: Exception) {
            L.w("AppearanceSettingsFragment: Error applying background color: ${e.message}")
        }
    }
    
    private fun applyBackgroundToParentViews(view: android.view.View, backgroundColor: Int) {
        try {
            var currentView = view.parent
            var level = 0
            val maxLevels = 3 // Limit to prevent going too far up the hierarchy
            
            while (currentView != null && currentView is android.view.View && level < maxLevels) {
                val parentView = currentView as android.view.View
                
                // Apply background to suitable parent views
                if (shouldApplyBackgroundToParent(parentView)) {
                    parentView.setBackgroundColor(backgroundColor)
                    L.d("AppearanceSettingsFragment: Applied background to parent view level $level: ${parentView.javaClass.simpleName}")
                }
                
                currentView = parentView.parent
                level++
            }
        } catch (e: Exception) {
            L.w("AppearanceSettingsFragment: Error applying background to parent views: ${e.message}")
        }
    }
    
    private fun shouldApplyBackgroundToParent(view: android.view.View): Boolean {
        // Only apply background to certain types of parent views to avoid breaking other UI elements
        val className = view.javaClass.simpleName
        return className.contains("FrameLayout") || 
               className.contains("LinearLayout") ||
               className.contains("PreferenceRecyclerViewFragment") ||
               view.id == android.R.id.list_container ||
               view.id == androidx.preference.R.id.recycler_view
    }
    
    private fun forceFragmentBackgroundRefresh(fragmentView: android.view.View) {
        try {
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme
            
            // Apply new theme background color to the fragment's root view
            val backgroundAttrs = arrayOf(
                com.omiyawaki.osrswiki.R.attr.paper_color,
                com.google.android.material.R.attr.colorSurface,
                android.R.attr.colorBackground
            )
            
            for (attr in backgroundAttrs) {
                if (theme.resolveAttribute(attr, typedValue, true)) {
                    fragmentView.setBackgroundColor(typedValue.data)
                    L.d("AppearanceSettingsFragment: Applied fragment view background: ${Integer.toHexString(typedValue.data)}")
                    break
                }
            }
            
            // Force complete invalidation and redraw
            fragmentView.invalidate()
            fragmentView.requestLayout()
            
        } catch (e: Exception) {
            L.w("AppearanceSettingsFragment: Error forcing fragment background refresh: ${e.message}")
        }
    }
    
    private fun forceRefreshVisiblePreferences(recyclerView: RecyclerView) {
        try {
            // Force refresh all visible preference items
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                child?.let { preferenceView ->
                    // Force complete refresh of each preference item
                    preferenceView.invalidate()
                    preferenceView.requestLayout()
                    
                    // If this preference has text views, force them to re-read theme colors
                    refreshTextViewsInPreference(preferenceView)
                }
            }
            L.d("AppearanceSettingsFragment: Forced refresh of ${recyclerView.childCount} visible preferences")
        } catch (e: Exception) {
            L.w("AppearanceSettingsFragment: Error refreshing visible preferences: ${e.message}")
        }
    }
    
    private fun refreshTextViewsInPreference(preferenceView: android.view.View) {
        try {
            if (preferenceView is android.view.ViewGroup) {
                for (i in 0 until preferenceView.childCount) {
                    val child = preferenceView.getChildAt(i)
                    if (child is android.widget.TextView) {
                        // CRITICAL: Apply actual theme colors instead of just invalidating
                        applyTextViewThemeColors(child)
                    } else if (child is android.view.ViewGroup) {
                        // Recursively refresh nested ViewGroups
                        refreshTextViewsInPreference(child)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors in preference item refresh
        }
    }
    
    private fun applyTextViewThemeColors(textView: android.widget.TextView) {
        try {
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme
            
            // Determine if this is a title or summary by checking text characteristics
            val isTitle = isPreferenceTitle(textView)
            
            if (isTitle) {
                // Apply primary text color for titles
                val titleAttrs = arrayOf(
                    com.google.android.material.R.attr.colorOnSurface,          // Material 3 primary
                    android.R.attr.textColorPrimary,                           // System primary
                    R.attr.primary_text_color                                  // App fallback
                )
                
                for (attr in titleAttrs) {
                    if (theme.resolveAttribute(attr, typedValue, true)) {
                        textView.setTextColor(typedValue.data)
                        L.d("AppearanceSettingsFragment: Applied title color from ${getAttributeName(attr)}")
                        break
                    }
                }
            } else {
                // Apply secondary text color for summaries
                val summaryAttrs = arrayOf(
                    android.R.attr.textColorSecondary,                         // System secondary (most common)
                    com.google.android.material.R.attr.colorOnSurfaceVariant,  // Material 3 secondary
                    R.attr.secondary_text_color                                // App fallback
                )
                
                for (attr in summaryAttrs) {
                    if (theme.resolveAttribute(attr, typedValue, true)) {
                        textView.setTextColor(typedValue.data)
                        L.d("AppearanceSettingsFragment: Applied summary color from ${getAttributeName(attr)}: ${textView.text}")
                        break
                    }
                }
            }
            
            // Force immediate refresh
            textView.invalidate()
            
        } catch (e: Exception) {
            L.w("AppearanceSettingsFragment: Error applying TextView theme colors: ${e.message}")
        }
    }
    
    private fun isPreferenceTitle(textView: android.widget.TextView): Boolean {
        // Heuristics to determine if this TextView is a title or summary
        val text = textView.text?.toString() ?: ""
        val textSize = textView.textSize
        val typeface = textView.typeface
        
        // Title characteristics: larger text, often bold, shorter text
        // Common title texts we know about
        val knownTitles = setOf(
            "Theme", 
            "Tables"
        )
        
        // Summary characteristics: smaller text, longer descriptive text
        val knownSummaries = setOf(
            "Dark", 
            "Light", 
            "Auto", 
            "Collapses infoboxes by default in articles"
        )
        
        return when {
            knownTitles.contains(text) -> true
            knownSummaries.contains(text) -> false
            // Fallback heuristics
            textSize > 16 -> true  // Larger text is likely a title
            text.length < 20 -> true  // Short text is likely a title  
            text.contains("by default") || text.contains("articles") -> false // Long descriptive text is summary
            else -> false // Default to summary to be safe
        }
    }
    
    private fun getAttributeName(attr: Int): String {
        return when (attr) {
            com.omiyawaki.osrswiki.R.attr.paper_color -> "paper_color"
            com.google.android.material.R.attr.colorSurface -> "colorSurface"
            android.R.attr.colorBackground -> "colorBackground"
            else -> "unknown_attr_$attr"
        }
    }
    
    private fun notifyGlobalThemeChange() {
        try {
            // Send local broadcast to notify other activities about theme change
            val intent = Intent(ACTION_THEME_CHANGED)
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
            L.d("AppearanceSettingsFragment: Global theme change broadcast sent")
        } catch (e: Exception) {
            L.e("AppearanceSettingsFragment: Error sending theme change broadcast: ${e.message}")
        }
    }


    companion object {
        const val TAG = "AppearanceSettingsFragment"
        const val ACTION_THEME_CHANGED = "com.omiyawaki.osrswiki.THEME_CHANGED"
        
        fun newInstance(): AppearanceSettingsFragment {
            return AppearanceSettingsFragment()
        }
    }
}