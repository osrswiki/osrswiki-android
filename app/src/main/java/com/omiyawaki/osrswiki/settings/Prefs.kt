package com.omiyawaki.osrswiki.settings

import com.omiyawaki.osrswiki.R // Adjusted import for OSRSWiki R class

/** Shared preferences utility for convenient POJO access.  */
object Prefs {

    var appChannel: String? // Explicitly typing for clarity, can be inferred
        get() = PrefsIoUtil.getString(R.string.preference_key_app_channel, null)
        set(channel) = PrefsIoUtil.setString(R.string.preference_key_app_channel, channel)

    val appChannelKey: String // Explicitly typing for clarity
        get() = PrefsIoUtil.getKey(R.string.preference_key_app_channel)

    // Other preferences from Wikipedia's Prefs.kt can be added here if and when needed.
    // For now, only appChannel and appChannelKey are included to support ReleaseUtil.kt.
}
