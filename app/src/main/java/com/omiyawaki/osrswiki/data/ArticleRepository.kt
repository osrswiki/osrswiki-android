package com.omiyawaki.osrswiki.data

import android.util.Log
import com.omiyawaki.osrswiki.network.WikiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArticleTextContent(
    val title: String?,
    val htmlContent: String?
)

class ArticleRepository(private val apiService: WikiApiService) {

    private companion object {
        private const val TAG = "ArticleRepository"
    }

    suspend fun fetchArticleTextContent(pageId: Int): ArticleTextContent? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching article text content by pageId: $pageId")
                // Assuming apiService.getArticleContent returns ArticleParseApiResponse
                val response = apiService.getArticleContent(pageId)
                val parseData = response.parse
                // After model change, parseData.text is String?
                if (parseData != null && parseData.text != null) {
                    ArticleTextContent(title = parseData.title, htmlContent = parseData.text)
                } else {
                    Log.w(TAG, "fetchArticleTextContent: Parse data or text string is null for pageId $pageId. Response: $response")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchArticleTextContent: Exception fetching content for pageId $pageId", e)
                null
            }
        }
    }

    suspend fun fetchArticleTextContentByTitle(title: String): ArticleTextContent? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching article text content by title: $title")
                val response = apiService.getArticleTextContentByTitle(title = title)
                val parseData = response.parse
                // After model change, parseData.text is String?
                if (parseData != null && parseData.text != null) {
                    ArticleTextContent(title = parseData.title, htmlContent = parseData.text)
                } else {
                    Log.w(TAG, "fetchArticleTextContentByTitle: Parse data or text string is null for title '$title'. Response: $response")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchArticleTextContentByTitle: Exception fetching content for title '$title'", e)
                null
            }
        }
    }

    // Assuming a method like this exists for fetching image by ID, based on logs and structure
    suspend fun fetchArticleImageUrl(pageId: Int, thumbSize: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching article image URL by pageId: $pageId, thumbSize: $thumbSize")
                // Ensure 'getArticleImageUrl' matches your WikiApiService definition for by-ID image fetching
                val response = apiService.getArticleImageUrlById(pageId = pageId, pithumbsize = thumbSize)
                val imageUrl = response.query?.pages?.firstOrNull()?.thumbnail?.source
                if (imageUrl == null) {
                    Log.w(TAG, "fetchArticleImageUrl: Image URL is null for pageId $pageId, thumbSize $thumbSize. Response: $response")
                }
                imageUrl
            } catch (e: Exception) {
                Log.e(TAG, "fetchArticleImageUrl: Exception fetching image URL for pageId $pageId", e)
                null
            }
        }
    }

    suspend fun fetchArticleImageUrlByTitle(title: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching article image URL by title: $title")
                val response = apiService.getArticleImageUrlByTitle(title = title)
                val imageUrl = response.query?.pages?.firstOrNull()?.thumbnail?.source
                if (imageUrl == null) {
                    Log.w(TAG, "fetchArticleImageUrlByTitle: Image URL is null for title '$title'. Response: $response")
                }
                imageUrl
            } catch (e: Exception) {
                Log.e(TAG, "fetchArticleImageUrlByTitle: Exception fetching image URL for title '$title'", e)
                null
            }
        }
    }
}
