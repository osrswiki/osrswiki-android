package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.offline.util.OfflineCacheUtil
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class PageRepository(
    private val localDataSource: PageLocalDataSource,
    private val remoteDataSource: PageRemoteDataSource,
    private val htmlBuilder: PageHtmlBuilder
) {
    companion object {
        private const val TAG = "PageRepository"
    }

    fun getArticle(pageId: Int, forceNetwork: Boolean): Flow<Result<PageUiState>> = flow {
        emit(Result.Loading)
        if (!forceNetwork) {
            val cachedArticle = localDataSource.getArticleFromCache(pageId)
            if (cachedArticle != null) {
                emit(Result.Success(cachedArticle))
                return@flow
            }
        }
        val networkResult = remoteDataSource.getArticleParseResult(pageId)
        emit(transformParseResult(networkResult))
    }.flowOn(Dispatchers.IO)

    fun getArticleByTitle(title: String, forceNetwork: Boolean): Flow<Result<PageUiState>> = flow {
        emit(Result.Loading)
        if (!forceNetwork) {
            val cachedArticle = localDataSource.getArticleFromCache(title)
            if (cachedArticle != null) {
                emit(Result.Success(cachedArticle))
                return@flow
            }
        }
        val networkResult = remoteDataSource.getArticleParseResult(title)
        emit(transformParseResult(networkResult))
    }.flowOn(Dispatchers.IO)
    
    private fun transformParseResult(networkResult: Result<ParseResult>): Result<PageUiState> {
        return when (networkResult) {
            is Result.Success -> {
                val parseResult = networkResult.data
                val canonicalTitle = parseResult.title!!
                val displayTitle = parseResult.displaytitle ?: canonicalTitle
                val htmlContent = htmlBuilder.buildFullHtmlDocument(parseResult.text!!)
                val uiState = PageUiState(
                    isLoading = false, error = null, imageUrl = null,
                    pageId = parseResult.pageid!!,
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
                val fullHtmlToSave = htmlBuilder.buildFullHtmlDocument(parseResult.text!!)
                localDataSource.saveArticle(
                    pageId = parseResult.pageid!!,
                    canonicalTitle = parseResult.title!!,
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
}
