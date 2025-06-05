package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

/**
 * Fragment to display app settings.
 * It uses SettingsPreferenceLoader to load and manage preferences.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    companion object {
        const val TAG = "OsrsSettingsFragment" // Changed tag slightly for clarity if needed
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }

    private lateinit var preferenceLoader: SettingsPreferenceLoader

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // SettingsPreferenceLoader will call addPreferencesFromResource(R.xml.preferences)
        // So, we don't call setPreferencesFromResource(R.xml.preferences, rootKey) here directly.
        preferenceLoader = SettingsPreferenceLoader(this)
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
