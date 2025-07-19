package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class PageContentLoader(
    private val context: Context,
    private val pageRepository: PageRepository,
    private val pageAssetDownloader: PageAssetDownloader,
    private val pageHtmlBuilder: PageHtmlBuilder,
    private val pageViewModel: PageViewModel,
    private val readingListPageDao: ReadingListPageDao,
    private val offlineObjectDao: OfflineObjectDao,
    private val coroutineScope: CoroutineScope,
    private val onStateUpdated: () -> Unit
) {
    private val baseApiUrl = "https://oldschool.runescape.wiki/api.php"

    private fun constructApiParseUrlFromApiTitle(apiTitle: String): String {
        val encodedApiTitle = URLEncoder.encode(apiTitle, "UTF-8")
        return "$baseApiUrl?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true&page=$encodedApiTitle"
    }

    fun loadPageByTitle(articleQueryTitle: String, theme: Theme, forceNetwork: Boolean = false) {
        // This is the old flow and remains unchanged for now.
    }

    fun loadPageById(pageId: Int, initialDisplayTitle: String?, theme: Theme, forceNetwork: Boolean = false) {
        val displayTitleDuringLoad = initialDisplayTitle ?: context.getString(R.string.label_loading)
        pageViewModel.uiState = PageUiState(isLoading = true, title = displayTitleDuringLoad, pageId = pageId, progressText = "Downloading...")
        onStateUpdated()

        coroutineScope.launch {
            pageAssetDownloader.downloadPriorityAssets(
                pageId = pageId,
                pageUrl = "https://oldschool.runescape.wiki/?curid=$pageId"
            ) { progress ->
                withContext(Dispatchers.Main) {
                    pageViewModel.uiState = pageViewModel.uiState.copy(
                        progress = progress,
                        progressText = "Downloading priority assets..."
                    )
                    onStateUpdated()
                }
            }.onSuccess { downloadResult ->
                val parseResult = downloadResult.parseResult
                // The HTML is now pre-processed. The expensive Jsoup step is gone from this class.
                val bodyContent = downloadResult.processedHtml
                val title = parseResult.displaytitle ?: parseResult.title ?: ""

                val finalHtml = pageHtmlBuilder.buildFullHtmlDocument(title, bodyContent, theme)

                pageViewModel.uiState = pageViewModel.uiState.copy(
                    isLoading = true,
                    error = null,
                    pageId = parseResult.pageid,
                    title = title,
                    plainTextTitle = parseResult.title,
                    htmlContent = finalHtml,
                    wikiUrl = WikiSite.OSRS_WIKI.mobileUrl(parseResult.title ?: ""),
                    revisionId = parseResult.revid,
                    lastFetchedTimestamp = System.currentTimeMillis(),
                    isCurrentlyOffline = false,
                    progress = 50,
                    progressText = "Rendering page..."
                )
                onStateUpdated()

                // Re-enable the background download.
                pageAssetDownloader.downloadBackgroundAssets(coroutineScope, downloadResult.backgroundUrls)
            }.onFailure { exception ->
                pageViewModel.uiState = pageViewModel.uiState.copy(
                    isLoading = false,
                    error = "Failed to load page: ${exception.message}"
                )
                onStateUpdated()
            }
        }
    }

    fun updateRenderProgress(progress: Int) {
        // Only update if we are in the rendering phase (50-99).
        // This prevents overwriting the final 100% state.
        if (pageViewModel.uiState.progress in 50..99) {
            pageViewModel.uiState = pageViewModel.uiState.copy(
                progress = progress,
                progressText = "Rendering page..."
            )
            onStateUpdated()
        }
    }

    fun onPageRendered() {
        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false,
            progress = 100,
            progressText = "Finished"
        )
        onStateUpdated()
    }

    fun onRenderFailed(errorMessage: String) {
        // This function is called when the WebView renderer itself crashes,
        // which is a terminal state for the current page load.
        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false,
            error = errorMessage,
            progress = null, // Clear progress as it's no longer relevant
            progressText = null
        )
        onStateUpdated()
    }
}
