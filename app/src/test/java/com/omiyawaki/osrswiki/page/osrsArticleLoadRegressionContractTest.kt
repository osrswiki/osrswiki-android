package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsArticleLoadRegressionContractTest {
    @Test
    fun firstPaintSwitcherPinAndViewportSamplerRemainIntact() {
        val htmlBuilder = source("page/PageHtmlBuilder.kt")
        val switcher = asset("web/switch_infobox.js")
        val firstViewport = asset("web/first_viewport_assets.js")
        val scoped = source("search/osrsScopedSearchPagingSource.kt")
        val layout = resource("layout/item_news_card_updates.xml")
        val fragment = source("search/SearchFragment.kt")
        val activity = source("search/SearchActivity.kt")

        assertTrue(htmlBuilder.contains("osrs-article-first-paint"))
        assertTrue(htmlBuilder.contains("usesDarkTheme"))
        assertTrue(htmlBuilder.contains("html:not(.theme-osrs-dark)"))
        assertTrue(htmlBuilder.contains("background-color: #28221d"))
        assertTrue(htmlBuilder.contains("--body-main: #28221d"))
        assertTrue(htmlBuilder.contains("first_viewport_assets.js"))
        assertTrue(htmlBuilder.contains("osrsActivateDeferredStylesheet"))
        assertTrue(htmlBuilder.contains("data-osrs-css=\"deferred\""))
        assertTrue(htmlBuilder.contains("LOAD-MINMAX html_ready"))
        assertTrue(switcher.contains("lockSwitcherMinBlockSize"))
        assertTrue(switcher.contains("stabilizeSwitcherScrollPin"))
        assertTrue(switcher.contains("bindSwitcherViewportPin"))
        assertTrue(switcher.contains("osrsSwitcherScrollingElement"))
        assertTrue(firstViewport.contains("osrsWatchFirstViewComplete"))
        assertTrue(firstViewport.contains("__osrsLayoutStability"))
        assertTrue(firstViewport.contains("osrs-first-view-complete"))
        assertTrue(firstViewport.contains("Event: FirstViewPainted"))
        val webViewManager = source("page/PageWebViewManager.kt")
        assertTrue(webViewManager.contains("notifyFirstViewPainted"))
        assertTrue(webViewManager.contains("Event: FirstViewPainted"))
        assertTrue(webViewManager.contains("onFirstViewComplete"))
        val collapsible = asset("web/collapsible_content.js")
        assertTrue(collapsible.contains("scheduleCollapseAndMapWork"))
        assertTrue(collapsible.contains("osrs-first-view-complete"))
        val pageFragment = source("page/PageFragment.kt")
        assertTrue(pageFragment.contains("notifyFirstViewPainted"))
        assertTrue(scoped.contains("enrichMissingPreviews"))
        assertTrue(layout.contains("?attr/linkColor"))
        assertFalse(layout.contains("?attr/colorPrimary"))
        assertTrue(fragment.contains("emptyQueryBrowsesNewest"))
        assertTrue(activity.contains("emptyQueryBrowsesNewest"))
    }

    private fun source(relativePath: String): String =
        File("src/main/java/com/omiyawaki/osrswiki/$relativePath").readText()

    private fun resource(relativePath: String): String =
        File("src/main/res/$relativePath").readText()

    private fun asset(path: String): String = listOf(
        File("src/main/assets", path),
        File("app/src/main/assets", path)
    ).firstOrNull(File::exists)?.readText() ?: error("Missing asset: $path")
}
