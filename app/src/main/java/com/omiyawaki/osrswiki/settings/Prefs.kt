package com.omiyawaki.osrswiki.settings

/**
 * Provides easy access to app-wide shared preferences.
 */
object Prefs {
    // String constants for preference keys, to be used by fragments and the data layer.
    // These keys MUST match the keys defined in res/xml/preferences_appearance.xml and res/xml/preferences_offline.xml
    const val KEY_APP_THEME_MODE = "app_theme_mode"
    const val KEY_DOWNLOAD_READING_LIST_ARTICLES = "downloadReadingListArticles"
    const val KEY_COLLAPSE_TABLES = "collapseTables"
    const val KEY_OFFLINE_CACHE_SIZE_LIMIT = "offlineCacheSizeLimit"

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
     * Gets the cache size limit in megabytes.
     * Defaults to "100" (100 MB).
     */
    val offlineCacheSizeLimitMB
        get() = PrefsIoUtil.getString(KEY_OFFLINE_CACHE_SIZE_LIMIT, "100")?.toIntOrNull() ?: 100
}
