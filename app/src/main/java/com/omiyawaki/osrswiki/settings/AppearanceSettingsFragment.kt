package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyRubikUILabel
import com.omiyawaki.osrswiki.util.applyInterBody
import com.omiyawaki.osrswiki.util.log.L

class AppearanceSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey)

        val appThemeModePref = findPreference<ListPreference>(Prefs.KEY_APP_THEME_MODE)
        val lightThemeChoicePref = findPreference<Preference>(Prefs.KEY_LIGHT_THEME_CHOICE)
        val darkThemeChoicePref = findPreference<Preference>(Prefs.KEY_DARK_THEME_CHOICE)

        // Set the initial enabled state for the light/dark theme choices.
        updateThemeChoiceStates(appThemeModePref?.value, lightThemeChoicePref, darkThemeChoicePref)

        // A single listener for all theme-related preferences.
        val themeChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            // If the preference being changed is the main app theme mode,
            // update the enabled state of the dependent preferences.
            if (preference.key == Prefs.KEY_APP_THEME_MODE) {
                updateThemeChoiceStates(newValue as? String, lightThemeChoicePref, darkThemeChoicePref)
            }

            // Post the recreate call to the view's message queue. This ensures the
            // preference value has time to be persisted before the activity is destroyed,
            // and provides immediate visual feedback for the theme change.
            view?.post {
                activity?.recreate()
            }

            // Return true to allow the preference change to be saved.
            true
        }

        // Assign the same listener to all three theme preferences.
        appThemeModePref?.onPreferenceChangeListener = themeChangeListener
        lightThemeChoicePref?.onPreferenceChangeListener = themeChangeListener
        darkThemeChoicePref?.onPreferenceChangeListener = themeChangeListener
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
        // Find TextViews in the preference item and apply appropriate fonts
        val title = itemView.findViewById<TextView>(android.R.id.title)
        val summary = itemView.findViewById<TextView>(android.R.id.summary)
        
        title?.applyInterBody()
        summary?.applyRubikUILabel()
    }

    /**
     * Enables or disables the light/dark theme choice preferences based on the app theme mode.
     */
    private fun updateThemeChoiceStates(
        mode: String?,
        lightPref: Preference?,
        darkPref: Preference?
    ) {
        when (mode) {
            "light" -> {
                lightPref?.isEnabled = true
                darkPref?.isEnabled = false
            }
            "dark" -> {
                lightPref?.isEnabled = false
                darkPref?.isEnabled = true
            }
            "auto" -> {
                lightPref?.isEnabled = true
                darkPref?.isEnabled = true
            }
        }
    }

    companion object {
        const val TAG = "AppearanceSettingsFragment"
        fun newInstance(): AppearanceSettingsFragment {
            return AppearanceSettingsFragment()
        }
    }
}