package com.omiyawaki.osrswiki.settings

/**
 * Provides easy access to app-wide shared preferences.
 */
object Prefs {
    // String constants for preference keys, to be used by fragments and the data layer.
    const val KEY_APP_THEME_MODE = "appThemeMode"
    const val KEY_LIGHT_THEME_CHOICE = "lightThemeChoice"
    const val KEY_DARK_THEME_CHOICE = "darkThemeChoice"
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
     * Gets the current theme mode (e.g., default, light, dark).
     * Defaults to "default".
     */
    val appThemeMode
        get() = PrefsIoUtil.getString(KEY_APP_THEME_MODE, "default") ?: "default"

    /**
     * Gets the specific choice for the light theme.
     * Defaults to "light".
     */
    val lightThemeChoice
        get() = PrefsIoUtil.getString(KEY_LIGHT_THEME_CHOICE, "light") ?: "light"

    /**
     * Gets the specific choice for the dark theme.
     * Defaults to "dark".
     */
    val darkThemeChoice
        get() = PrefsIoUtil.getString(KEY_DARK_THEME_CHOICE, "dark") ?: "dark"
}
