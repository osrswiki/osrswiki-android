package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.omiyawaki.osrswiki.R

class OfflineSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_offline, rootKey)
        
        // No special handling needed currently - just the cache size limit preference
        // Future offline/storage preferences can be added here with their own logic
    }

    companion object {
        const val TAG = "OfflineSettingsFragment"
        fun newInstance(): OfflineSettingsFragment {
            return OfflineSettingsFragment()
        }
    }
}