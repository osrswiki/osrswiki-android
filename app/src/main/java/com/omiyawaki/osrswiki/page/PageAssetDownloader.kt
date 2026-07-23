package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.cache.AssetCache
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val downloadSemaphore = Semaphore(2)
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val largeArticleImageDeferralThreshold = 1_000
    private val backgroundPrefetchLimit = 0
    private val maxBackgroundAssetBytes = 2 * 1024 * 1024L
    private val transparentImagePlaceholder =
        "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='1'%20height='1'%3E%3C/svg%3E"

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
        
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val apiUrl = "$wikiSiteUrl/api.php?action=parse&format=json&prop=text|revid|displaytitle&mobileformat=html&disableeditsection=true&page=$encodedTitle"
        L.d("downloadPriorityAssetsByTitle: Constructed API URL: $apiUrl")
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
        L.d("downloadPriorityAssets: Constructed API URL: $apiUrl")
        val parseResult = fetchParseResultWithProgress(apiUrl, this) ?: return@channelFlow

        L.d("downloadPriorityAssets: Finished HTML download, processing assets.")
        processAndDownloadAssets(parseResult, pageUrl).collect { send(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchParseResultWithProgress(url: String, flow: ProducerScope<DownloadProgress>): ParseResult? {
        try {
            L.d("fetchParseResultWithProgress: Starting HTML download for URL: $url")
            val request = Request.Builder().url(url).build()
            L.d("fetchParseResultWithProgress: Making network request...")
            
            okHttpClient.newCall(request).execute().use { response ->
                L.d("fetchParseResultWithProgress: Received response - Code: ${response.code}, Success: ${response.isSuccessful}")

                if (!response.isSuccessful) {
                    val errorMessage = "HTTP ${response.code}: ${response.message}"
                    L.e("fetchParseResultWithProgress: HTTP error - $errorMessage")
                    when (response.code) {
                        404 -> throw IOException("Page not found (404): The requested page does not exist")
                        500 -> throw IOException("Server error (500): Wiki server is experiencing issues")
                        503 -> throw IOException("Service unavailable (503): Wiki server is temporarily unavailable")
                        else -> throw IOException("Network error: $errorMessage")
                    }
                }

                val body = response.body ?: throw IOException("Response body is null")
                val totalBytes = body.contentLength()
                L.d("fetchParseResultWithProgress: Response body size: $totalBytes bytes")

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
                L.d("fetchParseResultWithProgress: Downloaded ${responseJson.length} characters of JSON")
                L.d("fetchParseResultWithProgress: JSON preview (first 200 chars): ${responseJson.take(200)}...")

                val apiResponseContainer = jsonParser.decodeFromString<ArticleParseApiResponse>(responseJson)
                L.d("fetchParseResultWithProgress: Successfully parsed JSON response")

                if (apiResponseContainer.parse == null) {
                    L.e("fetchParseResultWithProgress: API response parse object is null - possible API error")
                    throw IOException("API returned empty parse result - page may not exist or be accessible")
                }

                L.d("fetchParseResultWithProgress: Parse result - PageID: ${apiResponseContainer.parse.pageid}, Title: '${apiResponseContainer.parse.title}'")
                flow.send(DownloadProgress.FetchingHtml(100))
                return apiResponseContainer.parse
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            L.e("fetchParseResultWithProgress: DNS/Network error - could not resolve host for $url", e)
            flow.send(DownloadProgress.Failure(e))
            return null
        } catch (e: java.net.SocketTimeoutException) {
            L.e("fetchParseResultWithProgress: Request timeout for $url", e)
            flow.send(DownloadProgress.Failure(e))
            return null
        } catch (e: java.net.ConnectException) {
            L.e("fetchParseResultWithProgress: Connection failed for $url", e)
            flow.send(DownloadProgress.Failure(e))
            return null
        } catch (e: IOException) {
            if (isExpectedCancellation(e)) {
                L.d("fetchParseResultWithProgress: Request canceled for $url")
            } else {
                L.e("fetchParseResultWithProgress: Network error for $url", e)
            }
            flow.send(DownloadProgress.Failure(e))
            return null
        } catch (e: kotlinx.serialization.SerializationException) {
            L.e("fetchParseResultWithProgress: JSON parsing failed for $url - invalid API response format", e)
            flow.send(DownloadProgress.Failure(IOException("Invalid API response format", e)))
            return null
        } catch (e: Exception) {
            L.e("fetchParseResultWithProgress: Unexpected error during download/parse for $url - Error type: ${e::class.simpleName}", e)
            flow.send(DownloadProgress.Failure(e))
            return null
        }
    }

    private fun processAndDownloadAssets(parseResult: ParseResult, pageUrl: String): Flow<DownloadProgress> = channelFlow {
        val rawHtmlContent = parseResult.text ?: ""
        currentCoroutineContext().ensureActive()
        val document = Jsoup.parse(rawHtmlContent, "", Parser.xmlParser())
        currentCoroutineContext().ensureActive()
        val (priorityUrls, backgroundUrls) = extractAssetUrls(document)
        currentCoroutineContext().ensureActive()
        val processedHtml = preprocessHtml(document)
        currentCoroutineContext().ensureActive()

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
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.header("Content-Length")?.toLongOrNull() ?: 0L
                        } else {
                            0L
                        }
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
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val body = response.body ?: throw IOException("Response body is null")
                val source = body.source()
                val buffer = Buffer()
                val outputStream = ByteArrayOutputStream()

                while (true) {
                    val readCount = source.read(buffer, 8192L)
                    if (readCount == -1L) break
                    outputStream.write(buffer.readByteArray(readCount))
                    onProgress(readCount)
                }
                AssetCache.put(url, outputStream.toByteArray())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isExpectedCancellation(e)) {
                L.d("Download with progress canceled for $url: ${e.message}")
            } else {
                L.e("Download with progress FAILED for $url", e)
            }
        }
    }

    private suspend fun preprocessHtml(document: Document): String {
        val siteUrl = "https://oldschool.runescape.wiki"
        currentCoroutineContext().ensureActive()
        
        // Remove unwanted infobox sections that should be hidden by default
        val selectorsToRemove = listOf(
            "tr.advanced-data",
            "tr.leagues-global-flag", 
            "tr.infobox-padding"
        )
        document.select(selectorsToRemove.joinToString(", ")).remove()
        
        normalizeRelativeUrls(document, siteUrl)
        currentCoroutineContext().ensureActive()
        deferLargeArticleTableImages(document)
        currentCoroutineContext().ensureActive()
        return document.outerHtml()
    }

    private fun normalizeRelativeUrls(document: Document, siteUrl: String) {
        document.select("[src], [href], [srcset]").forEach { element ->
            listOf("src", "href").forEach { attr ->
                val originalUrl = element.attr(attr)
                if (originalUrl.startsWith("/") && !originalUrl.startsWith("//")) {
                    element.attr(attr, siteUrl + originalUrl)
                }
            }
            val originalSrcset = element.attr("srcset")
            if (originalSrcset.isNotBlank()) {
                element.attr("srcset", makeSrcsetAbsolute(originalSrcset, siteUrl))
            }
        }
    }

    private fun makeSrcsetAbsolute(srcset: String, siteUrl: String): String {
        return srcset.split(",").joinToString(", ") { candidate ->
            val trimmed = candidate.trim()
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            val url = parts.getOrNull(0).orEmpty()
            val descriptor = parts.getOrNull(1)
            val absoluteUrl = if (url.startsWith("/") && !url.startsWith("//")) siteUrl + url else url
            if (descriptor.isNullOrBlank()) absoluteUrl else "$absoluteUrl $descriptor"
        }
    }

    private fun deferLargeArticleTableImages(document: Document) {
        val imageCount = document.select("img").size
        if (imageCount < largeArticleImageDeferralThreshold) {
            return
        }

        val imagesToDefer = document.select("table.wikitable img, table.navbox img")
        if (imagesToDefer.isEmpty()) {
            return
        }

        imagesToDefer.forEach { image ->
            val src = image.attr("src")
            if (src.isNotBlank() && !src.startsWith("data:", ignoreCase = true)) {
                image.attr("data-osrs-deferred-src", src)
                image.attr("src", transparentImagePlaceholder)
            }
            val srcset = image.attr("srcset")
            if (srcset.isNotBlank()) {
                image.attr("data-osrs-deferred-srcset", srcset)
                image.removeAttr("srcset")
            }
            val sizes = image.attr("sizes")
            if (sizes.isNotBlank()) {
                image.attr("data-osrs-deferred-sizes", sizes)
                image.removeAttr("sizes")
            }
            image.addClass("osrs-deferred-table-image")
            image.attr("loading", "lazy")
            image.attr("decoding", "async")
        }
        L.d("preprocessHtml: Deferred ${imagesToDefer.size} table/navbox images for large article with $imageCount images.")
    }

    fun downloadBackgroundAssets(scope: CoroutineScope, urls: List<String>): Job {
        return scope.launch(Dispatchers.IO) {
            if (urls.isEmpty()) {
                return@launch
            }
            if (backgroundPrefetchLimit <= 0) {
                L.d("downloadBackgroundAssets: Background prefetch disabled; WebView will request non-priority assets on demand.")
                return@launch
            }
            val urlsToPrefetch = urls.take(backgroundPrefetchLimit)
            if (urls.size > urlsToPrefetch.size) {
                L.d("downloadBackgroundAssets: Prefetching ${urlsToPrefetch.size} of ${urls.size} background assets; WebView will request the rest on demand.")
            }
            urlsToPrefetch.forEach { url ->
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
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return
                    val contentLength = body.contentLength()
                    if (contentLength > maxBackgroundAssetBytes) {
                        L.d("downloadAndCache: Skipping oversized background asset ($contentLength bytes): $url")
                        return
                    }
                    val source = body.source()
                    val buffer = Buffer()
                    val outputStream = ByteArrayOutputStream()
                    var totalBytesRead = 0L
                    while (true) {
                        val readCount = source.read(buffer, 8192L)
                        if (readCount == -1L) break
                        totalBytesRead += readCount
                        if (totalBytesRead > maxBackgroundAssetBytes) {
                            L.d("downloadAndCache: Aborting oversized background asset after $totalBytesRead bytes: $url")
                            return
                        }
                        outputStream.write(buffer.readByteArray(readCount))
                    }
                    AssetCache.put(url, outputStream.toByteArray())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isExpectedCancellation(e)) {
                L.d("Background download canceled for $url: ${e.message}")
            } else {
                L.e("Background download FAILED for $url", e)
            }
        }
    }

    private fun isExpectedCancellation(error: Throwable): Boolean {
        return error is IOException && error.message?.equals("Canceled", ignoreCase = true) == true
    }

    private suspend fun extractAssetUrls(document: Document): AssetUrls {
        val priorityUrls = mutableSetOf<String>()
        val allUrls = mutableSetOf<String>()

        document.getElementsByTag("img").forEachIndexed { index, element ->
            if (index % 32 == 0) {
                currentCoroutineContext().ensureActive()
            }
            addUrlsFromElement(element, allUrls)
            if (isPriorityImageElement(element)) {
                addUrlsFromElement(element, priorityUrls)
            }
        }
        currentCoroutineContext().ensureActive()
        return AssetUrls(priorityUrls.toList(), (allUrls - priorityUrls).toList())
    }

    private fun isPriorityImageElement(element: Element): Boolean {
        var current: Element? = element
        while (current != null) {
            val classNames = current.classNames()
            if ("infobox" in classNames || "mw-halign-left" in classNames) {
                return true
            }
            current = current.parent()
        }
        return false
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
     * Removes any existing page-header titles to prevent duplication.
     */
    private fun extractBodyFromHtml(fullHtml: String?): String? {
        if (fullHtml == null) return null
        return try {
            val document = Jsoup.parse(fullHtml)
            val body = document.body()
            if (body != null) {
                // Remove any existing page-header titles to prevent duplication
                // when buildFullHtmlDocument adds a new title header
                body.select("h1.page-header").remove()
                body.html()
            } else {
                null
            }
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
