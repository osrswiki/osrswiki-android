package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class PageRemoteDataSource(
    private val mediaWikiApiService: WikiApiService
) {
    companion object {
        private const val TAG = "PageRemoteDataSource"
    }

    suspend fun getArticleParseResult(pageId: Int): Result<ParseResult> {
        return withContext(Dispatchers.IO) {
            try {
                val response = mediaWikiApiService.getArticleParseDataByPageId(pageId)
                val parseResult = response.parse
                if (parseResult?.title != null && parseResult.text != null && parseResult.pageid != null) {
                    Result.Success(parseResult)
                } else {
                    Result.Error("Failed to parse article for pageId: $pageId. Response or key fields were null.")
                }
            } catch (e: Exception) {
                handleException(e, "pageId $pageId")
            }
        }
    }

    suspend fun getArticleParseResult(title: String): Result<ParseResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Use the corrected API service method.
                val response = mediaWikiApiService.getArticleParseDataByTitle(title)
                val parseResult = response.parse
                if (parseResult?.title != null && parseResult.text != null && parseResult.pageid != null) {
                    Result.Success(parseResult)
                } else {
                    Result.Error("Failed to parse article for title: '$title'. Response or key fields were null.")
                }
            } catch (e: Exception) {
                handleException(e, "title '$title'")
            }
        }
    }

    suspend fun getImageInfo(title: String): Result<ImageInfoResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = mediaWikiApiService.getImageInfo(title)
                Result.Success(response)
            } catch (e: Exception) {
                handleException(e, "image info for title '$title'")
            }
        }
    }

    private fun <T> handleException(e: Exception, context: String): Result<T> {
        val errorMsg = when (e) {
            is HttpException -> "API request failed for $context: ${e.code()}"
            is IOException -> "Network I/O error for $context: ${e.message}"
            else -> "Unexpected error for $context: ${e.message}"
        }
        Log.e(TAG, errorMsg, e)
        return Result.Error(errorMsg, e)
    }
}
