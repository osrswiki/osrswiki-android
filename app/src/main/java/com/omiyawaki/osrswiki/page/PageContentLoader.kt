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
    @Volatile
    private var liveArticleAssetWarmer: osrsLiveArticleAssetWarmer? = null
    @Volatile
    private var firstViewOpenAtElapsed: Long? = null

    fun loadPageByTitle(articleQueryTitle: String, theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageContentLoader: Loading page by title: '$articleQueryTitle', theme: $theme, forceNetwork: $forceNetwork")
        val mobileUrl = WikiSite.OSRS_WIKI.mobileUrl(articleQueryTitle)
        L.d("PageContentLoader: Generated mobile URL: $mobileUrl")
        cancelActivePageWork()
        firstViewOpenAtElapsed = android.os.SystemClock.elapsedRealtime()
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
        firstViewOpenAtElapsed = android.os.SystemClock.elapsedRealtime()
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
                                canonicalTitle = result.parseResult.title
                            )
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    L.d(
                        "ArticleRenderPhase: tocExtractionMs=$tocTime documentBuildMs=$documentBuildTime " +
                            "processedChars=${result.processedHtml.length} finalChars=${finalHtml.length} " +
                            "paintSnapshot=${paintSnapshot != null}"
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
        liveArticleAssetWarmer?.promote(resolved)
    }

    fun markFirstViewComplete() {
        val started = firstViewOpenAtElapsed ?: return
        firstViewOpenAtElapsed = null
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        L.d("osrsFirstViewComplete elapsedMs=$elapsed")
    }

    fun cancelActivePageWork() {
        pageLoadJob?.cancel()
        pageLoadJob = null
        backgroundAssetsJob?.cancel()
        backgroundAssetsJob = null
        liveArticleAssetWarmer = null
    }
}
