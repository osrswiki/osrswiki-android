package com.omiyawaki.osrswiki.theme

import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.omiyawaki.osrswiki.R // Make sure R is imported from your app's package

// Represents the available themes in the application.
// Each theme has a marshalling ID for storage, a string tag, its Android style resource ID,
// and a string resource ID for its display name.
enum class Theme(
    val marshallingId: Int,
    val tag: String,
    @StyleRes val resourceId: Int,
    @StringRes val nameId: Int
) {
    OSRS_LIGHT(0, "osrs_light", R.style.Theme_OSRSWiki_OSRSLight, R.string.theme_name_osrs_light),
    OSRS_DARK(1, "osrs_dark", R.style.Theme_OSRSWiki_OSRSDark, R.string.theme_name_osrs_dark),
    WIKI_LIGHT(2, "wiki_light", R.style.Theme_OSRSWiki_WikiLight, R.string.theme_name_wiki_light),
    WIKI_DARK(3, "wiki_dark", R.style.Theme_OSRSWiki_WikiDark, R.string.theme_name_wiki_dark),
    WIKI_BLACK(4, "wiki_black", R.style.Theme_OSRSWiki_WikiBlack, R.string.theme_name_wiki_black);

    fun isDark(): Boolean {
        return this == OSRS_DARK || this == WIKI_DARK || this == WIKI_BLACK
    }

    // Add other helper properties if needed, e.g., isLight, isParchmentBased, etc.

    companion object {
        // The default theme to fall back to if no preference is set or an invalid one is found.
        // OSRS_LIGHT will be the app's default light theme.
        val DEFAULT_LIGHT = OSRS_LIGHT
        // OSRS_DARK will be the app's default dark theme (when chosen by user or system if DayNight without specific selection).
        val DEFAULT_DARK = OSRS_DARK

        fun ofMarshallingId(id: Int): Theme? {
            return entries.find { it.marshallingId == id }
        }

        fun ofTag(tag: String): Theme? {
            return entries.find { it.tag == tag }
        }
    }
}
