package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.omiyawaki.osrswiki.R // For R.xml.osrs_preferences if OsrsSettingsPreferenceLoader didn't call addPreferencesFromResource

/**
 * Fragment to display app settings.
 * It uses OsrsSettingsPreferenceLoader to load and manage preferences.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        const val TAG = "OsrsSettingsFragment" // Changed tag slightly for clarity if needed
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }

    private lateinit var preferenceLoader: OsrsSettingsPreferenceLoader

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // OsrsSettingsPreferenceLoader will call addPreferencesFromResource(R.xml.osrs_preferences)
        // So, we don't call setPreferencesFromResource(R.xml.osrs_preferences, rootKey) here directly.
        preferenceLoader = OsrsSettingsPreferenceLoader(this)
        preferenceLoader.loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        // Update the theme summary when the fragment resumes, in case the theme was changed.
        // This assumes preferenceLoader has been initialized.
        if (::preferenceLoader.isInitialized) {
            preferenceLoader.updateThemePreferenceSummary()
        }
    }
}
