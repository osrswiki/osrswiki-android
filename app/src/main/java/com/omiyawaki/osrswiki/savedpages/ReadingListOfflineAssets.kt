package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.page.SrcsetParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.jsoup.Jsoup

/** In-process request marker so only explicit artwork settlement receives asset validation. */
internal object ReadingListAssetRequestMarker

internal class ReadingListAssetValidationException(
    val assetUrl: String,
    reason: String
) : IOException("Invalid explicit-save asset response for $assetUrl: $reason")

/**
 * Rejects captive/error HTML and verifies the advertised and actual kind of common artwork.
 * Validation reads only a bounded prefix after the interceptor has safely staged the response.
 */
internal object ReadingListAssetResponseValidator {
    private const val PREFIX_BYTES = 4_096
    private val svgElement = Regex("<svg(?:\\s|>)", RegexOption.IGNORE_CASE)

    fun invalidReason(url: String, contentType: String?, contentFile: File): String? {
        if (!contentFile.isFile || contentFile.length() <= 0L) return "empty response body"
        val mimeType = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        val prefix = contentFile.inputStream().use { input ->
            val bytes = ByteArray(PREFIX_BYTES)
            val count = input.read(bytes)
            if (count <= 0) byteArrayOf() else bytes.copyOf(count)
        }
        val expected = expectedKind(url)
        if (mimeType == "text/html" || mimeType == "application/xhtml+xml") {
            return "HTML response cannot satisfy an artwork request"
        }
        if (looksLikeHtml(prefix)) return "response body is HTML"

        return when (expected) {
            ExpectedKind.GIF -> requireKind(mimeType == "image/gif" && isGif(prefix), "GIF")
            ExpectedKind.PNG -> requireKind(mimeType == "image/png" && isPng(prefix), "PNG")
            ExpectedKind.JPEG -> requireKind(
                mimeType in setOf("image/jpeg", "image/jpg") && isJpeg(prefix),
                "JPEG"
            )
            ExpectedKind.WEBP -> requireKind(mimeType == "image/webp" && isWebp(prefix), "WebP")
            ExpectedKind.SVG -> requireKind(isSvgMime(mimeType) && isSvg(prefix), "SVG")
            ExpectedKind.CSS -> requireKind(mimeType == "text/css", "CSS")
            ExpectedKind.OTHER_IMAGE -> requireKind(
                mimeType.startsWith("image/") && prefix.isNotEmpty(),
                "image"
            )
            ExpectedKind.UNKNOWN -> validateMimeIdentifiedAsset(mimeType, prefix)
        }
    }

    fun contentTypeFromMetadata(metadataFile: File): String? = metadataFile
        .takeIf(File::isFile)
        ?.useLines { lines ->
            lines.firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
        }

    private fun validateMimeIdentifiedAsset(mimeType: String, prefix: ByteArray): String? = when {
        mimeType == "image/gif" -> requireKind(isGif(prefix), "GIF")
        mimeType == "image/png" -> requireKind(isPng(prefix), "PNG")
        mimeType == "image/jpeg" || mimeType == "image/jpg" -> requireKind(isJpeg(prefix), "JPEG")
        mimeType == "image/webp" -> requireKind(isWebp(prefix), "WebP")
        isSvgMime(mimeType) -> requireKind(isSvg(prefix), "SVG")
        mimeType == "text/css" -> null
        mimeType.startsWith("image/") && prefix.isNotEmpty() -> null
        else -> "unsupported asset Content-Type '$mimeType'"
    }

    private fun expectedKind(url: String): ExpectedKind {
        val path = runCatching { URI(url).path }.getOrNull().orEmpty().lowercase(Locale.ROOT)
        return when {
            path.endsWith(".gif") -> ExpectedKind.GIF
            path.endsWith(".png") -> ExpectedKind.PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ExpectedKind.JPEG
            path.endsWith(".webp") -> ExpectedKind.WEBP
            path.endsWith(".svg") || path.endsWith(".svgz") -> ExpectedKind.SVG
            path.endsWith(".css") -> ExpectedKind.CSS
            path.endsWith(".bmp") || path.endsWith(".ico") || path.endsWith(".avif") ||
                path.endsWith(".tif") || path.endsWith(".tiff") -> ExpectedKind.OTHER_IMAGE
            else -> ExpectedKind.UNKNOWN
        }
    }

