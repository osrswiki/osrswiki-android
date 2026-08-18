package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.page.preemptive.ArticlePreparationCoordinator
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmDecision
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmEnvironmentProvider
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmEnvironmentSubscription
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmLease
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmSuppression
import com.omiyawaki.osrswiki.page.preemptive.PreparedArticleCache
import com.omiyawaki.osrswiki.util.log.L
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

enum class ArticleContentSource { NETWORK, SAVED }

/** Prepared article text only. Theme and runtime reader preferences are applied at render time. */
data class DownloadResult(
    val processedHtml: String,
    val parseResult: ParseResult,
    val backgroundUrls: List<String>,
    val source: ArticleContentSource = ArticleContentSource.NETWORK
)

class PageAssetDownloader(
    private val okHttpClient: OkHttpClient,
    private val pageRepository: PageRepository? = null,
    processScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val wikiSiteUrl = "https://oldschool.runescape.wiki"
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private val largeArticleImageDeferralThreshold = 1_000
    private val transparentImagePlaceholder =
        "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='1'%20height='1'%3E%3C/svg%3E"
    private val prewarmEnvironmentListeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var prewarmEnvironmentProvider = ArticlePrewarmEnvironmentProvider {
        ArticlePrewarmDecision(ArticlePrewarmSuppression.NONE, maxConcurrent = 2)
    }
    private val preparationCoordinator = ArticlePreparationCoordinator(
        processScope = processScope,
        cache = PreparedArticleCache(),
        environmentProvider = ArticlePrewarmEnvironmentProvider {
            prewarmEnvironmentProvider.currentDecision()
        },
        prepare = ::prepareArticle
    )

    internal fun configurePrewarmEnvironment(provider: ArticlePrewarmEnvironmentProvider) {
        prewarmEnvironmentProvider = provider
        preparationCoordinator.environmentChanged()
    }

    internal fun notifyPrewarmEnvironmentChanged() {
        preparationCoordinator.environmentChanged()
        prewarmEnvironmentListeners.forEach { listener -> runCatching(listener) }
    }

    internal fun addPrewarmEnvironmentListener(
        listener: () -> Unit
    ): ArticlePrewarmEnvironmentSubscription {
        prewarmEnvironmentListeners += listener
        return ArticlePrewarmEnvironmentSubscription { prewarmEnvironmentListeners -= listener }
    }

    internal fun prewarmArticle(request: ArticlePrewarmRequest): ArticlePrewarmLease =
        preparationCoordinator.requestPrewarm(request)

    fun peekPreparedArticle(title: String?, pageId: Int? = null): DownloadResult? {
        val request = runCatching { ArticlePrewarmRequest(pageId = pageId, title = title) }.getOrNull()
            ?: return null
        return preparationCoordinator.peekPrepared(request)
    }

    internal fun clearPreparedArticleCache() = preparationCoordinator.clearCache()

    internal fun invalidatePreparedArticle(pageId: Int?, title: String?) {
        runCatching { ArticlePrewarmRequest(pageId = pageId, title = title) }
            .getOrNull()
            ?.let(preparationCoordinator::invalidate)
    }

    fun downloadPriorityAssetsByTitle(
        title: String,
        pageUrl: String,
        forceNetwork: Boolean = false
    ): Flow<DownloadProgress> {
        L.d("downloadPriorityAssetsByTitle: title=$title pageUrl=$pageUrl forceNetwork=$forceNetwork")
        return downloadPreparedArticle(ArticlePrewarmRequest(title = title), forceNetwork)
    }

    fun downloadPriorityAssets(
        pageId: Int,
        pageUrl: String,
        forceNetwork: Boolean = false,
        initialTitle: String? = null
    ): Flow<DownloadProgress> {
        L.d("downloadPriorityAssets: pageId=$pageId title=$initialTitle pageUrl=$pageUrl forceNetwork=$forceNetwork")
        return downloadPreparedArticle(
            ArticlePrewarmRequest(pageId = pageId, title = initialTitle),
            forceNetwork
        )
    }

    private fun downloadPreparedArticle(
        request: ArticlePrewarmRequest,
        forceNetwork: Boolean
    ): Flow<DownloadProgress> = channelFlow {
        val foregroundStart = System.nanoTime()
        send(DownloadProgress.FetchingHtml(0))
        try {
            val result = preparationCoordinator.awaitForeground(request, forceNetwork) { progress ->
                send(DownloadProgress.FetchingHtml(progress))
            }
            val elapsedMillis = (System.nanoTime() - foregroundStart) / 1_000_000L
            // The text success boundary precedes all optional asset work. PageContentLoader may
            // schedule assets only after this emission and the WebView can fetch visible images.
            L.d(
                "ArticlePrewarmTiming: foreground_text_ready key=${request.key.logValue()} " +
                    "elapsedMs=$elapsedMillis source=${result.source} chars=${result.processedHtml.length}"
            )
            send(DownloadProgress.Success(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            logPreparationFailure(request, failure)
            val userFailure = if (failure is kotlinx.serialization.SerializationException) {
                IOException("Invalid API response format", failure)
            } else {
                failure
            }
            send(DownloadProgress.Failure(userFailure))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun prepareArticle(
        request: ArticlePrewarmRequest,
        forceNetwork: Boolean,
        reportHtmlProgress: (Int) -> Unit
    ): DownloadResult {
        if (!forceNetwork) {
            val cachedPage = request.pageId?.let { pageRepository?.getSavedPageContent(it) }
                ?: request.title?.let { pageRepository?.getSavedPageContentByTitle(it) }
            if (cachedPage != null) {
                val parseResult = ParseResult(
                    title = cachedPage.plainTextTitle ?: request.title ?: "Page ${request.pageId}",
                    pageid = cachedPage.pageId ?: request.pageId ?: 0,
                    revid = cachedPage.revisionId ?: 0,
                    text = extractBodyFromHtml(cachedPage.htmlContent),
                    displaytitle = cachedPage.title
                )
                reportHtmlProgress(100)
                return processPreparedText(parseResult, ArticleContentSource.SAVED)
            }
        }

        val apiUrl = request.pageId?.let { pageId ->
            "$wikiSiteUrl/api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&mobileformat=html&disableeditsection=true&disablelimitreport=true&maxage=300&smaxage=300&pageid=$pageId"
        } ?: run {
            val encodedTitle = java.net.URLEncoder.encode(requireNotNull(request.title), Charsets.UTF_8.name())
            "$wikiSiteUrl/api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&mobileformat=html&disableeditsection=true&disablelimitreport=true&maxage=300&smaxage=300&page=$encodedTitle"
        }
        val parseResult = fetchParseResult(apiUrl, reportHtmlProgress)
        return processPreparedText(parseResult, ArticleContentSource.NETWORK)
    }

    private suspend fun fetchParseResult(
        url: String,
        reportHtmlProgress: (Int) -> Unit
    ): ParseResult {
        L.d("fetchParseResult: Starting HTML download for URL: $url")
        val request = Request.Builder().url(url).build()
        val payload = readBodyCancellable(request, reportProgress = reportHtmlProgress)
        L.d("fetchParseResult: responseCode=${payload.code} successful=${payload.isSuccessful}")
        if (!payload.isSuccessful) {
            val errorMessage = "HTTP ${payload.code}: ${payload.message}"
            throw when (payload.code) {
                404 -> IOException("Page not found (404): The requested page does not exist")
                500 -> IOException("Server error (500): Wiki server is experiencing issues")
                503 -> IOException("Service unavailable (503): Wiki server is temporarily unavailable")
                else -> IOException("Network error: $errorMessage")
            }
        }
        val responseJson = payload.bytes.toString(Charsets.UTF_8)
        val apiResponse = jsonParser.decodeFromString<ArticleParseApiResponse>(responseJson)
        val parseResult = apiResponse.parse
            ?: throw IOException("API returned empty parse result - page may not exist or be accessible")
        reportHtmlProgress(100)
        return parseResult
    }

    private data class HttpBodyPayload(
        val code: Int,
        val message: String,
        val isSuccessful: Boolean,
        val bytes: ByteArray,
        val exceededLimit: Boolean = false
    )

    /** Keeps Call.cancel registered until the complete response body has been consumed. */
    private suspend fun readBodyCancellable(
        request: Request,
        maxBytes: Long = Long.MAX_VALUE,
        reportProgress: (Int) -> Unit = {}
    ): HttpBodyPayload =
        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!continuation.isActive) return
                            val body = response.body
                            if (body == null) {
                                continuation.resumeWithException(IOException("Response body is null"))
                                return
                            }
                            val totalBytes = body.contentLength()
                            if (totalBytes > maxBytes) {
                                continuation.resume(
                                    HttpBodyPayload(
                                        response.code,
                                        response.message,
                                        response.isSuccessful,
                                        ByteArray(0),
                                        exceededLimit = true
                                    )
                                )
                                return
                            }
                            val source = body.source()
                            val buffer = Buffer()
                            val outputStream = ByteArrayOutputStream()
                            var bytesRead = 0L
                            var lastProgress = -1
                            reportProgress(0)
                            while (continuation.isActive) {
                                val readCount = source.read(buffer, 8_192L)
                                if (readCount == -1L) break
                                bytesRead += readCount
                                if (bytesRead > maxBytes) {
                                    continuation.resume(
                                        HttpBodyPayload(
                                            response.code,
                                            response.message,
                                            response.isSuccessful,
                                            ByteArray(0),
                                            exceededLimit = true
                                        )
                                    )
                                    return
                                }
                                outputStream.write(buffer.readByteArray(readCount))
                                if (totalBytes > 0L) {
                                    val progress = ((bytesRead * 100L) / totalBytes).toInt()
                                    if (progress > lastProgress) {
                                        reportProgress(progress)
                                        lastProgress = progress
                                    }
                                }
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    HttpBodyPayload(
                                        response.code,
                                        response.message,
                                        response.isSuccessful,
                                        outputStream.toByteArray()
                                    )
                                )
                            }
                        }
                    } catch (failure: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }

    private suspend fun processPreparedText(
        parseResult: ParseResult,
        source: ArticleContentSource
    ): DownloadResult = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        val document = Jsoup.parse(parseResult.text.orEmpty(), "", Parser.xmlParser())
        currentCoroutineContext().ensureActive()
        // Prepared results are mode-independent and text-only. Image, map, and chart work is
        // deferred to the rendered WebView after Success, including when foreground joins prewarm.
        val processedHtml = preprocessHtml(document)
        currentCoroutineContext().ensureActive()
        DownloadResult(processedHtml, parseResult, backgroundUrls = emptyList(), source)
    }

    private fun logPreparationFailure(request: ArticlePrewarmRequest, failure: Throwable) {
        val prefix = "fetchParseResult: key=${request.key.logValue()}"
        when (failure) {
            is java.net.UnknownHostException -> L.e("$prefix DNS/network failure", failure)
            is java.net.SocketTimeoutException -> L.e("$prefix timeout", failure)
            is java.net.ConnectException -> L.e("$prefix connection failure", failure)
            is IOException -> if (isExpectedCancellation(failure)) {
                L.d("$prefix canceled")
            } else {
                L.e("$prefix network failure", failure)
            }
            is kotlinx.serialization.SerializationException -> L.e("$prefix JSON parsing failure", failure)
            else -> L.e("$prefix unexpected ${failure::class.simpleName}", failure)
        }
    }

    private suspend fun preprocessHtml(document: Document): String {
        val siteUrl = "https://oldschool.runescape.wiki"
        currentCoroutineContext().ensureActive()
        document.select("tr.advanced-data, tr.leagues-global-flag, tr.infobox-padding").remove()
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
        return SrcsetParser.rewriteUrls(srcset) { url ->
            val absoluteUrl = if (url.startsWith("/") && !url.startsWith("//")) siteUrl + url else url
            absoluteUrl
        }
    }

    private fun deferLargeArticleTableImages(document: Document) {
        val imageCount = document.select("img").size
        if (imageCount < largeArticleImageDeferralThreshold) return
        val imagesToDefer = document.select("table.wikitable img, table.navbox img")
        if (imagesToDefer.isEmpty()) return

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
        L.d("preprocessHtml: Deferred ${imagesToDefer.size} table/navbox images for article with $imageCount images.")
    }

    private fun isExpectedCancellation(error: Throwable): Boolean =
        error is IOException && error.message?.equals("Canceled", ignoreCase = true) == true

    private fun extractBodyFromHtml(fullHtml: String?): String? {
        if (fullHtml == null) return null
        return try {
            Jsoup.parse(fullHtml).body()?.apply { select("h1.page-header").remove() }?.html()
        } catch (failure: Exception) {
            L.e("extractBodyFromHtml: Failed to extract body from HTML", failure)
            fullHtml
        }
    }
}

sealed class DownloadProgress {
    data class FetchingHtml(val progress: Int) : DownloadProgress()
    data class FetchingAssets(val progress: Int) : DownloadProgress()
    data class Success(val result: DownloadResult) : DownloadProgress()
    data class Failure(val error: Throwable) : DownloadProgress()
}
