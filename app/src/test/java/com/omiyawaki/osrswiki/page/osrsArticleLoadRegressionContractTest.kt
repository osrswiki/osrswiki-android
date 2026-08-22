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
        assertTrue(switcher.contains("lockSwitcherMinBlockSize"))
        assertTrue(switcher.contains("stabilizeSwitcherScrollPin"))
        assertTrue(switcher.contains("bindSwitcherViewportPin"))
        assertTrue(switcher.contains("osrsSwitcherScrollingElement"))
        assertTrue(firstViewport.contains("osrsWatchFirstViewComplete"))
        assertTrue(firstViewport.contains("__osrsLayoutStability"))
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
