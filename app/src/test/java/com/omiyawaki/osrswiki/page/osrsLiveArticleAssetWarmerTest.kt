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
    private val leadUrl = "https://oldschool.runescape.wiki/images/lead.png"
    private val belowFoldUrl = "https://oldschool.runescape.wiki/images/below-fold.png"
    private val firstScreenHtml = """
        <table class="infobox"><tr><td><img src="/images/infobox.png"></td></tr></table>
        <p><img src="/images/lead.png"></p>
        <h2>Combat stats</h2>
        ${List(30) { index -> """<img src="/images/row-${index + 1}.png">""" }.joinToString("")}
        <img src="/images/below-fold.png">
    """.trimIndent()
    private val gloryHtml = """
        <table class="infobox infobox-switch" data-resource-class=".infobox-resources-glory">
          <tr><td>
            <img src="/images/glory-default.png">
            <span class="infobox-bonuses-image render-m"><img src="/images/glory-m.png"></span>
            <span class="infobox-bonuses-image render-f"><img src="/images/glory-f.png"></span>
          </td></tr>
        </table>
        <p>Lead text <img src="/images/glory-lead.png"></p>
        <h2>Combat stats</h2>
        <img src="/images/below-fold.png">
        <div class="infobox-resources-glory infobox-switch-resources">
          <div data-attr-param="version">
            <div data-attr-index="0"><img src="/images/glory-4.png"></div>
            <div data-attr-index="1"><img src="/images/glory-3.png"></div>
            <div data-attr-index="2"><img src="/images/glory-uncharged.png"></div>
          </div>
        </div>
    """.trimIndent()

    @Test
    fun partitionPutsFirstViewSlotAheadOfRemainder() {
        val required = ReadingListAssetUrlExtractor.extract(firstScreenHtml)
        val firstView = ReadingListAssetUrlExtractor.extractFirstViewSlot(firstScreenHtml)
        val plan = osrsLiveArticleAssetPlan.partition(required, firstView)

        assertTrue(infoboxUrl in firstView)
        assertTrue(leadUrl in firstView)
        assertFalse(belowFoldUrl in firstView)
        assertEquals(infoboxUrl, plan.high.first())
        assertTrue(leadUrl in plan.high)
        assertTrue("https://oldschool.runescape.wiki/images/row-1.png" in plan.low)
        assertTrue("https://oldschool.runescape.wiki/images/row-30.png" in plan.low)
        assertFalse(belowFoldUrl in plan.high)
        assertEquals(required.toSet(), (plan.high + plan.low).toSet())
        assertTrue(plan.high.size <= osrsLiveArticleAssetPlan.FIRST_VIEW_CAP)
    }

    @Test
    fun firstViewSlotIncludesSwitcherPoolAndGenderRenders() {
        val firstView = ReadingListAssetUrlExtractor.extractFirstViewSlot(gloryHtml)
        val required = ReadingListAssetUrlExtractor.extract(gloryHtml)
        val plan = osrsLiveArticleAssetPlan.partition(required, firstView)

        listOf(
            "https://oldschool.runescape.wiki/images/glory-default.png",
            "https://oldschool.runescape.wiki/images/glory-m.png",
            "https://oldschool.runescape.wiki/images/glory-f.png",
            "https://oldschool.runescape.wiki/images/glory-4.png",
            "https://oldschool.runescape.wiki/images/glory-3.png",
            "https://oldschool.runescape.wiki/images/glory-uncharged.png",
            "https://oldschool.runescape.wiki/images/glory-lead.png"
        ).forEach { url ->
            assertTrue("$url missing from first-view slot", url in firstView)
            assertTrue("$url missing from high queue", url in plan.high)
        }
        assertFalse(belowFoldUrl in firstView)
        assertFalse(belowFoldUrl in plan.high)
        assertTrue(belowFoldUrl in plan.low)
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
        assertTrue(fetched.indexOf(leadUrl) < fetched.indexOf("https://oldschool.runescape.wiki/images/row-30.png"))
        assertFalse(fetched.any { it.endsWith("row-2.png") })
    }

    @Test
    fun firstViewWarmerFetchesSlotOnlyAndCancelDropsWork() = runTest {
        val fetched = CopyOnWriteArrayList<String>()
        val started = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        val warmer = osrsFirstViewAssetWarmer(
            fetch = { url ->
                fetched.add(url)
                if (fetched.size == 1) {
                    started.complete(Unit)
                    hold.await()
                }
            },
            concurrency = 1
        )

        val job = launch { warmer.warm(gloryHtml) }
        started.await()
        job.cancelAndJoin()
        assertEquals(1, fetched.size)
        assertFalse(fetched.contains(belowFoldUrl))

        val completed = CopyOnWriteArrayList<String>()
        osrsFirstViewAssetWarmer(
            fetch = { url -> completed.add(url) },
            concurrency = 1
        ).warm(gloryHtml)
        assertTrue(completed.contains("https://oldschool.runescape.wiki/images/glory-uncharged.png"))
        assertFalse(completed.contains(belowFoldUrl))
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
        assertTrue(shared.contains("noteUserInteraction"))
        assertTrue(shared.contains("pointerdown"))
        assertTrue(shared.contains("touchmove"))
        assertTrue(shared.contains("interactionHoldMs = 750"))
        assertFalse(shared.contains(".src ="))
        assertFalse(shared.contains("setAttribute('src'"))
    }

    @Test
    fun firstViewportScriptMatchesSharedCopyAndDoesNotAssignDomSrc() {
        val shared = repoFile("shared/js/first_viewport_assets.js").readText()
        val android = repoFile("platforms/android/app/src/main/assets/web/first_viewport_assets.js").readText()
        assertEquals(shared, android)
        assertTrue(shared.contains("osrsCollectFirstViewportUrls"))
        assertTrue(shared.contains("data-attr-index"))
        assertTrue(shared.contains("render-m"))
        assertTrue(shared.contains("osrsFirstViewComplete"))
        assertTrue(shared.contains("osrsNotifyFirstViewComplete"))
        assertTrue(shared.contains("osrsWatchFirstViewComplete"))
        assertTrue(shared.contains("__osrsFirstViewPainted"))
        assertTrue(shared.contains("domImageAlreadyDecoded"))
        assertTrue(shared.contains("naturalWidth"))
        assertTrue(shared.contains("new Image()"))
        assertFalse(shared.contains("el.src ="))
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
