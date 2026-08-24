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
     * Live article HTML builds inline critical (+ deferred) first-paint CSS,
     * matching the saved-path `inlineFirstPaintCss = true` behavior.
     * Flip to false for one-commit rollback of Phase B Task 7.
     */
    @Volatile
    var inlineLiveFirstPaintCss: Boolean = true

    /**
     * When true, live/saved HTML uses one minified critical bundle
     * (`styles/critical-article.min.css`) instead of ten separate critical sheets.
     * Default true: production uses the Task 7b bundle on the Task 7 live inline path
     * (`inlineLiveFirstPaintCss`). Flip to false for one-commit rollback to per-file
     * critical sheets. Body reveal stays FirstViewPainted; settled is stopwatch-only.
     */
    @Volatile
    var useCriticalArticleBundle: Boolean = true

    /**
     * When true, live opens extract first-viewport slot image URLs from HTML and
     * fetch them into the article-view session store before WebView document
     * commit. Decode → FirstViewPainted semantics are unchanged; this only
     * requests intersecting/slot URLs sooner. Flip to false to roll back Task 10.
     */
    @Volatile
    var warmFirstViewportImagesEarly: Boolean = true

    /**
     * When true, FirstViewPainted waits only on intersecting media plus the
     * authored-default switcher pane (not the full switcher pool / every srcset
     * candidate), and the early first-view warmer extracts the slot only — not
     * the full document URL list. Flip to false to roll back the post-Task-10
     * painted-set cut. Reveal stays FirstViewPainted.
     */
    @Volatile
    var narrowFirstViewportPaintedSet: Boolean = true

    /**
     * When true, live HTML inlines the critical bundle (+ platform aesthetics)
     * and restores `wiki-integration.css` / `navbox_styles.css` to the existing
     * `media=print` onload deferred-link path. Saved/offline stays fully inlined.
     * Flip to false to roll back thousand-cuts slice 2.
     */
    @Volatile
    var deferLiveWikiFidelityCss: Boolean = true

    /**
     * When true, live Android opens commit HTML / start WebView paint before
     * `PageTableOfContentsExtractor` Jsoup work. The TOC sheet may be empty
     * briefly, then populate. Flip to false to roll back thousand-cuts #4
     * (sequential extract on the html_ready critical path).
     */
    @Volatile
    var deferLiveTableOfContentsExtract: Boolean = true

    /**
     * When true, live HTML keeps `src` off hidden switcher panes and below-fold
     * thumbs until they intersect (or the user switches), and first-view srcset
     * is reduced to one density URL via `sizes` + host choose(). Flip to false
     * to roll back thousand-cuts #7/#14. Reveal stays FirstViewPainted.
     */
    @Volatile
    var lazyOffscreenArticleImages: Boolean = true

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