    private fun requireKind(valid: Boolean, label: String): String? =
        if (valid) null else "Content-Type/body do not match expected $label"

    private fun looksLikeHtml(prefix: ByteArray): Boolean {
        val text = prefix.toString(Charsets.UTF_8).trimBomAndWhitespace().lowercase(Locale.ROOT)
        return text.startsWith("<!doctype html") || text.startsWith("<html") ||
            text.startsWith("<head") || text.startsWith("<body")
    }

    private fun isGif(bytes: ByteArray): Boolean =
        bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")

    private fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 &&
        bytes[0].toInt() and 0xff == 0x89 && bytes.copyOfRange(1, 4).contentEquals("PNG".toByteArray()) &&
        bytes[4] == 0x0d.toByte() && bytes[5] == 0x0a.toByte() &&
        bytes[6] == 0x1a.toByte() && bytes[7] == 0x0a.toByte()

    private fun isJpeg(bytes: ByteArray): Boolean = bytes.size >= 3 &&
        bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()

    private fun isWebp(bytes: ByteArray): Boolean = bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())

    private fun isSvg(bytes: ByteArray): Boolean =
        svgElement.containsMatchIn(bytes.toString(Charsets.UTF_8).trimBomAndWhitespace())

    private fun isSvgMime(mimeType: String): Boolean = mimeType in setOf(
        "image/svg+xml",
        "application/xml",
        "text/xml"
    )

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && copyOfRange(0, value.length).contentEquals(value.toByteArray())

    private fun String.trimBomAndWhitespace(): String =
        trimStart('\uFEFF', ' ', '\t', '\r', '\n')

    private enum class ExpectedKind { GIF, PNG, JPEG, WEBP, SVG, CSS, OTHER_IMAGE, UNKNOWN }
}

internal object ReadingListAssetUrlExtractor {
    private const val WIKI_BASE_URL = "https://oldschool.runescape.wiki/"
    private val imageSourceAttributes = listOf("src", "data-src", "data-osrs-deferred-src")
    private val imageSetAttributes = listOf("srcset", "data-srcset", "data-osrs-deferred-srcset")
    private val cssUrl = Regex("url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)", RegexOption.IGNORE_CASE)
    private val cssImport = Regex(
        "@import\\s+(?:url\\(\\s*)?(['\"])(.*?)\\1\\s*\\)?",
        RegexOption.IGNORE_CASE
    )
    private val cssComment = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
    private val cssFontFace = Regex(
        "@font-face\\s*\\{[^{}]*\\}",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * Enumerates rendered article artwork without a count cap. The allowlist deliberately avoids
     * navigation, scripts, frames, and playable/interactive media that an explicit article save
     * must not mirror merely because those elements expose a `src` attribute.
     */
    fun extract(html: String, baseUrl: String = WIKI_BASE_URL): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        return linkedSetOf<String>().apply {
            document.select("img, picture > source").forEach { element ->
                addImageElement(element, baseUrl)
            }
            document.select("video[poster]").forEach { video ->
                normalize(video.attr("poster"), baseUrl)?.let(::add)
            }
            document.select("svg image").forEach { image ->
                sequenceOf("href", "xlink:href").forEach { attribute ->
                    normalize(image.attr(attribute), baseUrl)?.let(::add)
                }
            }
            document.select("object[type^=image][data]").forEach { imageObject ->
                normalize(imageObject.attr("data"), baseUrl)?.let(::add)
            }
            document.select("link[rel~=stylesheet][href]").forEach { stylesheet ->
                normalize(stylesheet.attr("href"), baseUrl)?.let(::add)
            }
            document.getAllElements().forEach { element ->
                element.attr("style").takeIf(String::isNotBlank)?.let { style ->
                    addAll(extractCss(style, baseUrl))
                }
            }
            document.select("style").forEach { style ->
                addAll(extractCss(style.data(), baseUrl))
            }
        }.toList()
    }

