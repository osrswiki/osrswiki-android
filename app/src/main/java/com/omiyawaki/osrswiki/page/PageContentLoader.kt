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
import java.net.URLEncoder

class PageContentLoader(
    private val context: Context,
    private val pageRepository: PageRepository,
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

    @Suppress("unused")
    private fun constructApiParseUrlFromPageId(pageId: Int): String {
        return "$baseApiUrl?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true&pageid=$pageId"
    }

    fun loadPageByTitle(articleQueryTitle: String, theme: Theme, forceNetwork: Boolean = false) {
        L.d("loadPageByTitle called for: \"$articleQueryTitle\", forceNetwork: $forceNetwork")
        pageViewModel.uiState = PageUiState(isLoading = true, title = articleQueryTitle, pageId = null)
        onStateUpdated()

        coroutineScope.launch {
            if (!forceNetwork) {
                L.d("Attempting to load '$articleQueryTitle' from Reading List Offline Cache.")
                val apiTitle = articleQueryTitle
                val lang = WikiSite.OSRS_WIKI.languageCode

                val readingListPage = withContext(Dispatchers.IO) {
                    readingListPageDao.findPageInAnyList(WikiSite.OSRS_WIKI, lang, Namespace.MAIN, apiTitle)
                }

                if (readingListPage != null && readingListPage.offline && readingListPage.status == ReadingListPage.STATUS_SAVED) {
                    L.i("Page '$apiTitle' (RL ID: ${readingListPage.id}) found in reading list, marked offline and saved. Attempting cache load.")
                    val cacheKeyUrl = constructApiParseUrlFromApiTitle(apiTitle)
                    val parsedData = OfflineCacheUtil.readAndParseOfflinePageContent(
                        context.applicationContext,
                        offlineObjectDao,
                        cacheKeyUrl,
                        lang
                    )

                    if (parsedData?.text != null) {
                        L.i("Successfully loaded '$apiTitle' from Reading List Offline Cache.")
                        pageViewModel.uiState = PageUiState(
                            isLoading = false, error = null,
                            pageId = parsedData.pageid,
                            title = parsedData.displaytitle ?: parsedData.title ?: apiTitle,
                            plainTextTitle = parsedData.title ?: apiTitle,
                            htmlContent = parsedData.text,
                            wikiUrl = WikiSite.OSRS_WIKI.mobileUrl(parsedData.title ?: apiTitle),
                            revisionId = parsedData.revid,
                            lastFetchedTimestamp = readingListPage.mtime,
                            localFilePath = null,
                            isCurrentlyOffline = true
                        )
                        onStateUpdated()
                        return@launch
                    } else {
                        L.w("Failed to load '$apiTitle' from Reading List Offline Cache (content null or parse error). Falling back.")
                    }
                } else {
                    L.d("Page '$apiTitle' not eligible for Reading List Offline Cache load (ReadingListPage: $readingListPage).")
                }
            }

            L.d("Proceeding to load '$articleQueryTitle' via PageRepository (forceNetwork: $forceNetwork).")
            pageRepository.getArticleByTitle(title = articleQueryTitle, theme = theme, forceNetwork = forceNetwork)
                .catch { e ->
                    L.e("Flow collection error for title '$articleQueryTitle'", e)
                    pageViewModel.uiState = PageUiState(
                        isLoading = false, title = articleQueryTitle, pageId = null,
                        error = "Failed to load article: ${e.message}"
                    )
                    onStateUpdated()
                }
                .collectLatest { result ->
                    L.d("Received result from PageRepository for title '$articleQueryTitle': $result")
                    when (result) {
                        is Result.Loading -> {
                            pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
                        }
                        is Result.Success -> {
                            pageViewModel.uiState = result.data
                        }
                        is Result.Error -> {
                            L.e("Error from PageRepository for title '$articleQueryTitle': ${result.message}", result.throwable)
                            pageViewModel.uiState = PageUiState(
                                isLoading = false, title = articleQueryTitle, pageId = null,
                                error = "Error: ${result.message}"
                            )
                        }
                    }
                    onStateUpdated()
                }
        }
    }

    fun loadPageById(pageId: Int, initialDisplayTitle: String?, theme: Theme, forceNetwork: Boolean = false) {
        val displayTitleDuringLoad = initialDisplayTitle ?: context.getString(R.string.label_loading)
        L.d("loadPageById called for pageId: $pageId, initialDisplayTitle (as apiTitle): '$initialDisplayTitle', forceNetwork: $forceNetwork")
        pageViewModel.uiState = PageUiState(isLoading = true, title = displayTitleDuringLoad, pageId = pageId)
        onStateUpdated()

        coroutineScope.launch {
            if (!forceNetwork && initialDisplayTitle != null) {
                val apiTitle = initialDisplayTitle
                val lang = WikiSite.OSRS_WIKI.languageCode
                L.d("Attempting to load page by ID $pageId (apiTitle: '$apiTitle') from Reading List Offline Cache.")

                val readingListPage = withContext(Dispatchers.IO) {
                    readingListPageDao.findPageInAnyList(WikiSite.OSRS_WIKI, lang, Namespace.MAIN, apiTitle)
                }

                if (readingListPage != null && readingListPage.offline && readingListPage.status == ReadingListPage.STATUS_SAVED) {
                    L.i("Page '$apiTitle' (RL ID: ${readingListPage.id}) found in reading list, marked offline and saved. Attempting cache load.")
                    val cacheKeyUrl = constructApiParseUrlFromApiTitle(apiTitle)
                    val parsedData = OfflineCacheUtil.readAndParseOfflinePageContent(
                        context.applicationContext,
                        offlineObjectDao,
                        cacheKeyUrl,
                        lang
                    )

                    if (parsedData?.text != null) {
                        L.i("Successfully loaded page for apiTitle '$apiTitle' (original pageId: $pageId) from Reading List Offline Cache.")
                        pageViewModel.uiState = PageUiState(
                            isLoading = false, error = null,
                            pageId = parsedData.pageid ?: pageId,
                            title = parsedData.displaytitle ?: parsedData.title ?: apiTitle,
                            plainTextTitle = parsedData.title ?: apiTitle,
                            htmlContent = parsedData.text,
                            wikiUrl = WikiSite.OSRS_WIKI.mobileUrl(parsedData.title ?: apiTitle),
                            revisionId = parsedData.revid,
                            lastFetchedTimestamp = readingListPage.mtime,
                            localFilePath = null,
                            isCurrentlyOffline = true
                        )
                        onStateUpdated()
                        return@launch
                    } else {
                        L.w("Failed to load '$apiTitle' (original pageId: $pageId) from Reading List Offline Cache. Falling back.")
                    }
                } else {
                    L.d("Page with apiTitle '$apiTitle' (original pageId: $pageId) not eligible for Reading List Offline Cache load (ReadingListPage: $readingListPage).")
                }
            } else if (initialDisplayTitle == null && !forceNetwork) {
                L.w("Cannot check Reading List Offline Cache for pageId $pageId without apiTitle (initialDisplayTitle). Proceeding to PageRepository.")
            }

            L.d("Proceeding to load pageId $pageId via PageRepository (forceNetwork: $forceNetwork).")
            pageRepository.getArticle(pageId = pageId, theme = theme, forceNetwork = forceNetwork)
                .catch { e ->
                    L.e("Flow collection error for page ID '$pageId'", e)
                    pageViewModel.uiState = PageUiState(
                        isLoading = false, title = displayTitleDuringLoad, pageId = pageId,
                        error = "Failed to load article (ID: $pageId): ${e.message}"
                    )
                    onStateUpdated()
                }
                .collectLatest { result ->
                    L.d("Received result from PageRepository for page ID '$pageId': $result")
                    when (result) {
                        is Result.Loading -> {
                            pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
                        }
                        is Result.Success -> {
                            pageViewModel.uiState = result.data
                        }
                        is Result.Error -> {
                            L.e("Error from PageRepository for page ID '$pageId': ${result.message}", result.throwable)
                            pageViewModel.uiState = PageUiState(
                                isLoading = false, title = displayTitleDuringLoad, pageId = pageId,
                                error = "Error loading page (ID: $pageId): ${result.message}"
                            )
                        }
                    }
                    onStateUpdated()
                }
        }
    }
}
