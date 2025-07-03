package com.omiyawaki.osrswiki.page

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.network.WikiApiService
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import com.omiyawaki.osrswiki.offline.util.OfflineCacheUtil

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
        private const val FILE_NAMESPACE_PREFIX = "File:"
    }

    /**
     * Wraps the raw HTML fragment from the API in a full HTML document.
     * This method injects CSS to handle wide table scrolling and a viewport meta tag
     * for proper mobile scaling.
     *
     * @param content The HTML fragment from the MediaWiki API.
     * @return A full, well-formed HTML document as a string.
     */
    private fun buildFullHtmlDocument(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        margin: 0;
                        padding: 8px;
                        overflow-x: hidden; /* Prevents the body itself from scrolling horizontally */
                    }
                    .scrollable-table-wrapper {
                        width: 100%;
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch; /* Enables momentum scrolling on iOS */
                        border: 1px solid #555; /* Optional: adds a border to indicate scrollable area */
                    }
                    /* Style the scrollbar to be less obtrusive */
                    .scrollable-table-wrapper::-webkit-scrollbar {
                        height: 8px;
                    }
                    .scrollable-table-wrapper::-webkit-scrollbar-thumb {
                        background: #888;
                        border-radius: 4px;
                    }
                </style>
            </head>
            <body>
                $content
                <script>
                    // This script runs after the DOM is parsed.
                    var tables = document.querySelectorAll('table.wikitable, table.align-right-2');
                    tables.forEach(function(table) {
                        // Check if the table is not already inside our wrapper
                        if (table.parentElement.className !== 'scrollable-table-wrapper') {
                            var wrapper = document.createElement('div');
                            wrapper.className = 'scrollable-table-wrapper';
                            table.parentNode.insertBefore(wrapper, table);
                            wrapper.appendChild(table);
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun getArticle(pageId: Int, forceNetwork: Boolean = false): kotlinx.coroutines.flow.Flow<com.omiyawaki.osrswiki.util.Result<PageUiState>> = kotlinx.coroutines.flow.flow {
        emit(com.omiyawaki.osrswiki.util.Result.Loading)
        Log.d(TAG, "getArticle called for pageId: $pageId, forceNetwork: $forceNetwork")

        if (!forceNetwork) {
            Log.d(TAG, "getArticle (by ID): Attempting to load pageId: $pageId from local cache.")
            try {
                val localMeta = articleMetaDao.getMetaByPageId(pageId)
                Log.d(TAG, "getArticle (by ID): DAO query for pageId $pageId returned: $localMeta")
                if (localMeta != null && localMeta.localFilePath.isNotEmpty()) {
                    val localFile = File(localMeta.localFilePath)
                    if (localFile.exists()) {
                        Log.i(TAG, "getArticle (by ID): Found pageId: $pageId ('${localMeta.title}') in local cache at ${localFile.absolutePath}")
                        val htmlContent = localFile.readText()
                        val uiState = PageUiState(
                            isLoading = false, error = null, imageUrl = null,
                            pageId = localMeta.pageId,
                            title = localMeta.title,
                            plainTextTitle = localMeta.title,
                            htmlContent = htmlContent, // Saved content is already a full document
                            wikiUrl = localMeta.wikiUrl,
                            revisionId = localMeta.revisionId,
                            lastFetchedTimestamp = localMeta.lastFetchedTimestamp,
                            localFilePath = localMeta.localFilePath,
                            isCurrentlyOffline = true
                        )
                        emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))
                        return@flow
                    } else {
                        Log.w(TAG, "getArticle (by ID): Local cache metadata found for pageId: $pageId ('${localMeta.title}') but file missing: ${localMeta.localFilePath}")
                    }
                } else {
                    Log.d(TAG, "getArticle (by ID): pageId: $pageId not found in local cache metadata or no local file path.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "getArticle (by ID): Error reading pageId: $pageId from local cache: ${e.message}", e)
            }
        }

        Log.d(TAG, "getArticle (by ID): Attempting to fetch pageId $pageId from network.")
        try {
            val articleParseApiResponse = mediaWikiApiService.getArticleParseDataByPageId(pageId)
            val parseResult = articleParseApiResponse.parse

            if (parseResult == null || parseResult.title.isNullOrEmpty() || parseResult.text.isNullOrEmpty()) {
                val errorMsg = "Failed to fetch article details from network for pageId: $pageId (parse result, title, or text missing). API Response: $articleParseApiResponse"
                Log.e(TAG, errorMsg)
                emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg))
                return@flow
            }

            val fetchedCanonicalTitle = parseResult.title
            val fetchedDisplayTitle = parseResult.displaytitle ?: fetchedCanonicalTitle
            Log.i(TAG, "Network fetch for pageId $pageId yielded canonical title: '$fetchedCanonicalTitle', display title: '$fetchedDisplayTitle'")
            val articleUrl = "https://oldschool.runescape.wiki/w/${fetchedCanonicalTitle.replace(" ", "_")}"
            val htmlContentFromParse = buildFullHtmlDocument(parseResult.text)
            val plainTextDisplayTitle = OfflineCacheUtil.stripHtml(fetchedDisplayTitle) ?: fetchedCanonicalTitle

            val uiState = PageUiState(
                isLoading = false, error = null, imageUrl = null,
                pageId = parseResult.pageid ?: pageId,
                title = fetchedDisplayTitle,
                plainTextTitle = plainTextDisplayTitle,
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
            val errorMsg = "API request failed for pageId $pageId: ${e.code()} - $errorBody"; Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: IOException) {
            val errorMsg = "Network I/O error while fetching details for pageId $pageId: ${e.message}"; Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error while fetching article by pageId $pageId: ${e.message}"; Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        }
    }.flowOn(Dispatchers.IO)

    fun getArticleByTitle(title: String, forceNetwork: Boolean = false): kotlinx.coroutines.flow.Flow<com.omiyawaki.osrswiki.util.Result<PageUiState>> = kotlinx.coroutines.flow.flow {
        emit(com.omiyawaki.osrswiki.util.Result.Loading)
        Log.d(TAG, "getArticleByTitle called for: \"$title\", forceNetwork: $forceNetwork")

        if (!forceNetwork) {
            Log.d(TAG, "getArticleByTitle: Attempting to load \"$title\" (assumed canonical) from local cache.")
            try {
                val localMeta = articleMetaDao.getMetaByExactTitle(title)
                Log.d(TAG, "getArticleByTitle: DAO query for title '$title' returned: $localMeta")
                if (localMeta != null && localMeta.localFilePath.isNotEmpty()) {
                    val localFile = File(localMeta.localFilePath)
                    if (localFile.exists()) {
                        Log.i(TAG, "Found \"$title\" in local cache at ${localFile.absolutePath}")
                        val htmlContent = localFile.readText()
                        val uiState = PageUiState(
                            isLoading = false, error = null, imageUrl = null,
                            pageId = localMeta.pageId,
                            title = localMeta.title,
                            plainTextTitle = localMeta.title,
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
                    Log.d(TAG, "\"$title\" not found in local cache metadata by exact title or no local file path.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading \"$title\" from local cache: ${e.message}", e)
            }
        }

        Log.d(TAG, "Attempting to fetch \"$title\" (assumed canonical) from network.")
        try {
            if (title.startsWith(FILE_NAMESPACE_PREFIX, ignoreCase = true)) {
                // Handle File page: requires two API calls
                Log.d(TAG, "Handling as File page: $title")

                val imageInfoResponse = mediaWikiApiService.getImageInfo(title)
                val imageUrl = imageInfoResponse.query?.pages?.firstOrNull()?.imageInfo?.firstOrNull()?.url
                Log.d(TAG, "Fetched image URL: $imageUrl")

                val parseResponse = mediaWikiApiService.getArticleTextContentByTitle(title)
                val parseResult = parseResponse.parse
                val descriptionHtml = parseResult?.text ?: ""

                if (imageUrl == null || parseResult == null) {
                    throw IOException("Failed to retrieve complete File page data. Image URL or parse data missing.")
                }

                val imageTag = "<img src=\"$imageUrl\" style=\"background-color: #333; width: 100%; height: auto;\"/>"
                val finalHtml = buildFullHtmlDocument("$imageTag<br/>$descriptionHtml")

                val canonicalTitle = parseResult.title ?: title
                val displayTitle = parseResult.displaytitle ?: canonicalTitle
                val plainTextDisplayTitle = OfflineCacheUtil.stripHtml(displayTitle) ?: canonicalTitle
                val finalWikiUrl = "https://oldschool.runescape.wiki/w/${canonicalTitle.replace(" ", "_")}"

                val uiState = PageUiState(
                    isLoading = false, error = null,
                    pageId = parseResult.pageid ?: 0,
                    title = displayTitle,
                    plainTextTitle = plainTextDisplayTitle,
                    htmlContent = finalHtml,
                    wikiUrl = finalWikiUrl,
                    revisionId = parseResult.revid,
                    lastFetchedTimestamp = System.currentTimeMillis(),
                    localFilePath = null,
                    isCurrentlyOffline = false,
                    imageUrl = imageUrl
                )
                Log.i(TAG, "Successfully processed File page: '$title'")
                emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))

            } else {
                // Handle regular articles
                Log.d(TAG, "Handling as regular article: $title")
                val articleParseApiResponseByTitle = mediaWikiApiService.getArticleTextContentByTitle(title = title)
                val parseResultByTitle = articleParseApiResponseByTitle.parse

                if (parseResultByTitle == null || parseResultByTitle.text.isNullOrEmpty() || parseResultByTitle.pageid == null || parseResultByTitle.title.isNullOrEmpty()) {
                    val errorMsg = "Failed to fetch article details from network for title: '$title' (parse result or essential fields missing). API Response: $articleParseApiResponseByTitle"
                    Log.e(TAG, errorMsg)
                    emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg))
                    return@flow
                }

                val htmlContentFromTitleParse = buildFullHtmlDocument(parseResultByTitle.text)
                val pageIdFromTitleParse = parseResultByTitle.pageid
                val canonicalTitleFromTitleParse = parseResultByTitle.title
                val displayTitleFromTitleParse = parseResultByTitle.displaytitle ?: canonicalTitleFromTitleParse
                val revIdFromTitleParse = parseResultByTitle.revid
                val finalWikiUrl = "https://oldschool.runescape.wiki/w/${canonicalTitleFromTitleParse.replace(" ", "_")}"
                val plainTextDisplayTitle = OfflineCacheUtil.stripHtml(displayTitleFromTitleParse) ?: canonicalTitleFromTitleParse

                val uiState = PageUiState(
                    isLoading = false, error = null, imageUrl = null,
                    pageId = pageIdFromTitleParse,
                    title = displayTitleFromTitleParse,
                    plainTextTitle = plainTextDisplayTitle,
                    htmlContent = htmlContentFromTitleParse,
                    wikiUrl = finalWikiUrl,
                    revisionId = revIdFromTitleParse,
                    lastFetchedTimestamp = System.currentTimeMillis(),
                    localFilePath = null,
                    isCurrentlyOffline = false
                )
                Log.i(TAG, "Successfully fetched title: '$title' (resolved to plain title: '${uiState.plainTextTitle}', pageId: ${uiState.pageId}) from network for online viewing (no save).")
                emit(com.omiyawaki.osrswiki.util.Result.Success(uiState))
            }
        } catch (e: HttpException) {
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "Error body unreadable" } ?: "Unknown API error"
            val errorMsg = "API request failed for title '$title': ${e.code()} - $errorBody"; Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: IOException) {
            val errorMsg = "Network/IO error while fetching details for title '$title': ${e.message}"; Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        } catch (e: Exception) {
            val errorMsg = "Unexpected error while fetching article by title '$title': ${e.message}"; Log.e(TAG, errorMsg, e)
            emit(com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e))
        }
    }.flowOn(Dispatchers.IO)

    fun isArticleOffline(pageId: Int): kotlinx.coroutines.flow.Flow<Boolean> {
        Log.d(TAG, "Checking offline status for pageId: $pageId")
        return articleMetaDao.getMetaByPageIdFlow(pageId).map { meta ->
            val fileExists = meta?.localFilePath?.isNotEmpty() == true && File(meta.localFilePath).exists()
            val isOffline = meta != null && meta.localFilePath.isNotEmpty() && fileExists
            Log.d(TAG, "PageId: $pageId, Meta: ${meta != null}, FilePath: ${meta?.localFilePath}, File Exists: $fileExists, IsOffline: $isOffline")
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

        return withContext(Dispatchers.IO) {
            try {
                val apiResponse = mediaWikiApiService.getArticleParseDataByPageId(pageId = pageId)
                val parseResult = apiResponse.parse

                if (parseResult == null || parseResult.text.isNullOrEmpty() || parseResult.title.isNullOrEmpty() || parseResult.pageid == null) {
                    val errorMsg = "API response 'parse' or essential fields (text, title, pageid) are null for pageId $pageId. Full response: $apiResponse"
                    Log.e(TAG, errorMsg)
                    return@withContext com.omiyawaki.osrswiki.util.Result.Error(errorMsg)
                }

                val canonicalTitle = parseResult.title
                val htmlContent = buildFullHtmlDocument(parseResult.text)
                val revisionId = parseResult.revid
                val fetchedPageId = parseResult.pageid

                Log.i(TAG, "saveArticleOffline: For pageId $fetchedPageId, canonicalTitle from API to be stored is: \"$canonicalTitle\"")

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

                try {
                    articleFile.writeText(htmlContent)
                    Log.i(TAG, "Successfully saved HTML content for '$canonicalTitle' (PageID $fetchedPageId) to: ${articleFile.absolutePath}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to save HTML content for '$canonicalTitle' to file: ${e.message}", e)
                    return@withContext com.omiyawaki.osrswiki.util.Result.Error("Failed to save article HTML to file: ${e.message}", e)
                }

                val existingMeta = articleMetaDao.getMetaByPageId(fetchedPageId)
                val currentTime = System.currentTimeMillis()

                if (existingMeta != null) {
                    val updatedMeta = existingMeta.copy(
                        title = canonicalTitle,
                        wikiUrl = articleUrl,
                        localFilePath = articleFile.absolutePath,
                        lastFetchedTimestamp = currentTime,
                        revisionId = revisionId,
                        categories = existingMeta.categories
                    )
                    articleMetaDao.update(updatedMeta)
                    Log.i(TAG, "Successfully updated metadata for '$canonicalTitle' (PageID: $fetchedPageId) in database.")
                } else {
                    val newMeta = com.omiyawaki.osrswiki.database.ArticleMetaEntity(
                        id = 0L, pageId = fetchedPageId, title = canonicalTitle,
                        wikiUrl = articleUrl, localFilePath = articleFile.absolutePath,
                        lastFetchedTimestamp = currentTime, revisionId = revisionId, categories = null
                    )
                    articleMetaDao.insert(newMeta)
                    Log.i(TAG, "Successfully inserted new metadata for '$canonicalTitle' (PageID: $fetchedPageId) in database.")
                }
                com.omiyawaki.osrswiki.util.Result.Success(Unit)
            } catch (e: HttpException) {
                val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { "Error body unreadable" } ?: "Unknown API error"
                val errorMsg = "API request failed for pageId $pageId during save: ${e.code()} - $errorBody"; Log.e(TAG, errorMsg, e)
                com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e)
            } catch (e: IOException) {
                val errorMsg = "Network/IO error for pageId $pageId during save: ${e.message}"; Log.e(TAG, errorMsg, e)
                com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e)
            } catch (e: Exception) {
                val errorMsg = "Unexpected error for pageId $pageId during save: ${e.message}"; Log.e(TAG, errorMsg, e)
                com.omiyawaki.osrswiki.util.Result.Error(errorMsg, e)
            }
        }
    }
}
