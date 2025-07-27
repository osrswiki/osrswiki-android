package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.cache.AssetCache
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

data class AssetUrls(val priority: List<String>, val background: List<String>)
data class DownloadResult(val processedHtml: String, val parseResult: ParseResult, val backgroundUrls: List<String>)

class PageAssetDownloader(
    private val okHttpClient: OkHttpClient,
    private val pageRepository: PageRepository? = null
) {
    private val wikiSiteUrl = "https://oldschool.runescape.wiki"
    private val downloadSemaphore = Semaphore(8)
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun downloadPriorityAssetsByTitle(title: String, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        L.d("downloadPriorityAssetsByTitle: Starting flow for title: $title")
        
        // First check if we have this page cached (including reading list saved pages)
        pageRepository?.let { repo ->
            val cachedPage = repo.getSavedPageContentByTitle(title)
            if (cachedPage != null) {
                L.d("downloadPriorityAssetsByTitle: Found cached content for title: $title")
                // Convert cached page to ParseResult format and check assets
                val parseResult = ParseResult(
                    title = cachedPage.plainTextTitle ?: title,
                    pageid = cachedPage.pageId ?: 0,
                    revid = cachedPage.revisionId ?: 0,
                    text = extractBodyFromHtml(cachedPage.htmlContent),
                    displaytitle = cachedPage.title
                )
                
                L.d("downloadPriorityAssetsByTitle: Processing cached content and checking assets.")
                processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
                return@channelFlow
            }
        }
        
        val apiUrl = "$wikiSiteUrl/api.php?action=parse&format=json&prop=text|revid|displaytitle&mobileformat=html&disableeditsection=true&page=$title"
        val parseResult = fetchParseResultWithProgress(apiUrl, this) ?: return@channelFlow

        L.d("downloadPriorityAssetsByTitle: Finished HTML download, processing assets.")
        processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
    }.flowOn(Dispatchers.IO)

    fun downloadPriorityAssets(pageId: Int, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        L.d("downloadPriorityAssets: Starting flow for pageId: $pageId")
        
        // First check if we have this page cached (including reading list saved pages)
        pageRepository?.let { repo ->
            val cachedPage = repo.getSavedPageContent(pageId)
            if (cachedPage != null) {
                L.d("downloadPriorityAssets: Found cached content for pageId: $pageId")
                // Convert cached page to ParseResult format and check assets
                val parseResult = ParseResult(
                    title = cachedPage.plainTextTitle ?: "Page $pageId",
                    pageid = cachedPage.pageId ?: pageId,
                    revid = cachedPage.revisionId ?: 0,
                    text = extractBodyFromHtml(cachedPage.htmlContent),
                    displaytitle = cachedPage.title
                )
                
                L.d("downloadPriorityAssets: Processing cached content and checking assets.")
                processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
                return@channelFlow
            }
        }
        
        val apiUrl = "$wikiSiteUrl/api.php?action=parse&format=json&prop=text|revid|displaytitle&mobileformat=html&disableeditsection=true&pageid=$pageId"
        val parseResult = fetchParseResultWithProgress(apiUrl, this) ?: return@channelFlow

        L.d("downloadPriorityAssets: Finished HTML download, processing assets.")
        processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchParseResultWithProgress(url: String, flow: ProducerScope<DownloadProgress>): ParseResult? {
        try {
            L.d("fetchParseResultWithProgress: Starting HTML download for URL: $url")
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) throw IOException("Unexpected response code: ${response.code}")

            val body = response.body ?: throw IOException("Response body is null")
            val totalBytes = body.contentLength()
            val source = body.source()
            val buffer = Buffer()
            val outputStream = ByteArrayOutputStream()
            var bytesRead = 0L
            var lastSentProgress = -1

            flow.send(DownloadProgress.FetchingHtml(0))

            while (true) {
                val readCount = source.read(buffer, 8192L)
                if (readCount == -1L) break
                outputStream.write(buffer.readByteArray(readCount))
                bytesRead += readCount

                if (totalBytes > 0) {
                    val progress = ((bytesRead * 100) / totalBytes).toInt()
                    if (progress > lastSentProgress) {
                        flow.send(DownloadProgress.FetchingHtml(progress))
                        lastSentProgress = progress
                    }
                }
            }

            val responseJson = outputStream.toString()
            val apiResponseContainer = jsonParser.decodeFromString<ArticleParseApiResponse>(responseJson)

            if (apiResponseContainer.parse == null) {
                throw IOException("Failed to parse API response.")
            }
            flow.send(DownloadProgress.FetchingHtml(100))
            return apiResponseContainer.parse

        } catch (e: Exception) {
            L.e("fetchParseResultWithProgress: Download or parse failed for $url", e)
            flow.send(DownloadProgress.Failure(e))
            return null
        }
    }

    private fun processAndDownloadAssets(parseResult: ParseResult, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        val rawHtmlContent = parseResult.text ?: ""
        val document = Jsoup.parse(rawHtmlContent, "", Parser.xmlParser())
        val (priorityUrls, backgroundUrls) = extractAssetUrls(document)
        val processedHtml = preprocessHtml(document)

        if (priorityUrls.isEmpty()) {
            L.d("processAndDownloadAssets: No priority assets to download. -> Sending Success.")
            send(DownloadProgress.Success(DownloadResult(processedHtml, parseResult, backgroundUrls)))
            return@channelFlow
        }

        L.d("processAndDownloadAssets: Found ${priorityUrls.size} priority assets. Fetching sizes.")
        val totalAssetBytes = getTotalAssetSize(priorityUrls)
        L.d("processAndDownloadAssets: Total asset size: $totalAssetBytes bytes.")
        if (totalAssetBytes == 0L) {
            L.d("processAndDownloadAssets: Total asset size is zero. Skipping download phase. -> Sending Success.")
            send(DownloadProgress.Success(DownloadResult(processedHtml, parseResult, backgroundUrls)))
            return@channelFlow
        }

        val totalBytesRead = AtomicLong(0)
        var lastSentProgress = -1

        send(DownloadProgress.FetchingAssets(0))

        coroutineScope {
            priorityUrls.forEach { imageUrl ->
                launch {
                    downloadAndCacheWithProgress(imageUrl) { bytesRead ->
                        val currentTotal = totalBytesRead.addAndGet(bytesRead)
                        val progress = ((currentTotal * 100) / totalAssetBytes).toInt()
                        if (progress > lastSentProgress) {
                            send(DownloadProgress.FetchingAssets(progress))
                            lastSentProgress = progress
                        }
                    }
                }
            }
        }
        L.d("processAndDownloadAssets: All assets finished downloading. -> Sending Success.")
        send(DownloadProgress.FetchingAssets(100))
        send(DownloadProgress.Success(DownloadResult(processedHtml, parseResult, backgroundUrls)))
    }

    private suspend fun getTotalAssetSize(urls: List<String>): Long = coroutineScope {
        urls.map { url ->
            async {
                // Return 0 if already cached, as it won't be downloaded.
                if (AssetCache.get(url) != null) return@async 0L
                try {
                    val request = Request.Builder().url(url).head().build()
                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.header("Content-Length")?.toLongOrNull() ?: 0L
                    } else {
                        0L
                    }
                } catch (e: IOException) {
                    L.w("Failed to get content length for $url: ${e.message}")
                    0L
                }
            }
        }.sumOf { it.await() }
    }

    private suspend fun downloadAndCacheWithProgress(url: String, onProgress: suspend (Long) -> Unit) {
        try {
            if (AssetCache.get(url) != null) return

            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val body = response.body ?: throw IOException("Response body is null")
            val source = body.source()
            val buffer = Buffer()
            val outputStream = ByteArrayOutputStream()

            while (true) {
                val readCount = source.read(buffer, 8192L)
                if (readCount == -1L) break
                outputStream.write(buffer.snapshot().toByteArray())
                onProgress(readCount)
                buffer.skip(readCount)
            }
            AssetCache.put(url, outputStream.toByteArray())
        } catch (e: Exception) {
            L.e("Download with progress FAILED for $url", e)
        }
    }

    private fun preprocessHtml(document: Document): String {
        val siteUrl = "https://oldschool.runescape.wiki"
        
        // Remove unwanted infobox sections that should be hidden by default
        val selectorsToRemove = listOf(
            "tr.advanced-data",
            "tr.leagues-global-flag", 
            "tr.infobox-padding"
        )
        document.select(selectorsToRemove.joinToString(", ")).remove()
        
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
    
    /**
     * Extracts the body content from a full HTML document for processing.
     * This is needed when we have cached full HTML but need just the body content.
     */
    private fun extractBodyFromHtml(fullHtml: String?): String? {
        if (fullHtml == null) return null
        return try {
            val document = Jsoup.parse(fullHtml)
            document.body()?.html()
        } catch (e: Exception) {
            L.e("extractBodyFromHtml: Failed to extract body from HTML", e)
            fullHtml // Fallback to full HTML
        }
    }
}

sealed class DownloadProgress {
    data class FetchingHtml(val progress: Int) : DownloadProgress()
    data class FetchingAssets(val progress: Int) : DownloadProgress()
    data class Success(val result: DownloadResult) : DownloadProgress()
    data class Failure(val error: Throwable) : DownloadProgress()
}
