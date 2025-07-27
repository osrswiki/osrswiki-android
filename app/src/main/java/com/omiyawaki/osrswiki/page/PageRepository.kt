package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.offline.util.OfflineCacheUtil
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class PageRepository(
    private val localDataSource: PageLocalDataSource,
    private val remoteDataSource: PageRemoteDataSource,
    private val htmlBuilder: PageHtmlBuilder,
    private val readingListPageDao: ReadingListPageDao? = null
) {
    companion object {
        private const val TAG = "PageRepository"
        private const val FILE_NAMESPACE_PREFIX = "File:"
    }

    fun getArticle(pageId: Int, theme: Theme, forceNetwork: Boolean): Flow<Result<PageUiState>> = flow {
        emit(Result.Loading)
        if (!forceNetwork) {
            // First check if this page is saved offline in ReadingList
            val savedPage = getSavedPageContent(pageId)
            if (savedPage != null) {
                emit(Result.Success(savedPage))
                return@flow
            }
            
            // Then check regular article cache
            val cachedArticle = localDataSource.getArticleFromCache(pageId)
            if (cachedArticle != null) {
                emit(Result.Success(cachedArticle))
                return@flow
            }
        }
        val networkResult = remoteDataSource.getArticleParseResult(pageId)
        emit(transformParseResult(networkResult, theme))
    }.flowOn(Dispatchers.IO)

    fun getArticleByTitle(title: String, theme: Theme, forceNetwork: Boolean): Flow<Result<PageUiState>> = flow {
        emit(Result.Loading)
        if (!forceNetwork) {
            // First check if this page is saved offline in ReadingList
            val savedPage = getSavedPageContentByTitle(title)
            if (savedPage != null) {
                emit(Result.Success(savedPage))
                return@flow
            }
            
            // Then check regular article cache
            val cachedArticle = localDataSource.getArticleFromCache(title)
            if (cachedArticle != null) {
                emit(Result.Success(cachedArticle))
                return@flow
            }
        }

        if (title.startsWith(FILE_NAMESPACE_PREFIX, ignoreCase = true)) {
            emit(fetchAndBuildFilePage(title, theme))
        } else {
            val networkResult = remoteDataSource.getArticleParseResult(title)
            emit(transformParseResult(networkResult, theme))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchAndBuildFilePage(title: String, theme: Theme): Result<PageUiState> = coroutineScope {
        val imageInfoDeferred = async { remoteDataSource.getImageInfo(title) }
        val parseResultDeferred = async { remoteDataSource.getArticleParseResult(title) }

        val imageInfoResult = imageInfoDeferred.await()
        val parseResultResult = parseResultDeferred.await()

        if (imageInfoResult is Result.Error) {
            return@coroutineScope Result.Error(imageInfoResult.message, imageInfoResult.throwable)
        }
        if (parseResultResult is Result.Error) {
            return@coroutineScope Result.Error(parseResultResult.message, parseResultResult.throwable)
        }

        val imageUrl = (imageInfoResult as Result.Success).data.query?.pages?.firstOrNull()?.imageInfo?.firstOrNull()?.url
        val parseResult = (parseResultResult as Result.Success).data

        if (imageUrl == null) {
            return@coroutineScope Result.Error("Failed to extract image URL for File page: $title")
        }

        val descriptionHtml = parseResult.text ?: ""
        val imageTag = "<img src=\"$imageUrl\" style=\"background-color: #333;\"/>"
        val combinedContent = "$imageTag<br/>$descriptionHtml"

        val canonicalTitle = parseResult.title
        val displayTitle = parseResult.displaytitle ?: canonicalTitle
        val finalHtml = htmlBuilder.buildFullHtmlDocument(displayTitle, combinedContent, theme)

        val uiState = PageUiState(
            isLoading = false,
            error = null,
            imageUrl = imageUrl,
            pageId = parseResult.pageid,
            title = displayTitle,
            plainTextTitle = OfflineCacheUtil.stripHtml(displayTitle) ?: canonicalTitle,
            htmlContent = finalHtml,
            wikiUrl = "https://oldschool.runescape.wiki/w/${canonicalTitle.replace(" ", "_")}",
            revisionId = parseResult.revid,
            lastFetchedTimestamp = System.currentTimeMillis(),
            localFilePath = null,
            isCurrentlyOffline = false
        )
        Result.Success(uiState)
    }

    private fun transformParseResult(networkResult: Result<ParseResult>, theme: Theme): Result<PageUiState> {
        return when (networkResult) {
            is Result.Success -> {
                val parseResult = networkResult.data
                val canonicalTitle = parseResult.title
                val displayTitle = parseResult.displaytitle ?: canonicalTitle
                val htmlContent = htmlBuilder.buildFullHtmlDocument(displayTitle, parseResult.text!!, theme)
                val uiState = PageUiState(
                    isLoading = false, error = null, imageUrl = null,
                    pageId = parseResult.pageid,
                    title = displayTitle,
                    plainTextTitle = OfflineCacheUtil.stripHtml(displayTitle) ?: canonicalTitle,
                    htmlContent = htmlContent,
                    wikiUrl = "https://oldschool.runescape.wiki/w/${canonicalTitle.replace(" ", "_")}",
                    revisionId = parseResult.revid,
                    lastFetchedTimestamp = System.currentTimeMillis(),
                    localFilePath = null,
                    isCurrentlyOffline = false
                )
                Result.Success(uiState)
            }
            is Result.Error -> Result.Error(networkResult.message, networkResult.throwable)
            is Result.Loading -> Result.Loading
        }
    }

    suspend fun saveArticleOffline(pageId: Int, displayTitleFromUi: String?): Result<Unit> {
        Log.d(TAG, "saveArticleOffline called by worker for pageId: $pageId")
        val networkResult = remoteDataSource.getArticleParseResult(pageId)
        return when (networkResult) {
            is Result.Success -> {
                val parseResult = networkResult.data
                val canonicalTitle = parseResult.title
                val displayTitle = parseResult.displaytitle ?: canonicalTitle
                // Saving offline uses the default light theme, as it has no user context.
                val fullHtmlToSave = htmlBuilder.buildFullHtmlDocument(displayTitle, parseResult.text!!, Theme.DEFAULT_LIGHT)
                localDataSource.saveArticle(
                    pageId = parseResult.pageid,
                    canonicalTitle = canonicalTitle,
                    revisionId = parseResult.revid,
                    fullHtmlContent = fullHtmlToSave
                )
                Result.Success(Unit)
            }
            is Result.Error -> {
                Log.e(TAG, "Cannot save article offline, network fetch failed: ${networkResult.message}")
                Result.Error("Network fetch failed: ${networkResult.message}", networkResult.throwable)
            }
            is Result.Loading -> Result.Error("Received unexpected Loading state during save.")
        }
    }

    suspend fun removeArticleOffline(pageId: Int): Result<Unit> {
        return try {
            localDataSource.removeArticle(pageId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing article $pageId offline", e)
            Result.Error("Failed to remove article $pageId", e)
        }
    }

    fun isArticleOffline(pageId: Int): Flow<Boolean> {
        return localDataSource.isArticleOfflineFlow(pageId)
    }

    /**
     * Get article from ArticleMeta cache only (no network, no ReadingList)
     */
    internal suspend fun getArticleFromCache(pageId: Int): PageUiState? {
        return localDataSource.getArticleFromCache(pageId)
    }

    /**
     * Get article from ArticleMeta cache only by title (no network, no ReadingList)
     */
    internal suspend fun getArticleFromCache(title: String): PageUiState? {
        return localDataSource.getArticleFromCache(title)
    }

    /**
     * Check if a page with the given MediaWiki page ID is saved in ReadingList and return its content
     */
    internal suspend fun getSavedPageContent(pageId: Int): PageUiState? {
        if (readingListPageDao == null) return null
        
        return try {
            // Find any saved ReadingListPage that has this MediaWiki page ID
            val savedPages = readingListPageDao.getPagesByStatusAndOffline(ReadingListPage.STATUS_SAVED, true)
            val matchingPage = savedPages.find { it.mediaWikiPageId == pageId }
            
            if (matchingPage != null) {
                // Try to get content from ArticleMeta first (since SavedPageSyncWorker saves there too)
                val cachedArticle = localDataSource.getArticleFromCache(pageId)
                if (cachedArticle != null) {
                    Log.i(TAG, "Found saved page for pageId: $pageId in ReadingList, loading from ArticleMeta cache.")
                    return cachedArticle.copy(isCurrentlyOffline = true)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for saved page with pageId: $pageId", e)
            null
        }
    }

    /**
     * Check if a page with the given title is saved in ReadingList and return its content
     */
    internal suspend fun getSavedPageContentByTitle(title: String): PageUiState? {
        if (readingListPageDao == null) return null
        
        return try {
            // Find ReadingListPage by title
            val savedPage = readingListPageDao.findPageInAnyList(
                wiki = WikiSite.OSRS_WIKI,
                lang = "en",
                ns = Namespace.MAIN,
                apiTitle = title
            )
            
            if (savedPage != null && savedPage.offline && savedPage.status == ReadingListPage.STATUS_SAVED) {
                // If we have the MediaWiki page ID, use it to get content from ArticleMeta
                savedPage.mediaWikiPageId?.let { pageId ->
                    val cachedArticle = localDataSource.getArticleFromCache(pageId)
                    if (cachedArticle != null) {
                        Log.i(TAG, "Found saved page for title: '$title' in ReadingList, loading from ArticleMeta cache.")
                        return cachedArticle.copy(isCurrentlyOffline = true)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for saved page with title: '$title'", e)
            null
        }
    }
}
