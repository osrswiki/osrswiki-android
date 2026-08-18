package com.omiyawaki.osrswiki.page

import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ArticleSearchPolishInstrumentedTest {

    @Test
    fun representativeArticlesStayInsideMobileViewportAndPolishSemanticMedia() {
        val amulet = loadAndMeasure("Amulet of glory", AMULET_METRICS)
        assertTrue("article document overflowed: $amulet", amulet.getDouble("documentOverflow") <= 1.5)
        assertTrue("price chart overflowed its infobox: $amulet", amulet.getDouble("chartOverflow") <= 1.5)
        assertTrue("price chart was not upgraded to an interactive chart: $amulet", amulet.getBoolean("chartInteractive"))
        assertTrue("wide combat table did not receive a local scroll viewport: $amulet", amulet.getBoolean("wideTableScrollable"))
        assertTrue("primary switch infobox still owned horizontal overflow: $amulet", amulet.getBoolean("primaryInfoboxViewportFit"))
        assertEquals("Scroll cue DOM must be removed, not merely hidden: $amulet", 0, amulet.getInt("scrollCueCount"))
        assertTrue("compact recipe table was stretched to the viewport: $amulet", amulet.getBoolean("recipeIntrinsic"))
        assertTrue("generated disclosure label duplicated a visible table caption: $amulet", amulet.getInt("duplicateVisibleCaptions") == 0)
        assertTrue("switcher did not expose a stable authored selection: $amulet", amulet.getBoolean("switcherReady"))
        assertTrue("expected at least one semantic vignette: $amulet", amulet.getInt("vignetteCount") >= 1)
        assertTrue("vignette remained too wide for a phone reading column: $amulet", amulet.getDouble("maxVignetteWidth") <= 112.5)
        assertTrue("vignette remained too tall for a phone viewport: $amulet", amulet.getDouble("maxVignetteHeight") <= 196.5)

        val village = loadAndMeasure("Barbarian Village", BARBARIAN_VILLAGE_METRICS)
        assertTrue("expected representative inline icons: $village", village.getInt("inlineIconCount") >= 2)
        assertTrue("inline icon exceeded the mobile text-line cap: $village", village.getDouble("maxInlineIcon") <= 48.5)
        assertTrue("expected an inline-icon lore wrapper inside article prose: $village", village.getInt("loreCandidateCount") >= 1)
        assertTrue("expected the authored padded lore fixture: $village", village.getInt("authoredPaddedLoreCandidateCount") >= 1)
        assertTrue("padded lore note was not normalized: $village", village.getBoolean("lorePaddingTrimmed"))
        assertTrue("Barbarian Village overflowed the document viewport: $village", village.getDouble("documentOverflow") <= 1.5)

        val barbarian = loadAndMeasure("Barbarian", BARBARIAN_METRICS)
        assertTrue("expected a semantic portrait infobox image: $barbarian", barbarian.getInt("portraitCount") >= 1)
        assertTrue("portrait infobox image remained too wide: $barbarian", barbarian.getDouble("maxPortraitWidth") <= 220.5)
        assertTrue("portrait infobox image remained too tall: $barbarian", barbarian.getDouble("maxPortraitHeight") <= 280.5)
    }

    private fun loadAndMeasure(title: String, metricsScript: String): JSONObject {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<PageActivity>(
            PageActivity.newIntent(
                context = context,
                pageTitle = title,
                pageId = null,
                source = HistoryEntry.SOURCE_SEARCH
            )
        ).use { scenario ->
            var activity: PageActivity? = null
            scenario.onActivity { activity = it }
            val deadline = SystemClock.elapsedRealtime() + 45_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                ready = evaluate(activity!!, "String(document.documentElement.dataset.osrsArticlePolished === 'true' && document.body.innerText.length > 1000)") == "true"
                if (!ready) SystemClock.sleep(250)
            }
            assertTrue("$title did not finish rendering before measurement", ready)
            // Let chart fetch/decode and post-layout mutation observers settle without mutating selection.
            val chartDeadline = SystemClock.elapsedRealtime() + 12_000
            if (title == "Amulet of glory") {
                while (SystemClock.elapsedRealtime() < chartDeadline) {
                    if (evaluate(activity!!, "String(!!document.querySelector('.GEdatachart[role=application]'))") == "true") break
                    SystemClock.sleep(250)
                }
            }
            val json = evaluate(activity!!, metricsScript)
            return JSONObject(json)
        }
    }

    private fun evaluate(activity: PageActivity, script: String): String {
        val latch = CountDownLatch(1)
        var raw = "null"
        activity.runOnUiThread {
            val webView = activity.findViewById<WebView>(R.id.page_web_view)
            webView.evaluateJavascript("(function(){ return ($script); })();") {
                raw = it ?: "null"
                latch.countDown()
            }
        }
        assertTrue("JavaScript measurement timed out", latch.await(10, TimeUnit.SECONDS))
        val decoded = JSONTokener(raw).nextValue()
        // A newly attached WebView can return JavaScript null for its initial blank document.
        // The caller's readiness loop retries that transient state before any assertion probe.
        return decoded as? String ?: ""
    }

    private companion object {
        val AMULET_METRICS = """
            JSON.stringify((() => {
              window.OSRSApplyArticlePolish && window.OSRSApplyArticlePolish();
              window.refreshHorizontalScrollAffordances && window.refreshHorizontalScrollAffordances();
              const viewport = document.documentElement.clientWidth;
              const charts = Array.from(document.querySelectorAll('.GEChartBox'));
              const chartOverflow = charts.reduce((value, box) => {
                const rect = box.getBoundingClientRect();
                const owner = box.closest('.infobox')?.getBoundingClientRect();
                return Math.max(value, rect.right - Math.min(viewport, owner ? owner.right : viewport));
              }, 0);
              const wideRegions = Array.from(document.querySelectorAll('.osrs-article-scroll-region'))
                .filter(region => region.querySelector('.infobox-switch, .infobox-bonuses'));
              const visibleWideRegions = wideRegions.filter(region => region.getBoundingClientRect().height > 0 && (region.querySelector('table')?.getBoundingClientRect().width || 0) > region.clientWidth + 2);
              const primaryRegion = document.querySelector('.collapsible-primary-infobox > .collapsible-content');
              const primaryTable = primaryRegion?.querySelector('table.main-infobox');
              const cueLayers = Array.from(document.querySelectorAll('.osrs-scroll-cue-layer'));
              const recipes = Array.from(document.querySelectorAll('.recipe-table.osrs-intrinsic-table'));
              const duplicateVisibleCaptions = Array.from(document.querySelectorAll('.collapsible-wikitable')).filter(container => {
                const label = container.querySelector('.collapsible-label')?.textContent?.trim();
                const caption = container.querySelector('caption');
                return label && caption && label === caption.textContent.trim() && getComputedStyle(caption).display !== 'none';
              });
              const vignettes = Array.from(document.querySelectorAll('.osrs-balanced-vignette'));
              return {
                documentOverflow: Math.max(0, document.documentElement.scrollWidth - viewport),
                chartOverflow: Math.max(0, chartOverflow),
                chartInteractive: charts.length > 0 && charts.some(box => box.querySelector('.GEdatachart[role=application]')),
                wideTableScrollable: wideRegions.length > 0 && wideRegions.every(region => getComputedStyle(region).overflowX === 'auto'),
                primaryInfoboxViewportFit: !!primaryRegion && !!primaryTable && primaryTable.getBoundingClientRect().width <= primaryRegion.clientWidth + 1.5 && getComputedStyle(primaryRegion).overflowX === 'hidden',
                scrollCueCount: cueLayers.length,
                wideRegionMetrics: wideRegions.map(region => ({ clientWidth: region.clientWidth, scrollWidth: region.scrollWidth, tableWidth: region.querySelector('table')?.getBoundingClientRect().width || 0 })),
                recipeIntrinsic: recipes.length > 0 && recipes.every(recipe => recipe.getBoundingClientRect().width < viewport - 8),
                duplicateVisibleCaptions: duplicateVisibleCaptions.length,
                switcherReady: !!document.querySelector('.infobox-switch[data-osrs-switcher-ready=true] .button-selected'),
                vignetteCount: vignettes.length,
                maxVignetteWidth: vignettes.reduce((value, figure) => Math.max(value, figure.getBoundingClientRect().width), 0),
                maxVignetteHeight: vignettes.reduce((value, figure) => Math.max(value, figure.getBoundingClientRect().height), 0)
              };
            })())
        """.trimIndent()

        val BARBARIAN_VILLAGE_METRICS = """
            JSON.stringify((() => {
              window.OSRSApplyArticlePolish && window.OSRSApplyArticlePolish();
              const viewport = document.documentElement.clientWidth;
              const icons = Array.from(document.querySelectorAll('img.osrs-inline-icon'));
              const loreCandidates = Array.from(document.querySelectorAll('p .osrs-inline-icon-wrapper'))
                .filter(wrapper => wrapper.querySelector('img.osrs-inline-icon'));
              const authoredPaddedLoreCandidates = loreCandidates.filter(wrapper => {
                const authored = wrapper.getAttribute('style') || '';
                const match = authored.match(/(?:^|;)\s*padding(?:-(?:left|right))?\s*:\s*([0-9.]+)/i);
                return !!match && parseFloat(match[1]) > 1;
              });
              return {
                inlineIconCount: icons.length,
                maxInlineIcon: icons.reduce((value, icon) => Math.max(value, icon.getBoundingClientRect().width, icon.getBoundingClientRect().height), 0),
                loreCandidateCount: loreCandidates.length,
                authoredPaddedLoreCandidateCount: authoredPaddedLoreCandidates.length,
                lorePaddingTrimmed: authoredPaddedLoreCandidates.length > 0 && authoredPaddedLoreCandidates.every(wrapper => parseFloat(getComputedStyle(wrapper).paddingLeft) <= 1 && parseFloat(getComputedStyle(wrapper).paddingRight) <= 1),
                documentOverflow: Math.max(0, document.documentElement.scrollWidth - viewport)
              };
            })())
        """.trimIndent()

        val BARBARIAN_METRICS = """
            JSON.stringify((() => {
              window.OSRSApplyArticlePolish && window.OSRSApplyArticlePolish();
              const portraits = Array.from(document.querySelectorAll('img.osrs-balanced-portrait'));
              return {
                portraitCount: portraits.length,
                maxPortraitWidth: portraits.reduce((value, image) => Math.max(value, image.getBoundingClientRect().width), 0),
                maxPortraitHeight: portraits.reduce((value, image) => Math.max(value, image.getBoundingClientRect().height), 0)
              };
            })())
        """.trimIndent()
    }
}
