package com.omiyawaki.osrswiki.network

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Per-item GE timeseries cache: memory plus on-disk, ~30 minutes, no refresh.
 */
object osrsGeChartPriceCache {
    private data class Entry(val expiresAtMs: Long, val body: String)

    private val memory = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val lock = Any()
    private val itemIdRegex = Regex("""data-itemid=["'](\d+)["']""")

    fun timeseriesUrl(itemId: String): String =
        "https://prices.runescape.wiki/api/v2/osrs/timeseries?lookback=1y&id=${itemId.trim()}"

    fun itemIdsIn(html: String): List<String> =
        itemIdRegex.findAll(html).map { it.groupValues[1] }.distinct().toList()

    fun readMemory(url: String, nowMs: Long = System.currentTimeMillis()): String? {
        val key = cacheKey(url)
        synchronized(lock) {
            memory[key]?.let { hit ->
                if (nowMs < hit.expiresAtMs && hit.body.isNotEmpty()) {
                    return hit.body
                }
                memory.remove(key)
            }
        }
        return null
    }

    fun prefetchDiskAsync(context: Context?, itemIds: List<String>) {
        val app = context?.applicationContext ?: return
        if (itemIds.isEmpty()) {
            return
        }
        Thread({
            itemIds.forEach { id ->
                read(app, timeseriesUrl(id))
            }
        }, "osrs-ge-chart-prefetch").apply {
            isDaemon = true
        }.start()
    }

    fun memoryPrefetchScript(itemIds: List<String>): String {
        val parts = itemIds.mapNotNull { id ->
            val raw = readMemory(timeseriesUrl(id))?.trim().orEmpty()
            if (raw.isEmpty()) {
                null
            } else {
                "\"$id\":$raw"
            }
        }
        return if (parts.isEmpty()) {
            "window.__osrsGeChartPrefetch=window.__osrsGeChartPrefetch||{};"
        } else {
            "window.__osrsGeChartPrefetch=Object.assign(window.__osrsGeChartPrefetch||{},{${parts.joinToString(",")}});"
        }
    }

    fun read(context: Context, url: String, nowMs: Long = System.currentTimeMillis()): String? {
        readMemory(url, nowMs)?.let { return it }
        val key = cacheKey(url)
        val file = fileFor(context, key)
        if (!file.isFile) {
            return null
        }
        return runCatching {
            val raw = file.readText(Charsets.UTF_8)
            val split = raw.indexOf('\n')
            if (split <= 0) {
                file.delete()
                return null
            }
            val expiresAtMs = raw.substring(0, split).toLongOrNull() ?: return null
            val body = raw.substring(split + 1)
            if (nowMs >= expiresAtMs || body.isEmpty()) {
                file.delete()
                return null
            }
            synchronized(lock) {
                memory[key] = Entry(expiresAtMs, body)
            }
            body
        }.getOrNull()
    }

    fun putMemory(url: String, body: String, nowMs: Long = System.currentTimeMillis()) {
        if (body.isEmpty()) {
            return
        }
        val key = cacheKey(url)
        val expiresAtMs = nowMs + osrsWikiApiStewardship.GE_CHART_CACHE_TTL_MS
        synchronized(lock) {
            memory[key] = Entry(expiresAtMs, body)
        }
    }

    fun write(context: Context, url: String, body: String, nowMs: Long = System.currentTimeMillis()) {
        putMemory(url, body, nowMs)
        if (body.isEmpty()) {
            return
        }
        val key = cacheKey(url)
        val expiresAtMs = nowMs + osrsWikiApiStewardship.GE_CHART_CACHE_TTL_MS
        runCatching {
            val file = fileFor(context, key)
            file.parentFile?.mkdirs()
            file.writeText("$expiresAtMs\n$body", Charsets.UTF_8)
        }
    }

    private fun fileFor(context: Context, key: String): File {
        return File(File(context.cacheDir, "osrs-ge-charts"), "$key.json")
    }

    private fun cacheKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
