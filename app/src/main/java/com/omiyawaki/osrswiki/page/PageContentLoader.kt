package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.savedpages.osrsArticleViewAssetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class PageContentLoader(
    private val context: Context,
    private val pageRepository: PageRepository,
    private val pageAssetDownloader: PageAssetDownloader,
    private val pageHtmlBuilder: PageHtmlBuilder,
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope,
    private val onStateUpdated: () -> Unit
) {
    private var pageLoadJob: Job? = null
    private var backgroundAssetsJob: Job? = null
    private var firstViewSlotWarmJob: Job? = null
    @Volatile
    private var liveArticleAssetWarmer: osrsLiveArticleAssetWarmer? = null
    @Volatile
    private var firstViewSlotWarmer: osrsFirstViewAssetWarmer? = null
    @Volatile
    private var articleOpenAtElapsed: Long? = null // tap/open clock; not cleared by painted
    @Volatile
    private var firstViewOpenAtElapsed: Long? = null // painted helper; cleared after first_viewport
    @Volatile
    private var pendingFirstViewComplete = false
    @Volatile
    private var firstViewCompletePosted = false
    @Volatile
    private var pendingFirstViewportSettled = false
    @Volatile
    private var firstViewportSettledPosted = false
    @Volatile
    private var loggedTtfb = false

    fun loadPageByTitle(articleQueryTitle: String, theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageContentLoader: Loading page by title: '$articleQueryTitle', theme: $theme, forceNetwork: $forceNetwork")
        val mobileUrl = WikiSite.OSRS_WIKI.mobileUrl(articleQueryTitle)
        L.d("PageContentLoader: Generated mobile URL: $mobileUrl")
        cancelActivePageWork()
        firstViewCompletePosted = false
        firstViewportSettledPosted = false
        loggedTtfb = false
        val openAt = android.os.SystemClock.elapsedRealtime()
        articleOpenAtElapsed = openAt
        firstViewOpenAtElapsed = openAt
        L.d("LOAD-MINMAX open title='$articleQueryTitle'")
        if (pendingFirstViewComplete) {
            pendingFirstViewComplete = false
            markFirstViewComplete()
        }
        if (pendingFirstViewportSettled) {
            pendingFirstViewportSettled = false
            markFirstViewportSettled()
        }
        pageLoadJob = coroutineScope.launch {
            L.d("PageContentLoader: Collecting download progress flow.")
            pageAssetDownloader.downloadPriorityAssetsByTitle(
                articleQueryTitle,
                mobileUrl,
                forceNetwork
            ).collect { progress ->
                handleDownloadProgress(progress, theme)
            }
        }
    }


    fun loadPageById(pageId: Int, initialDisplayTitle: String?, theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageContentLoader: Loading page by ID: $pageId, displayTitle: '$initialDisplayTitle', theme: $theme, forceNetwork: $forceNetwork")
        val pageUrl = "https://oldschool.runescape.wiki/?curid=$pageId"
        L.d("PageContentLoader: Generated page URL: $pageUrl")
        cancelActivePageWork()
        firstViewCompletePosted = false
        firstViewportSettledPosted = false
        loggedTtfb = false
        val openAt = android.os.SystemClock.elapsedRealtime()
        articleOpenAtElapsed = openAt
        firstViewOpenAtElapsed = openAt
        L.d("LOAD-MINMAX open pageId=$pageId")
        if (pendingFirstViewComplete) {
            pendingFirstViewComplete = false
            markFirstViewComplete()
        }
        if (pendingFirstViewportSettled) {
            pendingFirstViewportSettled = false
            markFirstViewportSettled()
        }
        pageLoadJob = coroutineScope.launch {
            L.d("PageContentLoader: Collecting download progress flow.")
            pageAssetDownloader.downloadPriorityAssets(
                pageId = pageId,
                pageUrl = pageUrl,
                forceNetwork = forceNetwork,
                initialTitle = initialDisplayTitle
            ).collect { progress ->
                handleDownloadProgress(progress, theme)
            }
        }
    }

    private suspend fun handleDownloadProgress(progress: DownloadProgress, theme: Theme) {
        when (progress) {
            is DownloadProgress.FetchingHtml -> {
                withContext(Dispatchers.Main) {
                    if (!loggedTtfb) {
                        loggedTtfb = true
                        val started = firstViewOpenAtElapsed
                        val elapsed = if (started != null) {
                            android.os.SystemClock.elapsedRealtime() - started
                        } else {
                            -1L
                        }
                        L.d("LOAD-MINMAX ttfb elapsedMs=$elapsed")
                    }
                    if (pageViewModel.uiState.progressText == null) {
                        return@withContext
                    }
                    val scaledProgress = 5 + (progress.progress * 0.05).toInt()
                    L.d("handleDownloadProgress: Received FetchingHtml ${progress.progress}%. Setting scaled progress to $scaledProgress%.")
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        progress = scaledProgress,
                        progressText = "Opening page..."
                    )
                    onStateUpdated()
                }
            }
            is DownloadProgress.FetchingAssets -> {
                withContext(Dispatchers.Main) {
                    val scaledProgress = 10 + (progress.progress * 0.40).toInt()
                    L.d("handleDownloadProgress: Received FetchingAssets ${progress.progress}%. Setting scaled progress to $scaledProgress%.")
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        progress = scaledProgress,
                        progressText = "Downloading assets..."
                    )
                    onStateUpdated()
                }
            }
            is DownloadProgress.Success -> {
                val result = progress.result
                L.d("handleDownloadProgress: Received Success - PageID: ${result.parseResult.pageid}, Title: '${result.parseResult.title}', DisplayTitle: '${result.parseResult.displaytitle}'")
                L.d("handleDownloadProgress: HTML content length: ${result.processedHtml.length} characters")
                val wikiUrlForWarm = WikiSite.OSRS_WIKI.mobileUrl(result.parseResult.title ?: "")
                startFirstViewSlotWarm(result.processedHtml, wikiUrlForWarm)
                val paintSnapshot = result.readyToPaintHtml
                val (finalHtml, tableOfContentsSections) = withContext(Dispatchers.Default) {
                    currentCoroutineContext().ensureActive()
                    val collapseTablesEnabled = Prefs.isCollapseTablesEnabled
                    val displayTitle = result.parseResult.displaytitle ?: result.parseResult.title
                    val tocHtml = if (paintSnapshot != null) {
                        osrsSavedPaintHtml.extractBodyForToc(paintSnapshot)
                    } else {
                        result.processedHtml
                    }
                    lateinit var tableOfContentsSections: List<Section>
                    val tocTime = measureTimeMillis {
                        tableOfContentsSections = PageTableOfContentsExtractor.extract(
                            displayTitle,
                            tocHtml,
                            osrsArticleFloorConvention.resolved()
                        )
                    }
                    currentCoroutineContext().ensureActive()
                    lateinit var finalHtml: String
                    val documentBuildTime = measureTimeMillis {
                        finalHtml = if (paintSnapshot != null) {
                            val isCalculatorPage = tocHtml.contains("jcConfig") ||
                                osrsWikiWebViewUrl.isCalculatorNamespaceTitle(result.parseResult.title)
                            osrsSavedPaintHtml.applyingLivePreferences(
                                osrsSavedPaintHtml.inlineLinkedFirstPaintCss(paintSnapshot) { path ->
                                    pageHtmlBuilder.loadAssetText(path)
                                },
                                isDark = theme.isDark(),
                                wrapEnabled = Prefs.wrapTableCells,
                                scaleCssValue = PageHtmlBuilder.readerTextScaleCssValue(Prefs.readerTextScale),
                                bottomChromePx = if (isCalculatorPage) 96 else 0
                            )
                        } else {
                            pageHtmlBuilder.buildFullHtmlDocument(
                                displayTitle ?: "",
                                result.processedHtml,
                                theme,
                                collapseTablesEnabled,
                                canonicalTitle = result.parseResult.title,
                                inlineFirstPaintCss = Prefs.inlineLiveFirstPaintCss
                            )
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    L.d(
                        "ArticleRenderPhase: tocExtractionMs=$tocTime documentBuildMs=$documentBuildTime " +
                            "processedChars=${result.processedHtml.length} finalChars=${finalHtml.length} " +
                            "paintSnapshot=${paintSnapshot != null}"
                    )
                    val openStarted = firstViewOpenAtElapsed
                    val openElapsed = if (openStarted != null) {
                        android.os.SystemClock.elapsedRealtime() - openStarted
                    } else {
                        -1L
                    }
                    if (!loggedTtfb) {
                        loggedTtfb = true
                        L.d("LOAD-MINMAX ttfb elapsedMs=$openElapsed")
                    }
                    L.d(
                        "LOAD-MINMAX html_ready elapsedMs=$openElapsed documentBuildMs=$documentBuildTime " +
                            "htmlChars=${finalHtml.length} paintSnapshot=${paintSnapshot != null}"
                    )
                    finalHtml to tableOfContentsSections
                }
                L.d("handleDownloadProgress: Final HTML length: ${finalHtml.length} characters")

                withContext(Dispatchers.Main) {
                    val wikiUrl = WikiSite.OSRS_WIKI.mobileUrl(result.parseResult.title ?: "")
                    val savedPaintOpen = paintSnapshot != null
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        isLoading = true, error = null, pageId = result.parseResult.pageid,
                        title = result.parseResult.displaytitle ?: result.parseResult.title,
                        plainTextTitle = result.parseResult.title, htmlContent = finalHtml,
                        wikiUrl = wikiUrl,
                        revisionId = result.parseResult.revid, lastFetchedTimestamp = System.currentTimeMillis(),
                        isCurrentlyOffline = result.source == ArticleContentSource.SAVED,
                        progress = if (savedPaintOpen) 90 else 50,
                        progressText = if (savedPaintOpen) null else "Rendering page...",
                        tableOfContentsSections = tableOfContentsSections
                    )
                    backgroundAssetsJob?.cancel()
                    backgroundAssetsJob = null
                    liveArticleAssetWarmer = null
                    // WebView owns visible media after the document commit.
                    onStateUpdated()
                }
            }
            is DownloadProgress.Failure -> {
                withContext(Dispatchers.Main) {
                    L.e("handleDownloadProgress: Received Failure - Error type: ${progress.error::class.simpleName}, Message: ${progress.error.message}", progress.error)
                    val errorMessage = com.omiyawaki.osrswiki.util.UserFacingError.message(
                        progress.error,
                        fallback = "This page could not be loaded. Please try again."
                    )
                    L.w("handleDownloadProgress: Setting error message: $errorMessage")
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        isLoading = false, error = errorMessage, tableOfContentsSections = emptyList()
                    )
                    onStateUpdated()
                }
            }
        }
    }

    fun updateRenderProgress(progress: Int) {
        if (pageViewModel.uiState.progress in 50..99 && pageViewModel.uiState.progressText == "Rendering page...") {
            val newProgress = 50 + (progress * 0.5).toInt()
            L.d("updateRenderProgress: WebView progress: $progress%. Setting new progress to $newProgress%.")
            pageViewModel.uiState = pageViewModel.uiState.copy(
                progress = newProgress,
                progressText = "Rendering page..."
            )
            onStateUpdated()
        }
    }

    fun onPageRendered() {
        L.d("onPageRendered: Page finished rendering. Setting progress to 100%.")
        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false, progress = 100, progressText = "Finished"
        )
        onStateUpdated()
    }

    fun onRenderFailed(errorMessage: String) {
        L.e("onRenderFailed: $errorMessage")
        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false, error = errorMessage, progress = null, progressText = null
        )
        onStateUpdated()
    }

    fun startLiveArticleAssetWarm(html: String, wikiUrl: String) {
        backgroundAssetsJob?.cancel()
        if (html.isBlank()) {
            liveArticleAssetWarmer = null
            backgroundAssetsJob = null
            return
        }
        val warmer = osrsLiveArticleAssetWarmer()
        liveArticleAssetWarmer = warmer
        backgroundAssetsJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                warmer.warm(html, wikiUrl.ifBlank { "https://oldschool.runescape.wiki/" })
            } finally {
                if (liveArticleAssetWarmer === warmer) {
                    liveArticleAssetWarmer = null
                }
            }
        }
    }

    fun startFirstViewSlotWarm(html: String, wikiUrl: String) {
        if (!Prefs.warmFirstViewportImagesEarly) {
            return
        }
        firstViewSlotWarmJob?.cancel()
        if (html.isBlank()) {
            firstViewSlotWarmer = null
            firstViewSlotWarmJob = null
            return
        }
        val warmer = osrsFirstViewAssetWarmer()
        firstViewSlotWarmer = warmer
        val started = firstViewOpenAtElapsed
        val openElapsed = if (started != null) {
            android.os.SystemClock.elapsedRealtime() - started
        } else {
            -1L
        }
        L.d("LOAD-MINMAX first_view_slot_warm_start elapsedMs=$openElapsed htmlChars=${html.length}")
        firstViewSlotWarmJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                warmer.warm(html, wikiUrl.ifBlank { "https://oldschool.runescape.wiki/" })
            } finally {
                if (firstViewSlotWarmer === warmer) {
                    firstViewSlotWarmer = null
                }
            }
        }
    }

    fun promoteLiveArticleAssets(urls: List<String>) {
        val base = pageViewModel.uiState.wikiUrl?.ifBlank { null }
            ?: "https://oldschool.runescape.wiki/"
        val resolved = urls.mapNotNull { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                return@mapNotNull null
            }
            val absolute = runCatching {
                when {
                    trimmed.startsWith("//") -> "https:$trimmed"
                    trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                    else -> java.net.URI(base).resolve(trimmed).toString()
                }
            }.getOrNull() ?: return@mapNotNull null
            osrsArticleViewAssetStore.canonicalize(absolute)
        }
        firstViewSlotWarmer?.promote(resolved)
        liveArticleAssetWarmer?.promote(resolved)
    }

    fun markFirstViewComplete() {
        if (firstViewCompletePosted) {
            return
        }
        val started = firstViewOpenAtElapsed
        if (started == null) {
            pendingFirstViewComplete = true
            return
        }
        firstViewCompletePosted = true
        pendingFirstViewComplete = false
        firstViewOpenAtElapsed = null
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        L.d("osrsFirstViewComplete elapsedMs=$elapsed")
        L.d("LOAD-MINMAX first_viewport elapsedMs=$elapsed")
    }

    fun markFirstViewportSettled() {
        if (firstViewportSettledPosted) {
            return
        }
        val started = articleOpenAtElapsed
        if (started == null) {
            pendingFirstViewportSettled = true
            return
        }
        firstViewportSettledPosted = true
        pendingFirstViewportSettled = false
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        L.d("osrsFirstViewportSettled elapsedMs=$elapsed")
        L.d("LOAD-MINMAX first_viewport_settled elapsedMs=$elapsed")
    }

    fun cancelActivePageWork() {
        pageLoadJob?.cancel()
        pageLoadJob = null
        backgroundAssetsJob?.cancel()
        backgroundAssetsJob = null
        liveArticleAssetWarmer = null
        firstViewSlotWarmJob?.cancel()
        firstViewSlotWarmJob = null
        firstViewSlotWarmer = null
    }
}
