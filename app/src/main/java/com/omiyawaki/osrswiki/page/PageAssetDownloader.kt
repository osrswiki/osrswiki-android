package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.cache.AssetCache
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
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

data class AssetUrls(val priority: List<String>, val background: List<String>)
data class DownloadResult(val processedHtml: String, val parseResult: ParseResult, val backgroundUrls: List<String>)

class PageAssetDownloader(
    private val wikiApiService: WikiApiService,
    private val okHttpClient: OkHttpClient
) {
    private val wikiSiteUrl = "https://oldschool.runescape.wiki"
    private val downloadSemaphore = Semaphore(8)

    fun downloadPriorityAssetsByTitle(title: String, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        L.d("downloadPriorityAssetsByTitle: Starting flow for title: $title")
        L.d("downloadPriorityAssetsByTitle: -> Emitting FetchingHtml")
        send(DownloadProgress.FetchingHtml)

        val parseResult = try {
            L.d("downloadPriorityAssetsByTitle: Starting network call for HTML.")
            wikiApiService.getArticleTextContentByTitle(title).parse
        } catch (e: Exception) {
            L.e("API call failed for title: $title", e)
            send(DownloadProgress.Failure(e))
            return@channelFlow
        }
        L.d("downloadPriorityAssetsByTitle: Finished network call for HTML.")


        if (parseResult == null) {
            L.w("downloadPriorityAssetsByTitle: ParseResult was null for title: $title")
            send(DownloadProgress.Failure(Exception("Failed to parse API response for title: $title")))
            return@channelFlow
        }

        processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
    }.flowOn(Dispatchers.IO)

    fun downloadPriorityAssets(pageId: Int, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        L.d("downloadPriorityAssets: Starting flow for pageId: $pageId")
        L.d("downloadPriorityAssets: -> Emitting FetchingHtml")
        send(DownloadProgress.FetchingHtml)

        val parseResult = try {
            L.d("downloadPriorityAssets: Starting network call for HTML.")
            wikiApiService.getArticleParseDataByPageId(pageId).parse
        } catch (e: Exception) {
            L.e("API call failed for pageId: $pageId", e)
            send(DownloadProgress.Failure(e))
            return@channelFlow
        }
        L.d("downloadPriorityAssets: Finished network call for HTML.")


        if (parseResult == null) {
            L.w("downloadPriorityAssets: ParseResult was null for pageId: $pageId")
            send(DownloadProgress.Failure(Exception("Failed to parse API response for pageId: $pageId")))
            return@channelFlow
        }

        processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
    }.flowOn(Dispatchers.IO)

    private fun processAndDownloadAssets(parseResult: ParseResult, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        val rawHtmlContent = parseResult.text ?: ""
        val document = Jsoup.parse(rawHtmlContent, "", Parser.xmlParser())
        val (priorityUrls, backgroundUrls) = extractAssetUrls(document)
        val processedHtml = preprocessHtml(document)

        val totalAssets = priorityUrls.size
        L.d("processAndDownloadAssets: Found $totalAssets priority assets.")
        if (totalAssets == 0) {
            L.d("processAndDownloadAssets: No assets to download. -> Sending Success.")
            send(DownloadProgress.Success(DownloadResult(processedHtml, parseResult, backgroundUrls)))
            return@channelFlow
        }

        val assetsDownloaded = AtomicInteger(0)
        coroutineScope {
            priorityUrls.forEach { imageUrl ->
                launch {
                    downloadAndCache(imageUrl)
                    val progress = (assetsDownloaded.incrementAndGet() * 100 / totalAssets)
                    L.d("processAndDownloadAssets: Asset download progress: $progress%. -> Sending FetchingAssets.")
                    send(DownloadProgress.FetchingAssets(progress))
                }
            }
        }
        L.d("processAndDownloadAssets: All assets finished downloading. -> Sending Success.")
        send(DownloadProgress.Success(DownloadResult(processedHtml, parseResult, backgroundUrls)))
    }

    private fun preprocessHtml(document: Document): String {
        val siteUrl = "https://oldschool.runescape.wiki"
        document.select("[src], [href], [srcset]").forEach { element ->
            listOf("src", "href", "srcset").forEach { attr ->
                val originalUrl = element.attr(attr)
                if (originalUrl.startsWith("/") && !originalUrl.startsWith("//")) {
                    element.attr(attr, siteUrl + originalUrl)
                }
            }
        }
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
            if (AssetCache.get(url) != null) return
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body!!.bytes().also { AssetCache.put(url, it) }
            }
        } catch (e: Exception) {
            L.e("Background download FAILED for $url", e)
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
        return AssetUrls(priorityUrls.toList(), (allUrls - priorityUrls).toList())
    }

    private fun addUrlsFromElement(element: Element, destination: MutableSet<String>) {
        element.attr("src").takeIf { it.isNotBlank() }?.let { destination.add(makeUrlAbsolute(it)) }
        element.attr("srcset").takeIf { it.isNotBlank() }?.split(",")?.forEach { part ->
            part.trim().split("\\s+".toRegex()).firstOrNull()?.takeIf { it.isNotBlank() }?.let { destination.add(makeUrlAbsolute(it)) }
        }
    }

    private fun makeUrlAbsolute(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$wikiSiteUrl$url"
        else -> url
    }
}

sealed class DownloadProgress {
    object FetchingHtml : DownloadProgress()
    data class FetchingAssets(val progress: Int) : DownloadProgress()
    data class Success(val result: DownloadResult) : DownloadProgress()
    data class Failure(val error: Throwable) : DownloadProgress()
}