    /**
     * Infobox and lead artwork from the same allowlist [extract] uses. First-screen warm
     * promotes these ahead of later document-order URLs.
     */
    fun extractInfobox(html: String, baseUrl: String = WIKI_BASE_URL): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        return linkedSetOf<String>().apply {
            document.select(
                "table.infobox, table.main-infobox, .infobox, .collapsible-primary-infobox"
            ).forEach { root ->
                addClusterImages(root, baseUrl)
            }
        }.toList()
    }

    /**
     * Images that occupy the first-viewport slot: the primary infobox including every switcher
     * state and gender render, plus lead images that appear before the first heading.
     *
     * When [eagerOnly] is true (thousand-cuts #7/#14 early warmer): only the authored-default
     * switcher pane, not hidden charge states; one srcset density URL per img; skip
     * `data-osrs-deferred-*` placeholders.
     */
    fun extractFirstViewSlot(
        html: String,
        baseUrl: String = WIKI_BASE_URL,
        eagerOnly: Boolean = false,
        devicePixelRatio: Float = 2f
    ): List<String> {
        val document = Jsoup.parse(html, baseUrl)
        val defaultIndex = authoredDefaultSwitcherIndex(document)
        return linkedSetOf<String>().apply {
            val switcher = document.selectFirst(
                ".infobox-switch, .collapsible-primary-infobox, .switch-infobox"
            ) ?: document.selectFirst("table.infobox, table.main-infobox, .infobox")
            if (switcher != null) {
                addClusterImages(switcher, baseUrl, eagerOnly, devicePixelRatio)
                val resourceClass = switcher.attr("data-resource-class").trim()
                if (resourceClass.isNotEmpty()) {
                    runCatching { document.select(resourceClass) }.getOrNull()?.forEach { pool ->
                        addDefaultOrAllPoolImages(pool, baseUrl, eagerOnly, defaultIndex, devicePixelRatio)
                    }
                }
            }
            document.select(".infobox-switch-resources, [class*=infobox-resources-]").forEach { pool ->
                addDefaultOrAllPoolImages(pool, baseUrl, eagerOnly, defaultIndex, devicePixelRatio)
            }
            if (eagerOnly) {
                document.select("[data-attr-param] [data-attr-index=\"$defaultIndex\"]").forEach { node ->
                    addClusterImages(node, baseUrl, eagerOnly = true, devicePixelRatio)
                }
                var matchedItem = false
                document.select(".switch-infobox .item").forEach { pane ->
                    val itemIndex = pane.attr("data-switch-index").ifBlank { pane.attr("data-attr-index") }
                    if (itemIndex == defaultIndex) {
                        matchedItem = true
                        addClusterImages(pane, baseUrl, eagerOnly = true, devicePixelRatio)
                    }
                }
                if (!matchedItem) {
                    document.selectFirst(".switch-infobox .item")?.let { pane ->
                        addClusterImages(pane, baseUrl, eagerOnly = true, devicePixelRatio)
                    }
                }
            } else {
                document.select("[data-attr-param] [data-attr-index]").forEach { node ->
                    addClusterImages(node, baseUrl)
                }
                document.select(".switch-infobox .item").forEach { pane ->
                    addClusterImages(pane, baseUrl)
                }
            }
            document.select(
                ".infobox-bonuses-image.render-m, .infobox-bonuses-image.render-f"
            ).forEach { render ->
                addClusterImages(render, baseUrl, eagerOnly, devicePixelRatio)
            }
            for (element in document.body().select("img, picture > source, video[poster], h2, .mw-heading")) {
                if (element.tagName() == "h2" || element.classNames().any { it.contains("mw-heading") }) {
                    break
                }
                when (element.tagName()) {
                    "img", "source" -> addImageElement(element, baseUrl, eagerOnly, devicePixelRatio)
                    "video" -> normalize(element.attr("poster"), baseUrl)?.let(::add)
                }
            }
        }.toList()
    }

    private fun authoredDefaultSwitcherIndex(document: org.jsoup.nodes.Document): String {
        val buttons = document.selectFirst(".infobox-buttons")
        val defaultVersion = buttons?.attr("data-default-version")?.trim().orEmpty()
        if (defaultVersion.isNotEmpty()) {
            return defaultVersion
        }
        val selected = document.selectFirst(".button-selected")?.attr("data-switch-index")?.trim().orEmpty()
        if (selected.isNotEmpty()) {
            return selected
        }
        return document.selectFirst("[data-switch-index]")?.attr("data-switch-index")?.trim().orEmpty()
            .ifEmpty { "0" }
    }

    private fun MutableSet<String>.addDefaultOrAllPoolImages(
        pool: org.jsoup.nodes.Element,
        baseUrl: String,
        eagerOnly: Boolean,
        defaultIndex: String,
        devicePixelRatio: Float
    ) {
        if (!eagerOnly) {
            addClusterImages(pool, baseUrl)
            return
        }
        var matched = false
        pool.select("[data-attr-index]").forEach { node ->
            if (node.attr("data-attr-index") == defaultIndex) {
                matched = true
                addClusterImages(node, baseUrl, eagerOnly = true, devicePixelRatio)
            }
        }
        if (!matched) {
            addClusterImages(pool, baseUrl, eagerOnly = true, devicePixelRatio)
        }
    }

    /** Discovers artwork and nested imports in a persisted stylesheet. */
    fun extractCss(css: String, baseUrl: String): List<String> {
        // Explicit reading-list settlement mirrors rendered artwork, not web fonts. A generic
        // url() pass would otherwise queue .woff/.woff2 sources that are outside the bounded
        // article-artwork contract and then honestly (but incorrectly) fail the whole save.
        val artworkCss = css.replace(cssComment, "").replace(cssFontFace, "")
        return linkedSetOf<String>().apply {
            cssUrl.findAll(artworkCss).forEach { match ->
                normalize(match.groupValues[2], baseUrl)?.let(::add)
            }
            cssImport.findAll(artworkCss).forEach { match ->
                normalize(match.groupValues[2], baseUrl)?.let(::add)
            }
        }.toList()
    }

    private fun MutableSet<String>.addClusterImages(
        root: org.jsoup.nodes.Element,
        baseUrl: String,
        eagerOnly: Boolean = false,
        devicePixelRatio: Float = 2f
    ) {
        root.select("img, picture > source").forEach { element ->
            addImageElement(element, baseUrl, eagerOnly, devicePixelRatio)
        }
        root.select("svg image").forEach { image ->
            sequenceOf("href", "xlink:href").forEach { attribute ->
                normalize(image.attr(attribute), baseUrl)?.let(::add)
            }
        }
        root.select("video[poster]").forEach { video ->
            normalize(video.attr("poster"), baseUrl)?.let(::add)
        }
        root.attr("style").takeIf(String::isNotBlank)?.let { style ->
            addAll(extractCss(style, baseUrl))
        }
    }

    private fun MutableSet<String>.addImageElement(
        element: org.jsoup.nodes.Element,
        baseUrl: String,
        eagerOnly: Boolean = false,
        devicePixelRatio: Float = 2f
    ) {
        if (eagerOnly) {
            val src = element.attr("src")
            if (src.startsWith("data:", ignoreCase = true) && element.hasAttr("data-osrs-deferred-src")) {
                return
            }
            val chosen = SrcsetParser.choose(
                src = src.takeIf { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) },
                srcset = element.attr("srcset"),
                widthPx = element.attr("width").toIntOrNull(),
                devicePixelRatio = devicePixelRatio
            )
            chosen?.let { normalize(it, baseUrl)?.let(::add) }
            return
        }
        imageSourceAttributes.forEach { attribute ->
            normalize(element.attr(attribute), baseUrl)?.let(::add)
        }
        imageSetAttributes.forEach { attribute ->
            SrcsetParser.urls(element.attr(attribute)).forEach { raw ->
                normalize(raw, baseUrl)?.let(::add)
            }
        }
    }

    private fun normalize(rawUrl: String, baseUrl: String): String? {
        val trimmed = rawUrl.trim().trim('"', '\'')
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith('#')) return null
        if (
            trimmed.startsWith("data:", ignoreCase = true) ||
            trimmed.startsWith("blob:", ignoreCase = true) ||
            trimmed.startsWith("file:", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("javascript:", ignoreCase = true)
        ) return null
        return runCatching {
            val absolute = when {
                trimmed.startsWith("//") -> "https:$trimmed"
                else -> URI(baseUrl).resolve(trimmed).toString()
            }
            // Fragments select a client-side view into one response and are never part of HTTP
            // origin identity. Strip them before dedupe/persistence while preserving path case and
            // the complete query string.
            val networkUrl = absolute.substringBefore('#')
            URI(networkUrl).takeIf { it.scheme == "http" || it.scheme == "https" }
                ?.takeUnless { it.host.equals("appassets.androidplatform.net", ignoreCase = true) }
                ?.toString()
        }.getOrNull()
    }
}

