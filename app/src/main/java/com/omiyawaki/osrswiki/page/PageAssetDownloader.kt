package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.ImageInfo
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.cache.AssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Result

class PageAssetDownloader(
    private val wikiApiService: WikiApiService,
    private val okHttpClient: OkHttpClient
) {
    private val logTag = "PageLoadTrace"

    suspend fun downloadPageAndAssets(
        pageId: Int,
        pageUrl: String,
        progressReporter: suspend (Int) -> Unit
    ): Result<ParseResult> = coroutineScope {
        lateinit var finalParseResult: ParseResult
        try {
            // 1. Fetch HTML and image metadata in parallel on an IO thread.
            val htmlDataDeferred = async(Dispatchers.IO) { wikiApiService.getArticleParseDataByPageId(pageId) }
            val imageInfoDeferred = async(Dispatchers.IO) { wikiApiService.getArticleImageInfo(pageId) }

            val htmlResponse = htmlDataDeferred.await()
            val imageInfoResponse = imageInfoDeferred.await()

            val parseResult = htmlResponse.parse
                ?: return@coroutineScope Result.failure(Exception("Failed to parse API response."))
            finalParseResult = parseResult
            val htmlContent = parseResult.text ?: ""

            // 2. Get the list of images to download.
            val images: List<ImageInfo> = imageInfoResponse.query?.pages
                ?.mapNotNull { page -> page.imageInfo?.firstOrNull() }
                ?: emptyList()

            // 3. Calculate total work based on file count (1 for HTML + number of images).
            val totalAssets = 1 + images.size
            val assetsDownloaded = AtomicInteger(0)
            Log.d(logTag, "  - Total assets to download: $totalAssets")

            // 4. Cache HTML and report initial progress.
            val htmlBytes = htmlContent.toByteArray()
            AssetCache.put(pageUrl, htmlBytes)
            val initialProgress = (assetsDownloaded.incrementAndGet() * 100 / totalAssets)
            progressReporter(initialProgress)
            Log.d(logTag, "  - Progress emitted: $initialProgress% (${assetsDownloaded.get()} / $totalAssets)")

            // 5. Download all image assets in parallel on an IO thread.
            images.forEach { image ->
                launch(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(image.url).build()
                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            val bytes = response.body!!.bytes()
                            AssetCache.put(image.url, bytes)
                            val currentProgress = (assetsDownloaded.incrementAndGet() * 100 / totalAssets)
                            progressReporter(currentProgress)
                            Log.d(logTag, "  - Progress emitted: $currentProgress% (${assetsDownloaded.get()} / $totalAssets) for ${image.url}")
                        }
                    } catch (e: Exception) {
                        Log.e(logTag, "  - Download FAILED for ${image.url}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "  - Download failed with exception", e)
            return@coroutineScope Result.failure(e)
        }
        Result.success(finalParseResult)
    }
}
