package com.omiyawaki.osrswiki.page

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.database.ArticleMetaDao
// import com.omiyawaki.osrswiki.database.ArticleMetaEntity // Not directly used in this modified version from user output, but kept for context
import com.omiyawaki.osrswiki.network.WikiApiService
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import com.omiyawaki.osrswiki.offline.util.OfflineCacheUtil // Corrected import path

class PageRepository (
    private val mediaWikiApiService: WikiApiService,
    private val articleMetaDao: ArticleMetaDao,
    private val applicationContext: Context
) {

@Suppress("unused")
companion object {
        private const val ARTICLES_DIR_NAME = "osrs_wiki_articles"
        private const val HTML_EXTENSION = ".html"
        private const val TAG = "PageRepository"
    }

    // [PREVIOUSLY COMMENTED OUT downloadAndSaveArticle FUNCTION - REMAINS COMMENTED]

    fun getArticle(pageId: Int, forceNetwork: Boolean = false): kotlinx.coroutines.flow.Flow<com.omiyawaki.osrswiki.util.Result<PageUiState>> = kotlinx.coroutines.flow.flow {
        emit(com.omiyawaki.osrswiki.util.Result.Loading)
        Log.d(TAG, "getArticle called for pageId: $pageId, forceNetwork: $forceNetwork")

        if (!forceNetwork) {
            Log.d(TAG, "Attempting to load pageId: $pageId from local cache.")
            try {
                val localMeta = articleMetaDao.getMetaByPageId(pageId)
                if (localMeta != null && localMeta.localFilePath.isNotEmpty()) {
                    val localFile = File(localMeta.localFilePath)
                    if (localFile.exists()) {
                        Log.i(TAG, "Found pageId: $pageId ('${localMeta.title}') in local cache at ${localFile.absolutePath}")
                        val htmlContent = localFile.readText()
                        // Assuming localMeta.title is already plain text (canonical)
                        val uiState = PageUiState(
                            isLoading = false,
                            error = null,
                            imageUrl = null,
                            pageId = localMeta.pageId,
                            title = localMeta.title, // Use canonical title for display if HTML version isn't stored
                            plainTextTitle = localMeta.title, // Canonical title is plain text
                            htmlContent = htmlContent,
                            wikiUrl = localMeta.wikiUrl,
                            revisionId = localMeta.revisionId,
                            lastFetchedTimestamp = localMeta.lastFetchedTimestamp,
                            localFilePath = localMeta.localFilePath,
                            isCurrentlyOffline = true
                        )
                        emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))
                        return@flow
                    } else {
                        Log.w(TAG, "Local cache metadata found for pageId: $pageId ('${localMeta.title}') but file missing: ${localMeta.localFilePath}")
                    }
                } else {
                    Log.d(TAG, "pageId: $pageId not found in local cache metadata or no local file path.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading pageId: $pageId from local cache: ${e.message}", e)
            }
        }

        try {
            val articleParseApiResponse = mediaWikiApiService.getArticleParseDataByPageId(pageId)
            val parseResult = articleParseApiResponse.parse

            if (parseResult == null || parseResult.title.isNullOrEmpty() || parseResult.text.isNullOrEmpty()) {
                val errorMsg = "Failed to fetch article details from network for pageId: $pageId (parse result, title, or text missing). API Response: $articleParseApiResponse"
                Log.e(TAG, errorMsg)
                emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg))
                return@flow
            }

            val fetchedCanonicalTitle = parseResult.title // This is usually plain text from MediaWiki 'title' field
            val fetchedDisplayTitle = parseResult.displaytitle ?: fetchedCanonicalTitle // This can contain HTML
            Log.i(TAG, "Network fetch for pageId $pageId yielded canonical title: '$fetchedCanonicalTitle', display title: '$fetchedDisplayTitle'")

            val articleUrl = "https://oldschool.runescape.wiki/w/${fetchedCanonicalTitle.replace(" ", "_")}"
            val htmlContentFromParse = parseResult.text

            // Use OfflineCacheUtil to get a plain text version of the display title
            val plainTextDisplayTitle = OfflineCacheUtil.stripHtml(fetchedDisplayTitle) ?: fetchedCanonicalTitle

            val uiState = PageUiState(
                isLoading = false,
                error = null,
                imageUrl = null,
                pageId = parseResult.pageid ?: pageId,
                title = fetchedDisplayTitle, // Use display title (with HTML) from API for UI
                plainTextTitle = plainTextDisplayTitle, // Use stripped version for logic/API params
                htmlContent = htmlContentFromParse,
                wikiUrl = articleUrl,
                revisionId = parseResult.revid,
                lastFetchedTimestamp = System.currentTimeMillis(),
                localFilePath = null,
                isCurrentlyOffline = false
            )
            Log.i(TAG, "Successfully fetched pageId: $pageId (plain title: '${uiState.plainTextTitle}') from network for online viewing (no save).")
            emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))
        } catch (e: HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "Error body unreadable" } ?: "Unknown API error"
            val errorMsg = "API request failed for pageId $pageId: ${e.code()} - $errorBody"
            Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: IOException) {
            val errorMsg = "Network I/O error while fetching details for pageId $pageId: ${e.message}"
            Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error while fetching article by pageId $pageId: ${e.message}"
            Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        }
    }.flowOn(Dispatchers.IO)

    fun getArticleByTitle(title: String, forceNetwork: Boolean = false): kotlinx.coroutines.flow.Flow<com.omiyawaki.osrswiki.util.Result<PageUiState>> = kotlinx.coroutines.flow.flow {
        emit(com.omiyawaki.osrswiki.util.Result.Loading)
        Log.d(TAG, "getArticleByTitle called for: \"$title\", forceNetwork: $forceNetwork")
        // title parameter here is assumed to be a plain text, canonical title for API lookup

        if (!forceNetwork) {
            Log.d(TAG, "Attempting to load \"$title\" (assumed canonical) from local cache.")
            try {
                val localMeta = articleMetaDao.getMetaByExactTitle(title) // Uses plain text title for lookup
                if (localMeta != null && localMeta.localFilePath.isNotEmpty()) {
                    val localFile = File(localMeta.localFilePath)
                    if (localFile.exists()) {
                        Log.i(TAG, "Found \"$title\" in local cache at ${localFile.absolutePath}")
                        val htmlContent = localFile.readText()
                        // Assuming localMeta.title is already plain text (canonical)
                        val uiState = PageUiState(
                            isLoading = false,
                            error = null,
                            imageUrl = null,
                            pageId = localMeta.pageId,
                            title = localMeta.title, // Use canonical title for display if HTML version isn't stored
                            plainTextTitle = localMeta.title, // Canonical title is plain text
                            htmlContent = htmlContent,
                            wikiUrl = localMeta.wikiUrl,
                            revisionId = localMeta.revisionId,
                            lastFetchedTimestamp = localMeta.lastFetchedTimestamp,
                            localFilePath = localMeta.localFilePath,
                            isCurrentlyOffline = true
                        )
                        emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))
                        return@flow
                    } else {
                        Log.w(TAG, "Local cache metadata found for \"$title\" but file missing: ${localMeta.localFilePath}")
                    }
                } else {
                    Log.d(TAG, "\"$title\" not found in local cache metadata or no local file path.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading \"$title\" from local cache: ${e.message}", e)
            }
        }

        Log.d(TAG, "Attempting to fetch \"$title\" (assumed canonical) from network.")
        try {
            // When fetching by title, the 'title' param to API should be canonical (plain text)
            val articleParseApiResponseByTitle = mediaWikiApiService.getArticleTextContentByTitle(title = title)
            val parseResultByTitle = articleParseApiResponseByTitle.parse

            if (parseResultByTitle == null || parseResultByTitle.text.isNullOrEmpty() || parseResultByTitle.pageid == null || parseResultByTitle.title.isNullOrEmpty()) {
                val errorMsg = "Failed to fetch article details from network for title: '$title' (parse result or essential fields missing). API Response: $articleParseApiResponseByTitle"
                Log.e(TAG, errorMsg)
                emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg))
                return@flow
            }

            val htmlContentFromTitleParse = parseResultByTitle.text
            val pageIdFromTitleParse = parseResultByTitle.pageid
            val canonicalTitleFromTitleParse = parseResultByTitle.title // Should be plain text
            val displayTitleFromTitleParse = parseResultByTitle.displaytitle ?: canonicalTitleFromTitleParse // Can be HTML
            val revIdFromTitleParse = parseResultByTitle.revid
            val finalWikiUrl = "https://oldschool.runescape.wiki/w/${canonicalTitleFromTitleParse.replace(" ", "_")}"

            // Use OfflineCacheUtil to get a plain text version of the display title
            val plainTextDisplayTitle = OfflineCacheUtil.stripHtml(displayTitleFromTitleParse) ?: canonicalTitleFromTitleParse


            val uiState = PageUiState(
                isLoading = false,
                error = null,
                imageUrl = null,
                pageId = pageIdFromTitleParse,
                title = displayTitleFromTitleParse, // For display (can be HTML)
                plainTextTitle = plainTextDisplayTitle, // For logic/API params
                htmlContent = htmlContentFromTitleParse,
                wikiUrl = finalWikiUrl,
                revisionId = revIdFromTitleParse,
                lastFetchedTimestamp = System.currentTimeMillis(),
                localFilePath = null,
                isCurrentlyOffline = false
            )
            Log.i(TAG, "Successfully fetched title: '$title' (resolved to plain title: '${uiState.plainTextTitle}', pageId: ${uiState.pageId}) from network for online viewing (no save).")
            emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))
        } catch (e: HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "Error body unreadable" } ?: "Unknown API error"
            val errorMsg = "API request failed for title '$title': ${e.code()} - $errorBody"
            Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: IOException) {
            val errorMsg = "Network/IO error while fetching details for title '$title': ${e.message}"
            Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error while fetching article by title '$title': ${e.message}"
            Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        }
    }.flowOn(Dispatchers.IO)

    fun isArticleOffline(pageId: Int): kotlinx.coroutines.flow.Flow<Boolean> {
        Log.d(TAG, "Checking offline status for pageId: $pageId")
        return articleMetaDao.getMetaByPageIdFlow(pageId).map { meta ->
            val isOffline = meta != null && meta.localFilePath.isNotEmpty()
            Log.d(TAG, "PageId: $pageId, Meta: ${meta != null}, FilePath: ${meta?.localFilePath}, IsOffline: $isOffline")
            isOffline
        }
    }

    suspend fun removeArticleOffline(pageId: Int): com.omiyawaki.osrswiki.util.Result<Unit> {
        Log.d(TAG, "Attempting to remove article offline. PageId: $pageId")
        return withContext(Dispatchers.IO) {
            try {
                val articleMeta = articleMetaDao.getMetaByPageId(pageId)
                    ?: return@withContext com.omiyawaki.osrswiki.util.Result.Error("Article with pageId $pageId not found offline.")

                if (articleMeta.localFilePath.isNotEmpty()) {
                    val localFile = File(articleMeta.localFilePath)
                    if (localFile.exists()) {
                        Log.d(TAG, "Deleting local file: ${localFile.absolutePath}")
                        if (!localFile.delete()) {
                            Log.e(TAG, "Failed to delete local file: ${localFile.absolutePath}")
                            return@withContext com.omiyawaki.osrswiki.util.Result.Error("Failed to delete local file: ${localFile.absolutePath}")
                        }
                        Log.i(TAG, "Successfully deleted local file: ${localFile.absolutePath}")
                    } else {
                        Log.w(TAG, "Local file path specified but file not found: ${localFile.absolutePath}")
                    }
                } else {
                    Log.w(TAG, "No local file path found in metadata for pageId $pageId. Skipping file deletion.")
                }

                articleMetaDao.delete(articleMeta)
                Log.i(TAG, "Successfully removed article metadata for pageId $pageId from database.")
                com.omiyawaki.osrswiki.util.Result.Success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Error removing article offline for pageId $pageId: ${e.message}", e)
                com.omiyawaki.osrswiki.util.Result.Error("Error removing article: ${e.message}", e)
            }
        }
    }

    suspend fun saveArticleOffline(pageId: Int, displayTitleFromUi: String?): com.omiyawaki.osrswiki.util.Result<Unit> {
        Log.d(TAG, "Attempting to save article offline. PageId: $pageId, DisplayTitleFromUI: \"$displayTitleFromUi\"")
        // This 'displayTitleFromUi' might still contain HTML.
        // The API call below should use a canonical/plain text title if possible,
        // or the pageId which is more reliable.
        // The existing call uses pageId, which is good.

        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Fetch comprehensive article data using pageId
                val apiResponse = mediaWikiApiService.getArticleParseDataByPageId(pageId = pageId)
                val parseResult = apiResponse.parse

                if (parseResult == null || parseResult.text.isNullOrEmpty() || parseResult.title.isNullOrEmpty() || parseResult.pageid == null) {
                    val errorMsg = "API response 'parse' or essential fields (text, title, pageid) are null for pageId $pageId. Full response: $apiResponse"
                    Log.e(TAG, errorMsg)
                    return@withContext com.omiyawaki.osrswiki.util.Result.Error(errorMsg)
                }

                val canonicalTitle = parseResult.title // This is the canonical (plain text) title from API
                val htmlContent = parseResult.text
                val revisionId = parseResult.revid
                val fetchedPageId = parseResult.pageid

                // Step 2: Construct canonical URL and local file path
                val articleUrl = "https://oldschool.runescape.wiki/w/${canonicalTitle.replace(" ", "_")}"
                val fileName = "$fetchedPageId$HTML_EXTENSION"
                val articlesDir = File(applicationContext.filesDir, ARTICLES_DIR_NAME)
                if (!articlesDir.exists()) {
                    if (!articlesDir.mkdirs()) {
                        Log.e(TAG, "Failed to create directory: ${articlesDir.absolutePath}")
                        return@withContext com.omiyawaki.osrswiki.util.Result.Error("Failed to create storage directory.")
                    }
                }
                val articleFile = File(articlesDir, fileName)

                // Step 3: Save HTML content to file
                try {
                    articleFile.writeText(htmlContent)
                    Log.i(TAG, "Successfully saved HTML content for '$canonicalTitle' (PageID $fetchedPageId) to: ${articleFile.absolutePath}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to save HTML content for '$canonicalTitle' to file: ${e.message}", e)
                    return@withContext com.omiyawaki.osrswiki.util.Result.Error("Failed to save article HTML to file: ${e.message}", e)
                }

                // Step 4: Create or Update ArticleMetaEntity in database
                val existingMeta = articleMetaDao.getMetaByPageId(fetchedPageId)
                val currentTime = System.currentTimeMillis()

                if (existingMeta != null) {
                    // Update existing entity
                    val updatedMeta = existingMeta.copy(
                        title = canonicalTitle, // Store canonical (plain text) title
                        wikiUrl = articleUrl,
                        localFilePath = articleFile.absolutePath,
                        lastFetchedTimestamp = currentTime,
                        revisionId = revisionId,
                        categories = existingMeta.categories
                    )
                    articleMetaDao.update(updatedMeta)
                    Log.i(TAG, "Successfully updated metadata for '$canonicalTitle' (PageID: $fetchedPageId) in database.")
                } else {
                    // Insert new entity
                    val newMeta = com.omiyawaki.osrswiki.database.ArticleMetaEntity( // Ensure full path if not imported
                        id = 0L,
                        pageId = fetchedPageId,
                        title = canonicalTitle, // Store canonical (plain text) title
                        wikiUrl = articleUrl,
                        localFilePath = articleFile.absolutePath,
                        lastFetchedTimestamp = currentTime,
                        revisionId = revisionId,
                        categories = null
                    )
                    articleMetaDao.insert(newMeta)
                    Log.i(TAG, "Successfully inserted new metadata for '$canonicalTitle' (PageID: $fetchedPageId) in database.")
                }
                com.omiyawaki.osrswiki.util.Result.Success(Unit)

            } catch (e: HttpException) {
                val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "Error body unreadable" } ?: "Unknown API error"
                val errorMsg = "API request failed for pageId $pageId during save: ${e.code()} - $errorBody"
                Log.e(TAG, errorMsg, e)
                com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e)
            } catch (e: IOException) {
                val errorMsg = "Network/IO error for pageId $pageId during save: ${e.message}"
                Log.e(TAG, errorMsg, e)
                com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e)
            } catch (e: Exception) {
                val errorMsg = "Unexpected error for pageId $pageId during save: ${e.message}"
                Log.e(TAG, errorMsg, e)
                com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e)
            }
        }
    }
}