internal object ReadingListAssetOwnership {
    fun contains(encoded: String, pageId: Long): Boolean = pageId in decode(encoded)

    fun merge(existing: String, added: String): String = encode(decode(existing) + decode(added))

    fun add(existing: String, pageId: Long): String = encode(decode(existing) + pageId)

    fun remove(existing: String, pageId: Long): String = encode(decode(existing) - pageId)

    private fun decode(encoded: String): LinkedHashSet<Long> = encoded
        .trim('|')
        .split('|')
        .mapNotNull { it.toLongOrNull() }
        .toCollection(linkedSetOf())

    private fun encode(ids: Collection<Long>): String = if (ids.isEmpty()) {
        ""
    } else {
        ids.joinToString(separator = "|", prefix = "|", postfix = "|")
    }
}

internal data class ReadingListAssetPersistenceResult(
    val requiredCount: Int,
    val persistedCount: Int,
    val failedUrls: List<String>
) {
    val isComplete: Boolean get() = failedUrls.isEmpty() && persistedCount == requiredCount
}

internal fun interface ReadingListAssetFetcher {
    suspend fun fetchAndPersist(url: String, readingListPageId: Long): Boolean

    /** Returns CSS bytes from durable storage so nested imports/artwork can join this same save. */
    suspend fun readPersistedCss(url: String): ReadingListPersistedCss? = null
}

