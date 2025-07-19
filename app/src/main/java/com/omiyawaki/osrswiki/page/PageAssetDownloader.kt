package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.cache.AssetCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Result

data class AssetUrls(val priority: List<String>, val background: List<String>)
data class DownloadResult(val parseResult: ParseResult, val backgroundUrls: List<String>)

class PageAssetDownloader(
    private val wikiApiService: WikiApiService,
    private val okHttpClient: OkHttpClient
) {
    private val logTag = "PageLoadTrace"
    private val wikiSiteUrl = "https://oldschool.runescape.wiki"
    private val downloadSemaphore = Semaphore(8)

    suspend fun downloadPriorityAssets(
        pageId: Int,
        pageUrl: String,
        progressReporter: suspend (Int) -> Unit
    ): Result<DownloadResult> = coroutineScope {
        try {
            val htmlDataDeferred = async(Dispatchers.IO) { wikiApiService.getArticleParseDataByPageId(pageId) }
            val htmlResponse = htmlDataDeferred.await()
            val parseResult = htmlResponse.parse
                ?: return@coroutineScope Result.failure(Exception("Failed to parse API response."))
            val htmlContent = parseResult.text ?: ""

            val (priorityUrls, backgroundUrls) = extractAssetUrls(htmlContent)
            Log.d(logTag, " - Priority assets: ${priorityUrls.size}, Background assets: ${backgroundUrls.size}")

            val totalAssets = 1 + priorityUrls.size
            val assetsDownloaded = AtomicInteger(0)

            val htmlBytes = htmlContent.toByteArray()
            AssetCache.put(pageUrl, htmlBytes)
            val initialProgress = (assetsDownloaded.incrementAndGet() * 50 / totalAssets)
            progressReporter(initialProgress)

            priorityUrls.forEach { imageUrl ->
                launch(Dispatchers.IO) {
                    downloadSemaphore.withPermit {
                        downloadAndCache(imageUrl)
                        val currentProgress = (assetsDownloaded.incrementAndGet() * 50 / totalAssets)
                        progressReporter(currentProgress)
                    }
                }
            }
            // This return needs to wait for all priority downloads to complete.
            // A more robust implementation would use structured concurrency with a supervisor job.
            // For now, we rely on the parent coroutineScope to wait.

            Result.success(DownloadResult(parseResult, backgroundUrls))
        } catch (e: Exception) {
            Log.e(logTag, " - Download failed with exception", e)
            Result.failure(e)
        }
    }

    suspend fun downloadBackgroundAssets(scope: CoroutineScope, urls: List<String>) {
        Log.d(logTag, "Starting background download of ${urls.size} assets.")
        scope.launch(Dispatchers.IO) {
            urls.forEach { url ->
                launch {
                    downloadSemaphore.withPermit {
                        downloadAndCache(url)
                    }
                }
            }
        }
    }

    private suspend fun downloadAndCache(url: String) {
        try {
            if (AssetCache.get(url) != null) {
                Log.d(logTag, " - BG Cache HIT for $url")
                return
            }
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body!!.bytes()
                AssetCache.put(url, bytes)
                Log.d(logTag, " - BG Cache MISS & DOWNLOADED for $url")
            }
        } catch (e: Exception) {
            Log.e(logTag, " - Background download FAILED for $url", e)
        }
    }

    private fun extractAssetUrls(html: String): AssetUrls {
        val document = Jsoup.parse(html)
        val priorityUrls = mutableSetOf<String>()
        val allUrls = mutableSetOf<String>()

        document.select("img").forEach { element ->
            addUrlsFromElement(element, allUrls)
            if (element.closest(".infobox, .mw-halign-left") != null) {
                addUrlsFromElement(element, priorityUrls)
            }
        }

        val backgroundUrls = allUrls - priorityUrls
        return AssetUrls(priorityUrls.toList(), backgroundUrls.toList())
    }

    private fun addUrlsFromElement(element: Element, destination: MutableSet<String>) {
        element.attr("src").takeIf { it.isNotBlank() }?.let {
            destination.add(makeUrlAbsolute(it))
        }
        element.attr("srcset").takeIf { it.isNotBlank() }?.let { srcset ->
            srcset.split(",").forEach { part ->
                val url = part.trim().split("\\s+".toRegex()).firstOrNull()
                if (url != null && url.isNotBlank()) {
                    destination.add(makeUrlAbsolute(url))
                }
            }
        }
    }

    private fun makeUrlAbsolute(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$wikiSiteUrl$url"
            else -> url
        }
    }
}
