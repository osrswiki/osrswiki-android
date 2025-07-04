package com.omiyawaki.osrswiki.settings

/**
 * Provides easy access to app-wide shared preferences.
 */
object Prefs {
    // String constants for preference keys, to be used by fragments and the data layer.
    // These keys MUST match the keys defined in res/xml/preferences.xml
    const val KEY_APP_THEME_MODE = "app_theme_mode"
    const val KEY_LIGHT_THEME_CHOICE = "light_theme_choice"
    const val KEY_DARK_THEME_CHOICE = "dark_theme_choice"
    const val KEY_DOWNLOAD_READING_LIST_ARTICLES = "downloadReadingListArticles"
    const val KEY_COLLAPSE_TABLES = "collapseTables"

    /**
     * Gets whether tables in WebViews should be collapsed by default.
     * Defaults to true.
     */
    val isCollapseTablesEnabled
        get() = PrefsIoUtil.getBoolean(KEY_COLLAPSE_TABLES, true)

    /**
     * Gets whether reading list articles should be downloaded for offline use.
     * Defaults to true.
     */
    val isDownloadingReadingListArticlesEnabled
        get() = PrefsIoUtil.getBoolean(KEY_DOWNLOAD_READING_LIST_ARTICLES, true)

    /**
     * Gets the current theme mode (e.g., auto, light, dark).
     * Defaults to "auto".
     */
    val appThemeMode
        get() = PrefsIoUtil.getString(KEY_APP_THEME_MODE, "auto") ?: "auto"

    /**
     * Gets the specific choice for the light theme.
     * Defaults to "osrs_light".
     */
    val lightThemeChoice
        get() = PrefsIoUtil.getString(KEY_LIGHT_THEME_CHOICE, "osrs_light") ?: "osrs_light"

    /**
     * Gets the specific choice for the dark theme.
     * Defaults to "osrs_dark".
     */
    val darkThemeChoice
        get() = PrefsIoUtil.getString(KEY_DARK_THEME_CHOICE, "osrs_dark") ?: "osrs_dark"
}