internal sealed interface ReadingListPersistedCss {
    data class Content(val text: String) : ReadingListPersistedCss
    data object TooLarge : ReadingListPersistedCss
}

/** Durable, exhaustive asset phase used only by an explicit reading-list save. */
internal class ReadingListOfflineAssetSaver(
    private val fetcher: ReadingListAssetFetcher,
    private val maxConcurrent: Int = 6
) {
    init {
        require(maxConcurrent > 0)
    }

    suspend fun persistAll(
        readingListPageId: Long,
        html: String,
        onProgress: (suspend (persisted: Int, required: Int) -> Unit)? = null
    ): ReadingListAssetPersistenceResult = coroutineScope {
        val discovered = ReadingListAssetUrlExtractor.extract(html).toCollection(linkedSetOf())
        var pending = discovered.map { url -> url to 0 }
        val permits = Semaphore(maxConcurrent)
        val persisted = linkedSetOf<String>()
        val failed = linkedSetOf<String>()
        var parsedCssCharacters = 0L
        var cssDependencyCount = 0
        while (pending.isNotEmpty()) {
            val outcomes = pending.map { (url, depth) ->
                async {
                    permits.withPermit {
                        currentCoroutineContext().ensureActive()
                        try {
                            val didPersist = fetcher.fetchAndPersist(url, readingListPageId)
                            val css = if (didPersist) fetcher.readPersistedCss(url) else null
                            CssFetchOutcome(url, depth, didPersist, css)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            CssFetchOutcome(url, depth, false, null)
                        }
                    }
                }
            }.awaitAll()
            val next = linkedMapOf<String, Int>()
            outcomes.forEach { outcome ->
                val url = outcome.url
                val didPersist = outcome.didPersist
                if (didPersist) persisted += url else failed += url
                when (val css = outcome.css) {
                    null -> Unit
                    ReadingListPersistedCss.TooLarge -> failed += url
                    is ReadingListPersistedCss.Content -> {
                        if (css.text.length > MAX_CSS_CHARACTERS_PER_STYLESHEET) {
                            failed += url
                            return@forEach
                        }
                        parsedCssCharacters += css.text.length
                        if (parsedCssCharacters > MAX_TOTAL_CSS_CHARACTERS) {
                            failed += url
                            return@forEach
                        }
                        val dependencies = ReadingListAssetUrlExtractor.extractCss(css.text, url)
                        if (dependencies.isNotEmpty() && outcome.depth >= MAX_CSS_DEPENDENCY_DEPTH) {
                            failed += url
                            return@forEach
                        }
                        val newDependencyCount = dependencies.count { it !in discovered }
                        if (cssDependencyCount + newDependencyCount > MAX_CSS_DISCOVERED_DEPENDENCIES) {
                            failed += url
                            return@forEach
                        }
                        dependencies.forEach { dependency ->
                            if (discovered.add(dependency)) {
                                cssDependencyCount += 1
                                next[dependency] = outcome.depth + 1
                            }
                        }
                    }
                }
            }
            pending = next.map { it.key to it.value }
            onProgress?.invoke(persisted.size, discovered.size.coerceAtLeast(1))
        }
        ReadingListAssetPersistenceResult(
            requiredCount = discovered.size,
            persistedCount = persisted.size,
            failedUrls = failed.toList()
        )
    }

    private data class CssFetchOutcome(
        val url: String,
        val depth: Int,
        val didPersist: Boolean,
        val css: ReadingListPersistedCss?
    )

    internal companion object {
        const val MAX_CSS_DEPENDENCY_DEPTH = 8
        const val MAX_CSS_CHARACTERS_PER_STYLESHEET = 1_000_000
        const val MAX_TOTAL_CSS_CHARACTERS = 4_000_000L
        const val MAX_CSS_DISCOVERED_DEPENDENCIES = 4_096
    }
}

