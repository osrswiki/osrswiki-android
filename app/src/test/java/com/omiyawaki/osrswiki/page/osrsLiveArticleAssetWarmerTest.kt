package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.savedpages.ReadingListAssetUrlExtractor
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsLiveArticleAssetWarmerTest {
    private val infoboxUrl = "https://oldschool.runescape.wiki/images/infobox.png"
    private val firstScreenHtml = buildString {
        append("""<table class="infobox"><tr><td><img src="/images/infobox.png"></td></tr></table>""")
        repeat(30) { index ->
            append("""<img src="/images/row-${index + 1}.png">""")
        }
    }

    @Test
    fun partitionPutsInfoboxAndFirstScreenAheadOfRemainder() {
        val required = ReadingListAssetUrlExtractor.extract(firstScreenHtml)
        val infobox = ReadingListAssetUrlExtractor.extractInfobox(firstScreenHtml)
        val plan = osrsLiveArticleAssetPlan.partition(required, infobox, firstScreenLimit = 24)

        assertTrue(infoboxUrl in infobox)
        assertEquals(infoboxUrl, plan.high.first())
        assertTrue(plan.high.contains("https://oldschool.runescape.wiki/images/row-1.png"))
        assertTrue(plan.high.contains("https://oldschool.runescape.wiki/images/row-23.png"))
        assertTrue("https://oldschool.runescape.wiki/images/row-30.png" in plan.low)
        assertFalse("https://oldschool.runescape.wiki/images/row-30.png" in plan.high)
        assertEquals(required.toSet(), (plan.high + plan.low).toSet())
    }

    @Test
    fun queueSkipsCachedAndPromoteMovesLowToHigh() {
        val cached = setOf("https://oldschool.runescape.wiki/images/cached.png")
        val queue = osrsLiveArticleAssetQueue(isCached = { it in cached })
        queue.load(
            highUrls = listOf("https://oldschool.runescape.wiki/images/lead.png"),
            lowUrls = listOf(
                "https://oldschool.runescape.wiki/images/cached.png",
                "https://oldschool.runescape.wiki/images/rest.png",
                "https://oldschool.runescape.wiki/images/near.png"
            )
        )

        queue.promote(listOf("https://oldschool.runescape.wiki/images/near.png"))
        assertEquals("https://oldschool.runescape.wiki/images/near.png", queue.takeHigh())
        assertEquals("https://oldschool.runescape.wiki/images/lead.png", queue.takeHigh())
        assertEquals("https://oldschool.runescape.wiki/images/rest.png", queue.takeLow())
        assertEquals(null, queue.takeLow())
    }

    @Test
    fun warmerFetchesFirstScreenBeforeRemainderAndSkipsCached() = runTest {
        val fetched = CopyOnWriteArrayList<String>()
        val warmer = osrsLiveArticleAssetWarmer(
            isCached = { it.endsWith("row-2.png") },
            fetch = { url ->
                fetched.add(url)
                delay(1)
            },
            highConcurrency = 1,
            lowConcurrency = 0
        )

        warmer.warm(firstScreenHtml)

        assertTrue(fetched.first() == infoboxUrl)
        assertTrue(fetched.indexOf("https://oldschool.runescape.wiki/images/row-1.png") <
            fetched.indexOf("https://oldschool.runescape.wiki/images/row-30.png"))
        assertFalse(fetched.any { it.endsWith("row-2.png") })
    }

    @Test
    fun cancelDropsRemainingWork() = runTest {
        val fetched = CopyOnWriteArrayList<String>()
        val started = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val warmer = osrsLiveArticleAssetWarmer(
            fetch = { url ->
                fetched.add(url)
                if (fetched.size == 1) {
                    started.complete(Unit)
                    hold.await()
                }
            },
            highConcurrency = 1,
            lowConcurrency = 0
        )

        val job = launch { warmer.warm(firstScreenHtml) }
        started.await()
        job.cancelAndJoin()

        assertEquals(1, fetched.size)
    }

    @Test
    fun lookAheadScriptPrefetchesWithoutAssigningImgSrc() {
        val shared = repoFile("shared/js/live_article_asset_warm.js").readText()
        val android = repoFile("platforms/android/app/src/main/assets/web/live_article_asset_warm.js").readText()
        assertEquals(shared, android)
        assertTrue(shared.contains("warmNearViewportAssets"))
        assertTrue(shared.contains("osrsLiveAssetWarm"))
        assertTrue(shared.contains("rootMargin: '100% 0px'"))
        assertTrue(shared.contains("data-osrs-deferred-src"))
        assertFalse(shared.contains(".src ="))
        assertFalse(shared.contains("setAttribute('src'"))
    }

    private fun repoFile(path: String): java.io.File {
        return listOf(
            java.io.File(path),
            java.io.File("..", path),
            java.io.File("../..", path),
            java.io.File("../../..", path),
            java.io.File("../../../..", path)
        ).firstOrNull { it.isFile } ?: error("Could not find repo file: $path")
    }
}
