package com.omiyawaki.osrswiki.data

import android.text.Html
import android.util.Log
import com.omiyawaki.osrswiki.data.db.dao.ArticleDao
import com.omiyawaki.osrswiki.data.db.dao.ArticleFtsDao
import com.omiyawaki.osrswiki.data.db.entity.ArticleEntity
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

// Result wrapper for data fetching operations
sealed class Result<out T> {
    data object Loading : Result<Nothing>()
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

// Data class to represent combined article data for UI purposes
data class ArticleFullData(
    val pageId: Int,
    val title: String,
    val htmlContent: String,
    val mainImageUrl: String?, // URL from network or cache
    val localImageFileName: String?, // Filename if image is stored locally
    val revisionId: Long,
    val isCompleteOffline: Boolean, // Indicates if the article is fully available offline
    val lastUpdatedLocal: Long
)

class ArticleRepository(
    private val apiService: WikiApiService,
    private val articleDao: ArticleDao,
    private val articleFtsDao: ArticleFtsDao
) {

    private companion object {
        private const val TAG = "ArticleRepository"
        private const val DEFAULT_THUMB_SIZE = 600
    }

    fun getArticle(pageId: Int, forceNetwork: Boolean = false): Flow<Result<ArticleFullData>> = flow {
        emit(Result.Loading)

        var localDataEmitted = false
        if (!forceNetwork) {
            val localArticleEntity = articleDao.getArticleById(pageId).firstOrNull()
            if (localArticleEntity != null) {
                Log.d(TAG, "Article $pageId found in local DB.")
                emit(Result.Success(mapEntityToFullData(localArticleEntity)))
                localDataEmitted = true
                // If complete locally and not forcing network, consider returning early
                if (localArticleEntity.isComplete) {
                     Log.d(TAG, "Article $pageId is complete locally and not forcing network. Skipping network fetch.")
                     // Ensure FTS is populated if it wasn't before (e.g. old data)
                     // This might be a good place for a one-off FTS check/population if entity.summaryText is indicative
                     if (localArticleEntity.summaryText.isNullOrEmpty()) { // Heuristic for potentially missing FTS
                        val plainTextContentForFts = convertHtmlToPlainText(localArticleEntity.htmlContent)
                        articleFtsDao.insertOrReplace(
                            pageId = localArticleEntity.pageId,
                            title = localArticleEntity.title,
                            bodyText = plainTextContentForFts
                        )
                        Log.d(TAG, "Ensured FTS table updated for locally complete article $pageId.")
                     }
                     return@flow
                }
            }
        }

        val localArticleForNetworkCheck = articleDao.getArticleById(pageId).firstOrNull() // Re-check or use earlier if available
        // Determine if network fetch is needed:
        // 1. forceNetwork is true
        // 2. No local article found
        // 3. Local article found but is not marked as complete
        val shouldFetchFromNetwork = forceNetwork || localArticleForNetworkCheck == null || !localArticleForNetworkCheck.isComplete

        if (!shouldFetchFromNetwork && localDataEmitted) {
             // This case implies local data was emitted, it's complete, and we are not forcing network.
             // The early return above should have handled this. This is a safeguard.
            Log.d(TAG, "Article $pageId is complete locally, not forcing network, and already emitted. Redundant skip.")
            return@flow
        }

        try {
            Log.d(TAG, "Fetching article $pageId from network. Force network: $forceNetwork, Should fetch: $shouldFetchFromNetwork")
            val textApiResponse = apiService.getArticleContent(pageId) // Fetches content by ID
            val parseData = textApiResponse.parse

            if (parseData?.text == null || parseData.title == null || parseData.pageid == null || parseData.revid == null) {
                Log.w(TAG, "Network fetch for article $pageId: Essential parse data is null. Response: $textApiResponse")
                if (!localDataEmitted) { // Only emit error if no local data was good enough
                    emit(Result.Error("Failed to fetch article details from network for ID $pageId."))
                }
                return@flow
            }

            val imageUrlResponse = apiService.getArticleImageUrlById(pageId = parseData.pageid, pithumbsize = DEFAULT_THUMB_SIZE)
            val mainImageUrlFromApi = imageUrlResponse.query?.pages?.firstOrNull()?.thumbnail?.source
            if (mainImageUrlFromApi == null) {
                Log.w(TAG, "Network fetch for article $pageId: Image URL is null. Response: $imageUrlResponse")
            }

            val entityToSave = ArticleEntity(
                pageId = parseData.pageid,
                title = parseData.title,
                htmlContent = parseData.text,
                mainImageUrl = mainImageUrlFromApi,
                localImageFileName = localArticleForNetworkCheck?.localImageFileName, // Preserve if exists
                revisionId = parseData.revid,
                lastUpdatedLocal = System.currentTimeMillis(),
                isComplete = false, // Mark as false initially; set true once all parts (e.g., images) are downloaded
            summaryText = extractSummary(parseData.text)
            )

            articleDao.insertArticle(entityToSave)
            Log.d(TAG, "Article ${entityToSave.pageId} fetched from network and saved/updated in DB.")

            val plainTextContentForFts = convertHtmlToPlainText(entityToSave.htmlContent)
            articleFtsDao.insertOrReplace(
                pageId = entityToSave.pageId,
                title = entityToSave.title,
                bodyText = plainTextContentForFts
            )
            Log.d(TAG, "Article ${entityToSave.pageId} content updated in FTS table.")

            emit(Result.Success(mapEntityToFullData(entityToSave)))

        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching article by ID $pageId: ${e.message}", e)
            if (!localDataEmitted) {
                emit(Result.Error("Network error for ID $pageId: ${e.message}", e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching/processing article by ID $pageId: ${e.message}", e)
            if (!localDataEmitted) {
                emit(Result.Error("Failed to process article data for ID $pageId: ${e.message}", e))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun getArticleByTitle(articleTitle: String, forceNetwork: Boolean = true): Flow<Result<ArticleFullData>> = flow {
        emit(Result.Loading)
        Log.d(TAG, "getArticleByTitle called for title: '$articleTitle', forceNetwork: $forceNetwork")

        var localDataEmitted = false
        if (!forceNetwork) {
            val localArticleEntity = articleDao.getArticleByTitle(articleTitle).firstOrNull()
            if (localArticleEntity != null) {
                Log.d(TAG, "Article with title '$articleTitle' (ID: ${localArticleEntity.pageId}) found in local DB.")
                emit(Result.Success(mapEntityToFullData(localArticleEntity)))
                localDataEmitted = true
                if (localArticleEntity.isComplete) {
                    Log.d(TAG, "Article '$articleTitle' is complete locally and not forcing network. Skipping network fetch.")
                     if (localArticleEntity.summaryText.isNullOrEmpty()) { // Heuristic for potentially missing FTS
                        val plainTextContentForFts = convertHtmlToPlainText(localArticleEntity.htmlContent)
                        articleFtsDao.insertOrReplace(
                            pageId = localArticleEntity.pageId,
                            title = localArticleEntity.title,
                            bodyText = plainTextContentForFts
                        )
                        Log.d(TAG, "Ensured FTS table updated for locally complete article '$articleTitle'.")
                     }
                    return@flow
                }
            }
        }

        val localArticleForNetworkCheck = articleDao.getArticleByTitle(articleTitle).firstOrNull()
        val shouldFetchFromNetwork = forceNetwork || localArticleForNetworkCheck == null || !localArticleForNetworkCheck.isComplete

        if (!shouldFetchFromNetwork && localDataEmitted) {
            Log.d(TAG, "Article '$articleTitle' is complete locally, not forcing network, and already emitted. Skipping.")
            return@flow
        }

        try {
            Log.d(TAG, "Fetching article by title '$articleTitle' from network. Force network: $forceNetwork, Should fetch: $shouldFetchFromNetwork")
            // Fetch text content (includes pageid, title, revid)
            val textContentApiResponse = apiService.getArticleTextContentByTitle(articleTitle)
            val parseResult = textContentApiResponse.parse

            if (parseResult?.text == null || parseResult.title == null || parseResult.pageid == null || parseResult.revid == null) {
                Log.w(TAG, "Network fetch for article title '$articleTitle': Essential parse data is null. Response: $textContentApiResponse")
                if (!localDataEmitted) {
                    emit(Result.Error("Failed to fetch article details from network for title '$articleTitle'."))
                }
                return@flow
            }

            // Use the pageId obtained from the text content response to fetch the image URL
            // This is generally more reliable than fetching image by title if titles can be ambiguous
            val pageIdFromTitleFetch = parseResult.pageid
            val imageUrlResponse = apiService.getArticleImageUrlById(pageId = pageIdFromTitleFetch, pithumbsize = DEFAULT_THUMB_SIZE)
            // Alternative: apiService.getArticleImageUrlByTitle(articleTitle) - but using ID is better if available.
            // val imageUrlResponse = apiService.getArticleImageUrlByTitle(title = parseResult.title) // Use API title for consistency
            val mainImageUrlFromApi = imageUrlResponse.query?.pages?.firstOrNull()?.thumbnail?.source

            if (mainImageUrlFromApi == null) {
                Log.w(TAG, "Network fetch for article title '$articleTitle' (ID: $pageIdFromTitleFetch): Image URL is null. Response: $imageUrlResponse")
            }

            // Check if an entity for this pageId (obtained from title) already exists, to preserve localImageFileName
            val existingEntityForId = articleDao.getArticleById(pageIdFromTitleFetch).firstOrNull()

            val entityToSave = ArticleEntity(
                pageId = pageIdFromTitleFetch,
                title = parseResult.title, // Use title from API response for consistency
                htmlContent = parseResult.text,
                mainImageUrl = mainImageUrlFromApi,
                localImageFileName = existingEntityForId?.localImageFileName, // Preserve if exists
                revisionId = parseResult.revid,
                lastUpdatedLocal = System.currentTimeMillis(),
                isComplete = false, // To be set true once images etc. are downloaded
            summaryText = extractSummary(parseResult.text)
            )

            articleDao.insertArticle(entityToSave)
            Log.d(TAG, "Article '${entityToSave.title}' (ID: ${entityToSave.pageId}) fetched by title, saved/updated in DB.")

            val plainTextContentForFts = convertHtmlToPlainText(entityToSave.htmlContent)
            articleFtsDao.insertOrReplace(
                pageId = entityToSave.pageId,
                title = entityToSave.title,
                bodyText = plainTextContentForFts
            )
            Log.d(TAG, "Article '${entityToSave.title}' content updated in FTS table.")

            emit(Result.Success(mapEntityToFullData(entityToSave)))

        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching article by title '$articleTitle': ${e.message}", e)
            if (!localDataEmitted) {
                emit(Result.Error("Network error for title '$articleTitle': ${e.message}", e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching/processing article by title '$articleTitle': ${e.message}", e)
            if (!localDataEmitted) {
                emit(Result.Error("Failed to process article data for title '$articleTitle': ${e.message}", e))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapEntityToFullData(entity: ArticleEntity): ArticleFullData {
        return ArticleFullData(
            pageId = entity.pageId,
            title = entity.title,
            htmlContent = entity.htmlContent,
            mainImageUrl = entity.mainImageUrl,
            localImageFileName = entity.localImageFileName,
            revisionId = entity.revisionId,
            isCompleteOffline = entity.isComplete, // Map from ArticleEntity.isComplete
            lastUpdatedLocal = entity.lastUpdatedLocal
        )
    }

    private fun extractSummary(htmlContent: String?, maxLength: Int = 200): String? {
        return htmlContent?.let {
            val text = convertHtmlToPlainText(it)
            text.take(maxLength).replace("\n", " ").trim() + if (text.length > maxLength) "..." else ""
        }
    }

    private fun convertHtmlToPlainText(html: String?): String {
        if (html == null) return ""
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    }
}
