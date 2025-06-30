package com.omiyawaki.osrswiki.settings

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.omiyawaki.osrswiki.OSRSWikiApp

object Prefs {
    private fun getPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(OSRSWikiApp.instance)
    }

    // --- Existing Preferences (Restored and Refactored) ---
    private const val KEY_DOWNLOAD_READING_LIST_ARTICLES = "isDownloadingReadingListArticlesEnabled"
    private const val DEFAULT_DOWNLOAD_READING_LIST_ARTICLES = true

    /**
     * Manages the setting for whether to automatically download articles added to reading lists.
     */
    var isDownloadingReadingListArticlesEnabled: Boolean
        get() = getPrefs().getBoolean(KEY_DOWNLOAD_READING_LIST_ARTICLES, DEFAULT_DOWNLOAD_READING_LIST_ARTICLES)
        set(value) = getPrefs().edit().putBoolean(KEY_DOWNLOAD_READING_LIST_ARTICLES, value).apply()


    // --- New Theme Preferences ---
    const val KEY_APP_THEME_MODE = "app_theme_mode"
    const val KEY_LIGHT_THEME_CHOICE = "light_theme_choice"
    const val KEY_DARK_THEME_CHOICE = "dark_theme_choice"

    private const val DEFAULT_APP_THEME_MODE = "auto"
    private const val DEFAULT_LIGHT_THEME_CHOICE = "osrs_light"
    private const val DEFAULT_DARK_THEME_CHOICE = "osrs_dark"

    /**
     * Manages the selected application theme mode.
     * Values: "light", "dark", "auto"
     */
    var appThemeMode: String
        get() = getPrefs().getString(KEY_APP_THEME_MODE, DEFAULT_APP_THEME_MODE) ?: DEFAULT_APP_THEME_MODE
        set(value) = getPrefs().edit().putString(KEY_APP_THEME_MODE, value).apply()

    /**
     * Manages the selected light theme variant.
     * Values: "osrs_light", "wiki_light"
     */
    var lightThemeChoice: String
        get() = getPrefs().getString(KEY_LIGHT_THEME_CHOICE, DEFAULT_LIGHT_THEME_CHOICE) ?: DEFAULT_LIGHT_THEME_CHOICE
        set(value) = getPrefs().edit().putString(KEY_LIGHT_THEME_CHOICE, value).apply()

    /**
     * Manages the selected dark theme variant.
     * Values: "osrs_dark", "wiki_dark", "wiki_black"
     */
    var darkThemeChoice: String
        get() = getPrefs().getString(KEY_DARK_THEME_CHOICE, DEFAULT_DARK_THEME_CHOICE) ?: DEFAULT_DARK_THEME_CHOICE
        set(value) = getPrefs().edit().putString(KEY_DARK_THEME_CHOICE, value).apply()
}