internal class OkHttpReadingListAssetFetcher(
    private val context: Context,
    private val client: OkHttpClient,
    private val offlineObjectDao: OfflineObjectDao,
    private val language: String = "en"
) : ReadingListAssetFetcher {
    override suspend fun fetchAndPersist(url: String, readingListPageId: Long): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Accept-Language", language)
            .header("X-Offline-Save", "readinglist")
            .header("X-Offline-Save-PageLibIds", "|$readingListPageId|")
            .tag(ReadingListAssetRequestMarker::class.java, ReadingListAssetRequestMarker)
            .build()
        try {
            if (!executeAndDrainCancellable(request)) return false
        } catch (_: ReadingListAssetValidationException) {
            // A prior version could have cached the same captive/error body. Reuse only if that
            // durable object independently passes today's contract; otherwise evict it below.
        }

        val saved = offlineObjectDao.findByUrlAndLangAndSaveType(
            url,
            language,
            OfflineObject.SAVE_TYPE_READING_LIST
        ) ?: return false
        val storageDir = File(context.filesDir, ReadingListOfflineAssetResolver.STORAGE_DIRECTORY)
        val contentFile = File(storageDir, saved.path + ReadingListOfflineAssetResolver.CONTENT_SUFFIX)
        val metadataFile = File(storageDir, saved.path + ReadingListOfflineAssetResolver.METADATA_SUFFIX)
        val invalidReason = if (saved.status != OfflineObject.STATUS_SAVED) {
            "offline object is not saved"
        } else {
            ReadingListAssetResponseValidator.invalidReason(
                url = url,
                contentType = ReadingListAssetResponseValidator.contentTypeFromMetadata(metadataFile),
                contentFile = contentFile
            )
        }
        if (invalidReason != null) {
            offlineObjectDao.deleteFilesForObject(saved, context)
            offlineObjectDao.deleteOfflineObjectQuery(saved.id)
            return false
        }

        if (!ReadingListAssetOwnership.contains(saved.usedByStr, readingListPageId)) {
            offlineObjectDao.updateOfflineObject(
                saved.copy(usedByStr = ReadingListAssetOwnership.add(saved.usedByStr, readingListPageId))
            )
        }
        return true
    }

    override suspend fun readPersistedCss(url: String): ReadingListPersistedCss? {
        val saved = offlineObjectDao.findByUrlAndLangAndSaveType(
            url,
            language,
            OfflineObject.SAVE_TYPE_READING_LIST
        ) ?: return null
        val storageDir = File(context.filesDir, ReadingListOfflineAssetResolver.STORAGE_DIRECTORY)
        val metadataFile = File(storageDir, saved.path + ReadingListOfflineAssetResolver.METADATA_SUFFIX)
        val contentFile = File(storageDir, saved.path + ReadingListOfflineAssetResolver.CONTENT_SUFFIX)
        if (!metadataFile.isFile || !contentFile.isFile) return null
        val contentType = metadataFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
        }.orEmpty()
        val isCss = contentType.substringBefore(';').trim().equals("text/css", ignoreCase = true) ||
            runCatching { URI(url).path.endsWith(".css", ignoreCase = true) }.getOrDefault(false)
        if (!isCss) return null
        if (contentFile.length() > ReadingListOfflineAssetSaver.MAX_CSS_CHARACTERS_PER_STYLESHEET) {
            return ReadingListPersistedCss.TooLarge
        }
        return ReadingListPersistedCss.Content(contentFile.readText())
    }

    private suspend fun executeAndDrainCancellable(request: Request): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!response.isSuccessful || response.body == null) {
                                if (continuation.isActive) continuation.resume(false)
                                return
                            }
                            val source = response.body!!.source()
                            val scratch = Buffer()
                            while (continuation.isActive) {
                                val count = source.read(scratch, 8_192L)
                                if (count == -1L) break
                                scratch.clear()
                            }
                            if (continuation.isActive) continuation.resume(true)
                        }
                    } catch (failure: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            })
        }
}

