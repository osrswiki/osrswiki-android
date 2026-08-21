package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import com.omiyawaki.osrswiki.R

class OfflineSettingsFragment : osrsSettingsPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_offline, rootKey)
    }

    companion object {
        const val TAG = "OfflineSettingsFragment"
        fun newInstance(): OfflineSettingsFragment {
            return OfflineSettingsFragment()
        }
    }
}
