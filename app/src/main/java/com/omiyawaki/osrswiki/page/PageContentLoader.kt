package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.offline.util.OfflineCacheUtil
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
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

    private fun preprocessHtmlContent(rawHtml: String?): String? {
        if (rawHtml == null) {
            return null
        }
        // Experiment: Use the faster XML parser instead of the default HTML parser.
        // This assumes the input from the MediaWiki API is well-formed.
        val document = Jsoup.parse(rawHtml, "", Parser.xmlParser())
        val siteUrl = WikiSite.OSRS_WIKI.url()
        val urlAttributes = listOf("src", "href", "srcset")
        urlAttributes.forEach { attr ->
            document.select("[$attr]").forEach { element ->
                val originalUrl = element.attr(attr)
                if (originalUrl.startsWith("/") && !originalUrl.startsWith("//")) {
                    element.attr(attr, siteUrl + originalUrl)
                }
            }
        }
        document.outputSettings().prettyPrint(false)
        val resources = document.select("[class*=\"infobox-resources-\"]")
        resources.remove()
        val selectorsToRemove = listOf(
            "tr.advanced-data",
            "tr.leagues-global-flag",
            "tr.infobox-padding"
        )
        document.select(selectorsToRemove.joinToString(", ")).remove()
        resources.forEach { document.body().appendChild(it) }
        return document.outerHtml()
    }

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
                val bodyContent = preprocessHtmlContent(parseResult.text) ?: ""
                val title = parseResult.displaytitle ?: parseResult.title ?: ""

                // Use the PageHtmlBuilder to construct the final, styled document.
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
}
