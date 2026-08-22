package com.omiyawaki.osrswiki.settings

/**
 * Provides easy access to app-wide shared preferences.
 */
object Prefs {
    // String constants for preference keys, to be used by fragments and the data layer.
    // These keys MUST match the keys defined in res/xml/preferences_appearance.xml and res/xml/preferences_offline.xml
    const val KEY_APP_THEME_MODE = AppearancePreferenceKeys.APP_THEME_MODE
    const val KEY_DOWNLOAD_READING_LIST_ARTICLES = "downloadReadingListArticles"
    const val KEY_COLLAPSE_TABLES = AppearancePreferenceKeys.COLLAPSE_TABLES
    const val KEY_WRAP_TABLE_CELLS = AppearancePreferenceKeys.WRAP_TABLE_CELLS
    const val KEY_READER_TEXT_SCALE_PERCENT = AppearancePreferenceKeys.READER_TEXT_SCALE_PERCENT
    const val KEY_SWIPE_RIGHT_BACK = AppearancePreferenceKeys.SWIPE_RIGHT_BACK
    const val KEY_SWIPE_LEFT_CONTENTS = AppearancePreferenceKeys.SWIPE_LEFT_CONTENTS
    const val KEY_FLOOR_NUMBERING = AppearancePreferenceKeys.FLOOR_NUMBERING
    const val KEY_OFFLINE_CACHE_SIZE_LIMIT = "offlineCacheSizeLimit"

    /**
     * Gets whether tables in WebViews should be collapsed by default.
     * Defaults to false (tables stay expanded, matching iOS).
     */
    val isCollapseTablesEnabled
        get() = appearancePreferences.collapseTables

    val wrapTableCells
        get() = appearancePreferences.wrapTableCells

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
        get() = appearancePreferences.themeMode.persistedValue

    val readerPreferences
        get() = appearancePreferences.reader

    val readerTextScale
        get() = readerPreferences.textScale

    val isSwipeRightBackEnabled
        get() = readerPreferences.swipeRightBackEnabled

    val isSwipeLeftContentsEnabled
        get() = readerPreferences.swipeLeftContentsEnabled

    val floorNumberingMode
        get() = appearancePreferences.floorNumberingMode

    @Volatile
    var disableFirstViewPaintPrewarm: Boolean = true

    @Volatile
    var disableArticlePrewarm: Boolean = true


    /**
     * Gets the cache size limit in megabytes.
     * Defaults to "100" (100 MB).
     */
    val offlineCacheSizeLimitMB
        get() = PrefsIoUtil.getString(KEY_OFFLINE_CACHE_SIZE_LIMIT, "100")?.toIntOrNull() ?: 100

    fun migrateLegacyAppearancePreferences() {
        appearancePreferences
    }

    private val appearancePreferences: AppearancePreferences
        get() = AppearancePreferencesCodec.readAndMigrate(PrefsIoUtil.sharedPreferences)
}
