package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleMobilePolishContractTest {
    @Test
    fun rendererClassifiesInlinePortraitVignetteAndScrollableContent() {
        val script = asset("web/mobile_article_polish.js")
        val css = asset("styles/fixes.css")

        assertTrue(script.contains("width > 48 || height > 48"))
        assertTrue(script.contains("osrs-inline-lore-note"))
        assertTrue(script.contains("osrs-inline-icon-prose"))
        assertTrue(script.contains("osrsWrapperIsIconChrome"))
        assertTrue(script.contains("osrs-balanced-portrait"))
        assertTrue(script.contains("height / Math.max(width, 1) >= 1.45"))
        assertTrue(script.contains("osrs-balanced-vignette"))
        assertTrue(script.contains("osrs-article-scroll-region"))
        assertFalse(script.contains(".infobox-switch, .infobox-bonuses"))
        assertTrue(script.contains("table.matches('.infobox-bonuses')"))
        assertTrue(script.contains("osrs-local-scroll-surface"))
        assertTrue(script.contains("initializeLogicalScrollStart"))
        assertTrue(script.contains("hasRealHorizontalOverflow"))
        assertTrue(script.contains("tableIntrinsicScrollWidth"))
        assertTrue(script.contains("setProperty('width', 'max-content', 'important')"))
        assertTrue(script.contains("overflowingLocalSurface"))
        assertTrue(script.contains("disclosureBody || collapsibleContent"))
        assertTrue(script.contains("localScrollOwnerForTarget"))
        assertTrue(script.contains("classifyTouchOwner"))
        assertTrue(script.contains("collapsible-primary-infobox"))
        assertTrue(script.contains("collapsible-map-table"))
        assertTrue(script.contains("osrs-intrinsic-table"))
        assertTrue(css.contains("max-width: min(28vw, 112px)"))
        assertTrue(css.contains("max-height: min(26vh, 196px)"))
        assertTrue(css.contains("max-height: min(34vh, 280px)"))
        assertTrue(css.contains("overflow-x: auto !important"))
        assertTrue(css.contains("width: max-content !important"))
        assertTrue(css.contains(".collapsible-recipe-table .collapsible-close-footer"))
        assertTrue(css.contains(".collapsible-map-table table.osrs-map-table"))
        assertTrue(css.contains("--osrs-article-user-text-scale: 1"))
        assertTrue(css.contains("var(--osrs-article-text-scale, 1) * var(--osrs-article-user-text-scale, 1)"))
    }

    @Test
    fun priceChartIsWidthBoundedAndTouchInteractive() {
        val chart = asset("web/ge_charts_init.js")

        assertTrue(chart.contains("overflow:hidden !important"))
        assertTrue(chart.contains("zoomType: 'x'"))
        assertTrue(chart.contains("pinchType: 'x'"))
        assertTrue(chart.contains("panning: { enabled: true, type: 'x' }"))
        assertTrue(chart.contains("followTouchMove: true"))
        assertTrue(chart.contains("ResizeObserver"))
        assertTrue(chart.contains("Interactive Grand Exchange price chart"))
    }

    @Test
    fun infoboxStatesArePreloadedWithoutVisibleAutocycling() {
        val switcher = asset("web/switch_infobox.js")

        assertTrue(switcher.contains("data-default-version"))
        assertTrue(switcher.contains("preloader.decode()"))
        assertTrue(switcher.contains("updateExistingImage"))
        assertTrue(switcher.contains("lockSwitcherMinBlockSize"))
        assertFalse(switcher.contains("container.classList.contains('infobox-bonuses')"))
        assertFalse(switcher.contains("\n            stabilizeInfoboxWidth(mainInfobox"))
    }

    private fun asset(path: String): String = listOf(
        File("src/main/assets", path),
        File("app/src/main/assets", path)
    ).firstOrNull(File::exists)?.readText() ?: error("Missing asset: $path")
}
