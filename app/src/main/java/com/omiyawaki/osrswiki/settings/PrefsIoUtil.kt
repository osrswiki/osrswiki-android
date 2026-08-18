package com.omiyawaki.osrswiki.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.omiyawaki.osrswiki.OSRSWikiApp

/**
 * Manages direct I/O for shared preferences. This utility works with String keys.
 */
internal object PrefsIoUtil {
    internal val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(OSRSWikiApp.instance)
    }

    fun getString(key: String, defaultValue: String?): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }
}
