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
            
            // Refresh the activity's UI elements to reflect new theme
            refreshActivityTheme()
            
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
    
    private fun refreshActivityTheme() {
        val activity = activity ?: return
        
        // Apply dynamic theme to the current activity
        if (activity is com.omiyawaki.osrswiki.activity.BaseActivity) {
            activity.runOnUiThread {
                activity.applyThemeDynamically()
                // Refresh preferences UI after theme is applied
                refreshPreferencesUI()
            }
        }
    }
    
    private fun refreshPreferencesUI() {
        // Refresh the preference list to apply new theme colors
        try {
            preferenceScreen?.let {
                // Refresh preference screen colors and styling
                val recyclerView = listView as? RecyclerView
                recyclerView?.invalidate()
                
                // Re-apply fonts with new theme
                setupFonts()
                
                L.d("AppearanceSettingsFragment: Preferences UI refreshed for new theme")
            }
        } catch (e: Exception) {
            L.e("AppearanceSettingsFragment: Error refreshing preferences UI: ${e.message}")
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