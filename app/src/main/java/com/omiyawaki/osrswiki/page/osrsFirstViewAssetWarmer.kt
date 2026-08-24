package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.savedpages.ReadingListAssetUrlExtractor
import com.omiyawaki.osrswiki.savedpages.osrsArticleViewAssetStore
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class osrsFirstViewAssetWarmer(
    private val isCached: (String) -> Boolean = { url -> osrsArticleViewAssetStore.get(url) != null },
    private val fetch: suspend (String) -> Unit = { url ->
        osrsArticleViewAssetStore.fetchAndCache(url)
        Unit
    },
    private val concurrency: Int = 4
) {
    val queue = osrsLiveArticleAssetQueue(isCached)

    fun promote(urls: Collection<String>) {
        queue.promote(urls)
    }

    suspend fun warm(
        html: String,
        baseUrl: String = "https://oldschool.runescape.wiki/"
    ) {
        if (html.isBlank()) {
            return
        }
        val firstView = ReadingListAssetUrlExtractor.extractFirstViewSlot(html, baseUrl)
        val high = if (Prefs.narrowFirstViewportPaintedSet) {
            // Slice 1: slot extract only. Do not Jsoup-extract the full document
            // URL list on the early path; remainder warm still does that after reveal.
            firstView.take(osrsLiveArticleAssetPlan.FIRST_VIEW_CAP)
        } else {
            val required = ReadingListAssetUrlExtractor.extract(html, baseUrl)
            osrsLiveArticleAssetPlan.partition(required, firstView).high
        }
        queue.load(high, emptyList())
        L.d("osrsFirstViewWarm: start count=${high.size}")
        coroutineScope {
            repeat(concurrency.coerceAtLeast(0)) {
                launch { drain() }
            }
        }
        L.d("osrsFirstViewWarm: done count=${high.size}")
    }

    private suspend fun drain() {
        while (currentCoroutineContext().isActive) {
            currentCoroutineContext().ensureActive()
            val url = queue.takeHigh()
            if (url == null) {
                if (queue.isIdle) {
                    return
                }
                delay(50)
                continue
            }
            try {
                currentCoroutineContext().ensureActive()
                fetch(url)
            } finally {
                queue.complete(url)
            }
        }
    }
}
