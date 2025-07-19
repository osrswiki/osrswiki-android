package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.dataclient.WikiSite
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Result
import kotlin.system.measureTimeMillis

data class AssetUrls(val priority: List<String>, val background: List<String>)
// The result now includes the fully processed HTML, ready for the WebView.
data class DownloadResult(val processedHtml: String, val parseResult: ParseResult, val backgroundUrls: List<String>)

class PageAssetDownloader(
    private val wikiApiService: WikiApiService,
    private val okHttpClient: OkHttpClient
) {
    private val logTag = "PageAssetDownloader"
    private val wikiSiteUrl = "https://oldschool.runescape.wiki"
    private val downloadSemaphore = Semaphore(8)

    suspend fun downloadPriorityAssetsByTitle(
        title: String,
        pageUrl: String,
        progressReporter: suspend (Int) -> Unit
    ): Result<DownloadResult> = coroutineScope {
        try {
            val htmlDataDeferred = async(Dispatchers.IO) { wikiApiService.getArticleTextContentByTitle(title) }
            val htmlResponse = htmlDataDeferred.await()
            val parseResult = htmlResponse.parse
                ?: return@coroutineScope Result.failure(Exception("Failed to parse API response for title: $title"))
            processAndDownloadAssets(parseResult, pageUrl, progressReporter)
        } catch (e: Exception) {
            Log.e(logTag, "Download by title failed for '$title'", e)
            Result.failure(e)
        }
    }

    suspend fun downloadPriorityAssets(
        pageId: Int,
        pageUrl: String,
        progressReporter: suspend (Int) -> Unit
    ): Result<DownloadResult> = coroutineScope {
        try {
            val htmlDataDeferred = async(Dispatchers.IO) { wikiApiService.getArticleParseDataByPageId(pageId) }
            val htmlResponse = htmlDataDeferred.await()
            val parseResult = htmlResponse.parse
                ?: return@coroutineScope Result.failure(Exception("Failed to parse API response for pageId: $pageId"))
            processAndDownloadAssets(parseResult, pageUrl, progressReporter)
        } catch (e: Exception) {
            Log.e(logTag, "Download by ID failed for '$pageId'", e)
            Result.failure(e)
        }
    }

    private suspend fun processAndDownloadAssets(
        parseResult: ParseResult,
        pageUrl: String,
        progressReporter: suspend (Int) -> Unit
    ): Result<DownloadResult> = coroutineScope {
        val rawHtmlContent = parseResult.text ?: ""
        val document = Jsoup.parse(rawHtmlContent, "", Parser.xmlParser())
        val (priorityUrls, backgroundUrls) = extractAssetUrls(document)
        val processedHtml = preprocessHtml(document)

        val totalAssets = 1 + priorityUrls.size
        val assetsDownloaded = AtomicInteger(0)

        val htmlBytes = rawHtmlContent.toByteArray()
        AssetCache.put(pageUrl, htmlBytes)
        val initialProgress = (assetsDownloaded.incrementAndGet() * 50 / totalAssets)
        progressReporter(initialProgress)

        coroutineScope {
            priorityUrls.forEach { imageUrl ->
                launch(Dispatchers.IO) {
                    downloadSemaphore.withPermit {
                        downloadAndCache(imageUrl)
                        val currentProgress = (assetsDownloaded.incrementAndGet() * 50 / totalAssets)
                        progressReporter(currentProgress)
                    }
                }
            }
        }
        Result.success(DownloadResult(processedHtml, parseResult, backgroundUrls))
    }


    private fun preprocessHtml(document: Document): String {
        val siteUrl = WikiSite.OSRS_WIKI.url()
        val urlAttributes = listOf("src", "href", "srcset")
        urlAttributes.forEach { attr ->
            document.select("[$attr]").forEach { element ->
                val originalUrl = element.attr(attr)
                if (originalUrl.startsWith("/") && !originalUrl.startsWith("//")) {
                    element.attr(attr, siteUrl + originalUrl)
                }
            }
        }
        document.outputSettings().prettyPrint(false)
        val resources = document.select("[class*=\"infobox-resources-\"]")
        resources.remove()
        val selectorsToRemove = listOf(
            "tr.advanced-data",
            "tr.leagues-global-flag",
            "tr.infobox-padding"
        )
        document.select(selectorsToRemove.joinToString(", ")).remove()
        resources.forEach { document.body().appendChild(it) }
        return document.outerHtml()
    }

    suspend fun downloadBackgroundAssets(scope: CoroutineScope, urls: List<String>) {
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
                return
            }
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body!!.bytes()
                AssetCache.put(url, bytes)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Background download FAILED for $url", e)
        }
    }

    private fun extractAssetUrls(document: Document): AssetUrls {
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
