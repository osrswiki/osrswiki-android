package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.savedpages.ReadingListAssetUrlExtractor
import com.omiyawaki.osrswiki.savedpages.osrsArticleViewAssetStore
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class osrsLiveArticleAssetWarmer(
    private val isCached: (String) -> Boolean = { url -> osrsArticleViewAssetStore.get(url) != null },
    private val fetch: suspend (String) -> Unit = { url ->
        osrsArticleViewAssetStore.fetchAndCache(url)
        Unit
    },
    private val highConcurrency: Int = 4,
    private val lowConcurrency: Int = 2
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
        val required = ReadingListAssetUrlExtractor.extract(html, baseUrl)
        val infobox = ReadingListAssetUrlExtractor.extractInfobox(html, baseUrl)
        val plan = osrsLiveArticleAssetPlan.partition(required, infobox)
        queue.load(plan.high, plan.low)
        L.d(
            "osrsLiveAssetWarm: start required=${required.size} high=${plan.high.size} low=${plan.low.size}"
        )
        coroutineScope {
            repeat(highConcurrency.coerceAtLeast(0)) {
                launch { drain(preferHigh = true) }
            }
            repeat(lowConcurrency.coerceAtLeast(0)) {
                launch { drain(preferHigh = false) }
            }
        }
        L.d("osrsLiveAssetWarm: done")
    }

    private suspend fun drain(preferHigh: Boolean) {
        while (currentCoroutineContext().isActive) {
            currentCoroutineContext().ensureActive()
            val url = if (preferHigh) {
                queue.takeHigh() ?: queue.takeLow()
            } else {
                queue.takeLow()
            }
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