internal data class ReadingListOfflineAsset(
    val mimeType: String,
    val encoding: String?,
    val stream: InputStream
)

/** Reopens interceptor-owned reading-list bytes, including after process recreation. */
internal class ReadingListOfflineAssetResolver internal constructor(
    private val storageDir: File,
    private val lookup: (String, String) -> OfflineObject?
) {
    constructor(context: Context, offlineObjectDao: OfflineObjectDao) : this(
        File(context.filesDir, STORAGE_DIRECTORY),
        { url, lang ->
            offlineObjectDao.findByUrlAndLangAndSaveType(
                url,
                lang,
                OfflineObject.SAVE_TYPE_READING_LIST
            )
        }
    )

    fun open(url: String, language: String = "en"): ReadingListOfflineAsset? {
        val saved = lookup(url, language)?.takeIf {
            it.saveType == OfflineObject.SAVE_TYPE_READING_LIST &&
                it.status == OfflineObject.STATUS_SAVED &&
                it.usedByStr.isNotBlank()
        } ?: return null
        val contentFile = File(storageDir, saved.path + CONTENT_SUFFIX).takeIf(File::isFile)
            ?: return null
        val metadataFile = File(storageDir, saved.path + METADATA_SUFFIX).takeIf(File::isFile)
            ?: return null
        val contentType = metadataFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
        }.orEmpty()
        val mimeType = contentType.substringBefore(';').trim().ifBlank { "application/octet-stream" }
        val encoding = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
            .find(contentType)
            ?.groupValues
            ?.getOrNull(1)
        return ReadingListOfflineAsset(mimeType, encoding, FileInputStream(contentFile))
    }

    companion object {
        internal const val STORAGE_DIRECTORY = "offline_pages_rl"
        internal const val METADATA_SUFFIX = ".0"
        internal const val CONTENT_SUFFIX = ".1"
    }
}
