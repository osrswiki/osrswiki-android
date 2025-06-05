package com.omiyawaki.osrswiki.settings

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.theme.ThemeChooserDialog

/**
 * Loads and manages preferences for the OSRSWiki settings screen.
 * Inspired by Wikipedia's SettingsPreferenceLoader.
 */
internal class SettingsPreferenceLoader(private val fragment: PreferenceFragmentCompat) {

    private val context = fragment.requireContext()
    // private val activity = fragment.requireActivity() // activity val not strictly needed if only using fragment for context/childFragmentManager

    fun loadPreferences() {
        // Load the preferences from the XML resource
        fragment.addPreferencesFromResource(R.xml.preferences)

        // Setup the theme preference
        setupThemePreference()

        // Other preferences can be set up here in the future
    }

    private fun setupThemePreference() {
        val themePreferenceKey = context.getString(R.string.osrs_preference_key_theme)
        val themePreference: Preference? = fragment.findPreference(themePreferenceKey)
        themePreference?.let { pref ->
            updateThemePreferenceSummary(pref)
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // Launch the ThemeChooserDialog
                ThemeChooserDialog.newInstance().show(fragment.childFragmentManager, ThemeChooserDialog.TAG)
                true // Indicate that the click was handled
            }
        }
    }

    /**
     * Updates the summary of the theme preference to display the currently selected theme's name.
     * This can be called when the fragment resumes or when the theme changes.
     */
    fun updateThemePreferenceSummary() {
        val themePreferenceKey = context.getString(R.string.osrs_preference_key_theme)
        val themePreference: Preference? = fragment.findPreference(themePreferenceKey)
        themePreference?.let { updateThemePreferenceSummary(it) }
    }

    private fun updateThemePreferenceSummary(preference: Preference) {
        val currentSelectedTheme = OSRSWikiApp.instance.getCurrentTheme() // Corrected: Use getter
        // Use the nameId from the Theme enum, which holds the @StringRes for the theme's display name
        val themeNameForDisplay = fragment.getString(currentSelectedTheme.nameId) // Corrected: Use nameId
        preference.summary = themeNameForDisplay
    }
}
