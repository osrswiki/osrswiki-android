package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L
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

    fun loadPageByTitle(articleQueryTitle: String, theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageContentLoader: Loading page by title: '$articleQueryTitle', theme: $theme, forceNetwork: $forceNetwork")
        val mobileUrl = WikiSite.OSRS_WIKI.mobileUrl(articleQueryTitle)
        L.d("PageContentLoader: Generated mobile URL: $mobileUrl")
        cancelActivePageWork()
        pageLoadJob = coroutineScope.launch {
            L.d("PageContentLoader: Collecting download progress flow.")
            pageAssetDownloader.downloadPriorityAssetsByTitle(articleQueryTitle, mobileUrl).collect { progress ->
                handleDownloadProgress(progress, theme)
            }
        }
    }


    fun loadPageById(pageId: Int, initialDisplayTitle: String?, theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageContentLoader: Loading page by ID: $pageId, displayTitle: '$initialDisplayTitle', theme: $theme, forceNetwork: $forceNetwork")
        val pageUrl = "https://oldschool.runescape.wiki/?curid=$pageId"
        L.d("PageContentLoader: Generated page URL: $pageUrl")
        cancelActivePageWork()
        pageLoadJob = coroutineScope.launch {
            L.d("PageContentLoader: Collecting download progress flow.")
            pageAssetDownloader.downloadPriorityAssets(pageId, pageUrl).collect { progress ->
                handleDownloadProgress(progress, theme)
            }
        }
    }

    private suspend fun handleDownloadProgress(progress: DownloadProgress, theme: Theme) {
        when (progress) {
            is DownloadProgress.FetchingHtml -> {
                withContext(Dispatchers.Main) {
                    val scaledProgress = 5 + (progress.progress * 0.05).toInt()
                    L.d("handleDownloadProgress: Received FetchingHtml ${progress.progress}%. Setting scaled progress to $scaledProgress%.")
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        progress = scaledProgress,
                        progressText = "Downloading..."
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
                
                // Perform CPU-intensive HTML string building on a background thread.
                val (finalHtml, tableOfContentsSections) = withContext(Dispatchers.Default) {
                    currentCoroutineContext().ensureActive()
                    L.d("handleDownloadProgress: Building final HTML on background thread.")
                    val collapseTablesEnabled = Prefs.isCollapseTablesEnabled
                    L.d("handleDownloadProgress: Using table collapse preference: $collapseTablesEnabled")
                    val displayTitle = result.parseResult.displaytitle ?: result.parseResult.title
                    lateinit var tableOfContentsSections: List<Section>
                    val tocTime = measureTimeMillis {
                        tableOfContentsSections = PageTableOfContentsExtractor.extract(displayTitle, result.processedHtml)
                    }
                    currentCoroutineContext().ensureActive()
                    lateinit var finalHtml: String
                    val documentBuildTime = measureTimeMillis {
                        finalHtml = pageHtmlBuilder.buildFullHtmlDocument(displayTitle ?: "", result.processedHtml, theme, collapseTablesEnabled)
                    }
                    currentCoroutineContext().ensureActive()
                    L.d("ArticleRenderPhase: tocExtractionMs=$tocTime documentBuildMs=$documentBuildTime processedChars=${result.processedHtml.length} finalChars=${finalHtml.length}")
                    finalHtml to tableOfContentsSections
                }
                L.d("handleDownloadProgress: Final HTML length: ${finalHtml.length} characters")
                
                // Switch back to the main thread to update the UI and state.
                withContext(Dispatchers.Main) {
                    L.d("handleDownloadProgress: Received Success. Setting progress to 50%.")
                    val wikiUrl = WikiSite.OSRS_WIKI.mobileUrl(result.parseResult.title ?: "")
                    L.d("handleDownloadProgress: Generated wiki URL: $wikiUrl")
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        isLoading = true, error = null, pageId = result.parseResult.pageid,
                        title = result.parseResult.displaytitle ?: result.parseResult.title,
                        plainTextTitle = result.parseResult.title, htmlContent = finalHtml,
                        wikiUrl = wikiUrl,
                        revisionId = result.parseResult.revid, lastFetchedTimestamp = System.currentTimeMillis(),
                        isCurrentlyOffline = false, progress = 50, progressText = "Rendering page...",
                        tableOfContentsSections = tableOfContentsSections
                    )
                    backgroundAssetsJob?.cancel()
                    backgroundAssetsJob = pageAssetDownloader.downloadBackgroundAssets(coroutineScope, result.backgroundUrls)
                    onStateUpdated()
                }
            }
            is DownloadProgress.Failure -> {
                withContext(Dispatchers.Main) {
                    L.e("handleDownloadProgress: Received Failure - Error type: ${progress.error::class.simpleName}, Message: ${progress.error.message}", progress.error)
                    val errorMessage = when (progress.error) {
                        is java.net.UnknownHostException -> "No internet connection available"
                        is java.net.SocketTimeoutException -> "Request timed out"
                        is java.net.ConnectException -> "Unable to connect to server"
                        else -> "Failed to load page: ${progress.error.message}"
                    }
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
        if (pageViewModel.uiState.progress in 50..99) {
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

    fun cancelActivePageWork() {
        pageLoadJob?.cancel()
        pageLoadJob = null
        backgroundAssetsJob?.cancel()
        backgroundAssetsJob = null
    }
}
