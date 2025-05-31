package com.omiyawaki.osrswiki.offline.downloader

import android.util.Log
import com.omiyawaki.osrswiki.network.interceptor.OfflineAssetInterceptor
import com.omiyawaki.osrswiki.database.SavedArticleEntry
import com.omiyawaki.osrswiki.database.SavedArticleEntryDao
import com.omiyawaki.osrswiki.database.ArticleSaveStatus // Import the correct enum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
// Response import was unused, can be removed if still unused after changes
// import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

// Local ArticleSaveStatus object removed, use the one from SavedArticleEntry.kt (now database package)

class ArticleDownloader(
    private val okHttpClient: OkHttpClient, // Must be configured with OfflineAssetInterceptor
    private val savedArticleEntryDao: SavedArticleEntryDao,
    private val mediaWikiApiBaseUrl: String = "https://oldschool.runescape.wiki/api.php"
) {
    companion object {
        private const val TAG = "ArticleDownloader"
    }

    /**
     * Downloads an article and its images for offline use.
     * Assumes SavedArticleEntry has a 'status: String' field.
     *
     * @param articlePageTitle The MediaWiki title of the page (e.g., "Abyssal_whip").
     * @param articleHtmlUrl The direct URL to the article's HTML content (if known, otherwise could be derived).
     * @param displayTitle The title to be displayed for the article.
     * @param normalizedTitle The normalized title for searching.
     * @param snippet An optional snippet.
     * @return True if the core HTML download was successful, false otherwise.
     * Image download failures are logged but don't cause this method to return false.
     */
    suspend fun downloadAndSaveArticle(
        articlePageTitle: String,
        articleHtmlUrl: String,
        displayTitle: String,
        normalizedTitle: String,
        snippet: String?
    ): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting download for article: $displayTitle (URL: $articleHtmlUrl)")

        var currentStatus = ArticleSaveStatus.PENDING // Use the imported enum
        var entryId: Long = 0

        try {
            val preliminaryEntry = SavedArticleEntry(
                articleTitle = displayTitle,
                normalizedArticleTitle = normalizedTitle,
                snippet = snippet,
                timestamp = System.currentTimeMillis(),
                status = ArticleSaveStatus.DOWNLOADING_HTML.name // Set initial status using enum
            )
            entryId = savedArticleEntryDao.insert(preliminaryEntry)

            if (entryId <= 0L) { // More robust check for failed insert
                Log.e(TAG, "Failed to insert preliminary SavedArticleEntry for $displayTitle, ID: $entryId")
                // No need to update status if insert failed and no valid ID was obtained
                return@withContext false
            }
            currentStatus = ArticleSaveStatus.DOWNLOADING_HTML // Status already set in preliminaryEntry
            Log.d(TAG, "Preliminary entry inserted with ID: $entryId, status: ${currentStatus.name}")


            Log.d(TAG, "Requesting HTML download for $articleHtmlUrl, to be handled by interceptor.")
            val htmlRequestBuilder = Request.Builder()
                .url(articleHtmlUrl)
                .header(OfflineAssetInterceptor.HEADER_SAVE_ASSET, "true")
                .header(OfflineAssetInterceptor.HEADER_SAVED_ARTICLE_ENTRY_ID, entryId.toString())
                .header(OfflineAssetInterceptor.HEADER_ASSET_ORIGINAL_URL, articleHtmlUrl)
                .header(OfflineAssetInterceptor.HEADER_ASSET_TYPE, "html")

            val htmlRequest = htmlRequestBuilder.build() // Call .build() here
            val htmlResponse = okHttpClient.newCall(htmlRequest).execute()

            if (!htmlResponse.isSuccessful) {
                Log.e(TAG, "HTML download failed for $articleHtmlUrl. Code: ${htmlResponse.code}")
                currentStatus = ArticleSaveStatus.FAILED
                savedArticleEntryDao.updateStatus(entryId, currentStatus.name)
                htmlResponse.close()
                return@withContext false
            }
            htmlResponse.close() // Close response body, interceptor handled saving.
            Log.i(TAG, "HTML for $displayTitle processed by interceptor.")
            currentStatus = ArticleSaveStatus.DOWNLOADING_IMAGES
            savedArticleEntryDao.updateStatus(entryId, currentStatus.name)


            // Step 2: Get image filenames from the page
            Log.d(TAG, "Fetching image filenames for page title: $articlePageTitle")
            val imageFileTitles = getImageFileTitles(articlePageTitle)
            if (imageFileTitles.isEmpty()) {
                Log.i(TAG, "No image file titles found for $articlePageTitle.")
            } else {
                Log.d(TAG, "Found ${imageFileTitles.size} image file titles. Fetching their URLs...")
                // Step 3: Get image URLs from filenames
                val imageUrls = getImageUrls(imageFileTitles)
                Log.d(TAG, "Found ${imageUrls.size} image URLs to download.")

                for (imageUrl in imageUrls) {
                    Log.d(TAG, "Requesting image download for $imageUrl, to be handled by interceptor.")
                    val imageRequestBuilder = Request.Builder() // Create new builder instance
                        .url(imageUrl)
                        .header(OfflineAssetInterceptor.HEADER_SAVE_ASSET, "true")
                        .header(OfflineAssetInterceptor.HEADER_SAVED_ARTICLE_ENTRY_ID, entryId.toString())
                        .header(OfflineAssetInterceptor.HEADER_ASSET_ORIGINAL_URL, imageUrl)
                        .header(OfflineAssetInterceptor.HEADER_ASSET_TYPE, "image")

                    val imageRequest = imageRequestBuilder.build() // Call .build() here
                    try {
                        val imageResponse = okHttpClient.newCall(imageRequest).execute()
                        if (!imageResponse.isSuccessful) {
                            Log.w(TAG, "Failed to download image $imageUrl. Code: ${imageResponse.code}")
                        }
                        imageResponse.close() // Close response, interceptor handled content
                    } catch (e: IOException) {
                        Log.w(TAG, "IOException during image download for $imageUrl", e)
                        // Continue to next image
                    }
                }
            }

            Log.i(TAG, "Article download process complete for $displayTitle.")
            currentStatus = ArticleSaveStatus.COMPLETE
            savedArticleEntryDao.updateStatus(entryId, currentStatus.name)
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error during article download for $displayTitle", e)
            currentStatus = ArticleSaveStatus.FAILED
            if (entryId > 0L) { // Only update status if we have a valid ID
                savedArticleEntryDao.updateStatus(entryId, currentStatus.name)
            }
            return@withContext false
        }
        // 'finally' block for status update is removed as status is updated at each step or failure.
    }

    @Throws(IOException::class)
    private fun getImageFileTitles(pageTitle: String): List<String> {
        val urlBuilder = mediaWikiApiBaseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid MediaWiki API base URL")
        urlBuilder.addQueryParameter("action", "query")
        urlBuilder.addQueryParameter("titles", pageTitle)
        urlBuilder.addQueryParameter("prop", "images")
        urlBuilder.addQueryParameter("imlimit", "max") // Get all images
        urlBuilder.addQueryParameter("format", "json")

        val request = Request.Builder().url(urlBuilder.build()).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) throw IOException("API request for image titles failed with code ${response.code}")

        val responseBody = response.body?.string() ?: throw IOException("Empty response body for image titles")
        response.close()

        val fileTitles = mutableListOf<String>()
        val jsonResponse = JSONObject(responseBody)
        val pages = jsonResponse.optJSONObject("query")?.optJSONObject("pages")
        pages?.keys()?.forEach { pageIdKey -> // Renamed pageId to avoid conflict if needed
            val page = pages.optJSONObject(pageIdKey)
            page?.optJSONArray("images")?.let { imagesArray ->
                for (i in 0 until imagesArray.length()) {
                    imagesArray.optJSONObject(i)?.optString("title")?.let { title ->
                        if (title.isNotBlank()) fileTitles.add(title)
                    }
                }
            }
        }
        return fileTitles
    }

    @Throws(IOException::class)
    private fun getImageUrls(fileTitles: List<String>): List<String> {
        if (fileTitles.isEmpty()) return emptyList()

        // MediaWiki API often has a limit on the number of titles per request (e.g., 50)
        val imageUrls = mutableListOf<String>()
        val titleChunks = fileTitles.chunked(50) // Process in chunks of 50

        for (chunk in titleChunks) {
            val urlBuilder = mediaWikiApiBaseUrl.toHttpUrlOrNull()?.newBuilder()
                ?: throw IllegalArgumentException("Invalid MediaWiki API base URL")
            urlBuilder.addQueryParameter("action", "query")
            urlBuilder.addQueryParameter("titles", chunk.joinToString("|"))
            urlBuilder.addQueryParameter("prop", "imageinfo")
            urlBuilder.addQueryParameter("iiprop", "url")
            urlBuilder.addQueryParameter("format", "json")

            val request = Request.Builder().url(urlBuilder.build()).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "API request for image URLs failed for chunk with code ${response.code}")
                continue // Try next chunk
            }
            val responseBody = response.body?.string() ?: continue
            response.close()

            try {
                val jsonResponse = JSONObject(responseBody)
                val pages = jsonResponse.optJSONObject("query")?.optJSONObject("pages")
                pages?.keys()?.forEach { pageIdKey -> // Renamed pageId to avoid conflict
                    pages.optJSONObject(pageIdKey)?.optJSONArray("imageinfo")?.let { imageInfoArray ->
                        if (imageInfoArray.length() > 0) {
                            imageInfoArray.optJSONObject(0)?.optString("url")?.let { url ->
                                if (url.isNotBlank()) imageUrls.add(url)
                            }
                        }
                    }
                }
            } catch (e: org.json.JSONException) {
                Log.e(TAG, "Failed to parse JSON for image URLs: $responseBody", e)
            }
        }
        return imageUrls
    }
}
