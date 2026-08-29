package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import android.net.Uri
import android.util.Log
import com.omiyawaki.osrswiki.page.AppWebViewClient
import com.omiyawaki.osrswiki.page.osrsWikiWebViewUrl
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class osrsSessionAsset(
    val body: ByteArray,
    val contentType: String
)

internal fun interface osrsSessionAssetLookup {
    fun get(url: String): osrsSessionAsset?
}

/**
 * Disk-backed URL-keyed bytes captured while an article is on screen. First save copies
 * exact-URL hits into the snapshot generation and GETs only the misses.
 */
internal object osrsArticleViewAssetStore : osrsSessionAssetLookup {
    private const val TAG = "osrsViewAssetStore"
    private const val DIRECTORY = "osrs_article_view_assets"
    private const val MAX_BYTES = 64L * 1024L * 1024L
    private val imageOrCssExtension = Regex(
        """\.(?:apng|avif|bmp|css|gif|ico|jpe?g|png|svgz?|tiff?|webp)$""",
        RegexOption.IGNORE_CASE
    )

    @Volatile
    private var root: File? = null
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    fun install(context: Context) {
        if (root != null) return
        val directory = File(context.applicationContext.cacheDir, DIRECTORY)
        directory.mkdirs()
        root = directory
    }

    fun canonicalize(url: String): String? {
        val rewritten = AppWebViewClient.normalizeWikiStaticUrl(url).substringBefore('#')
        val uri = runCatching { Uri.parse(rewritten) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.equals(osrsWikiWebViewUrl.LOCAL_ASSET_HOST, ignoreCase = true)) return null
        return rewritten
    }

    fun isEligible(url: String): Boolean {
        val canonical = canonicalize(url) ?: return false
        val uri = Uri.parse(canonical)
        val host = uri.host?.lowercase() ?: return false
        if (!host.contains("runescape.wiki")) return false
        val path = uri.path.orEmpty().lowercase()
        if (path.contains("api.php") || path.contains("load.php")) return false
        return path.startsWith("/images/") || imageOrCssExtension.containsMatchIn(path)
    }

    override fun get(url: String): osrsSessionAsset? {
        val canonical = canonicalize(url) ?: return null
        val directory = root ?: return null
        val bodyFile = bodyFile(directory, canonical)
        val metaFile = metaFile(directory, canonical)
        if (!bodyFile.isFile || bodyFile.length() <= 0L) return null
        val contentType = metaFile.takeIf { it.isFile }?.readText(StandardCharsets.UTF_8)
            ?.trim()
            .orEmpty()
            .ifBlank { "application/octet-stream" }
        return runCatching {
            osrsSessionAsset(body = bodyFile.readBytes(), contentType = contentType)
        }.getOrNull()
    }

    fun put(url: String, body: ByteArray, contentType: String) {
        val canonical = canonicalize(url) ?: return
        val directory = root ?: return
        if (body.isEmpty()) return
        directory.mkdirs()
        val bodyFile = bodyFile(directory, canonical)
        val metaFile = metaFile(directory, canonical)
        val bodyTemp = File(directory, "${bodyFile.name}.tmp")
        val metaTemp = File(directory, "${metaFile.name}.tmp")
        try {
            bodyTemp.writeBytes(body)
            metaTemp.writeText(contentType.substringBefore('\n').trim(), StandardCharsets.UTF_8)
            if (!bodyTemp.renameTo(bodyFile)) {
                bodyFile.delete()
                check(bodyTemp.renameTo(bodyFile))
            }
            if (!metaTemp.renameTo(metaFile)) {
                metaFile.delete()
                check(metaTemp.renameTo(metaFile))
            }
            trimToLimit(directory)
        } catch (failure: Throwable) {
            bodyTemp.delete()
            metaTemp.delete()
            Log.w(TAG, "Could not persist session asset for $canonical", failure)
        }
    }

    fun fetchAndCache(url: String): osrsSessionAsset? {
        val canonical = canonicalize(url) ?: return null
        get(canonical)?.let { return it }
        return try {
            val response = client.newCall(Request.Builder().url(canonical).build()).execute()
            response.use {
                if (!response.isSuccessful) return null
                val body = response.body?.bytes() ?: return null
                if (body.isEmpty()) return null
                val contentType = response.header("Content-Type").orEmpty()
                    .ifBlank { "application/octet-stream" }
                put(canonical, body, contentType)
                osrsSessionAsset(body = body, contentType = contentType)
            }
        } catch (failure: Throwable) {
            Log.d(TAG, "Session fetch missed $canonical: ${failure.message}")
            null
        }
    }

    fun openWebResponse(
        url: String,
        requestHeaders: Map<String, String>? = null
    ): android.webkit.WebResourceResponse? {
        val asset = fetchAndCache(url) ?: return null
        val mime = asset.contentType.substringBefore(';').trim()
            .ifBlank { "application/octet-stream" }
        val total = asset.body.size
        // Chromium only marks media seekable when the server honors byte ranges.
        // A plain 200 here left seekable=[0,0]: every audio seek snapped to 0.
        val rangeSpec = requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value
        val range = parseByteRange(rangeSpec, total)
        if (range != null) {
            val (start, endInclusive) = range
            val length = endInclusive - start + 1
            return android.webkit.WebResourceResponse(
                mime,
                "UTF-8",
                206,
                "Partial Content",
                mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes $start-$endInclusive/$total",
                    "Content-Length" to length.toString()
                ),
                ByteArrayInputStream(asset.body, start, length)
            )
        }
        return android.webkit.WebResourceResponse(
            mime,
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to total.toString()
            ),
            ByteArrayInputStream(asset.body)
        )
    }

    /** Returns inclusive [start, end] within [0, total) for an RFC 7233 single byte range. */
    internal fun parseByteRange(spec: String?, total: Int): Pair<Int, Int>? {
        if (spec == null || total <= 0) return null
        val match = Regex("""^\s*bytes=(\d*)-(\d*)\s*$""").find(spec) ?: return null
        val startText = match.groupValues[1]
        val endText = match.groupValues[2]
        if (startText.isEmpty() && endText.isEmpty()) return null
        if (startText.isEmpty()) {
            val suffixLength = endText.toLongOrNull() ?: return null
            if (suffixLength <= 0L) return null
            val start = (total - suffixLength).coerceAtLeast(0L).toInt()
            return start to total - 1
        }
        val start = startText.toLongOrNull() ?: return null
        if (start >= total) return null
        val endInclusive = endText.toLongOrNull()
            ?.coerceAtMost((total - 1).toLong())
            ?: (total - 1).toLong()
        if (endInclusive < start) return null
        return start.toInt() to endInclusive.toInt()
    }

    @Synchronized
    fun clear() {
        root?.deleteRecursively()
        root?.mkdirs()
    }

    private fun bodyFile(directory: File, url: String) = File(directory, "${hashUrl(url)}.1")

    private fun metaFile(directory: File, url: String) = File(directory, "${hashUrl(url)}.0")

    private fun hashUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(url.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun trimToLimit(directory: File) {
        val bodies = directory.listFiles { file -> file.isFile && file.name.endsWith(".1") }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = bodies.sumOf { it.length() }
        for (body in bodies) {
            if (total <= MAX_BYTES) return
            val meta = File(directory, body.name.removeSuffix(".1") + ".0")
            total -= body.length()
            body.delete()
            meta.delete()
        }
    }
}
