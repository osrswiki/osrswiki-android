package com.omiyawaki.osrswiki.undergroundmaps.ui

/**
 * Host-app bridge so map chrome can reuse Appearance floor numbering without
 * depending on the `app` module (Prefs / article WebView).
 */
fun interface osrsMapFloorNumberingHost {
    fun osrsMapFloorUsEntranceIsFirstFloor(): Boolean
}
