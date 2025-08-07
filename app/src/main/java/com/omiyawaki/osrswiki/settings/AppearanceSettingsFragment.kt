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
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.log.L

class AppearanceSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey)

        val appThemeModePref = findPreference<ListPreference>(Prefs.KEY_APP_THEME_MODE)

        // Theme change listener for app theme mode preference.
        val themeChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            L.d("AppearanceSettingsFragment: Theme change requested to: $newValue")
            
            // Post the theme change to ensure preference is persisted first
            view?.post {
                applyThemeDynamically()
            }

            // Return true to allow the preference change to be saved.
            true
        }

        // Assign the listener to the app theme mode preference.
        appThemeModePref?.onPreferenceChangeListener = themeChangeListener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFonts()
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
    
    private fun applyThemeDynamically() {
        val activity = activity ?: return
        L.d("AppearanceSettingsFragment: Applying theme dynamically without recreation")
        
        try {
            // Get the new theme from the app
            val app = activity.applicationContext as OSRSWikiApp
            val newTheme = app.getCurrentTheme()
            
            L.d("AppearanceSettingsFragment: New theme: $newTheme")
            
            // Apply the new theme to current activity without recreation
            activity.setTheme(newTheme.resourceId)
            
            // CRITICAL FIX: Refresh the current activity's UI immediately
            // Don't call activity.applyThemeDynamically() as that would be recursive
            if (activity is com.omiyawaki.osrswiki.activity.BaseActivity) {
                val baseActivity = activity as com.omiyawaki.osrswiki.activity.BaseActivity
                
                // Update the BaseActivity's currentThemeId to match using reflection
                try {
                    val field = baseActivity.javaClass.superclass.getDeclaredField("currentThemeId")
                    field.isAccessible = true
                    field.setInt(baseActivity, newTheme.resourceId)
                } catch (e: Exception) {
                    L.w("AppearanceSettingsFragment: Could not update currentThemeId: ${e.message}")
                }
                
                // Call applyThemeDynamically but prevent recursion by setting a flag
                try {
                    // Use reflection to call the protected method refreshThemeDependentElements
                    val method = baseActivity.javaClass.superclass.getDeclaredMethod("refreshThemeDependentElements")
                    method.isAccessible = true
                    method.invoke(baseActivity)
                    L.d("AppearanceSettingsFragment: Called refreshThemeDependentElements via reflection")
                } catch (e: Exception) {
                    L.w("AppearanceSettingsFragment: Reflection failed, using fallback mechanism: ${e.message}")
                    // Fallback - call the activity's own applyThemeDynamically method
                    try {
                        baseActivity.applyThemeDynamically()
                        L.d("AppearanceSettingsFragment: Fallback - called applyThemeDynamically directly")
                    } catch (fallbackException: Exception) {
                        L.w("AppearanceSettingsFragment: Fallback also failed: ${fallbackException.message}")
                        // Final fallback - manual refresh
                        activity.window?.decorView?.invalidate()
                        activity.window?.decorView?.requestLayout()
                        L.d("AppearanceSettingsFragment: Final fallback - manual window refresh")
                    }
                }
            }
            
            // Refresh the fragment's own preferences UI immediately
            refreshPreferencesUI()
            
            // CRITICAL: Force immediate background refresh for the entire fragment view  
            view?.let { fragmentView ->
                forceFragmentBackgroundRefresh(fragmentView)
            }
            
            // Notify the global app about theme change for other activities/fragments
            notifyGlobalThemeChange()
            
            L.d("AppearanceSettingsFragment: Dynamic theme change completed")
            
        } catch (e: Exception) {
            L.e("AppearanceSettingsFragment: Error applying theme dynamically: ${e.message}")
            // Fallback to recreation if dynamic update fails
            L.d("AppearanceSettingsFragment: Falling back to activity recreation")
            activity.recreate()
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
                        // Force TextView to re-read theme colors
                        child.invalidate()
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