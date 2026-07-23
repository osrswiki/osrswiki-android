package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArticleAestheticCssContractTest {

    @Test
    fun androidOnlyAestheticStylesheetContainsArticlePolishContracts() {
        val css = assetFile("styles/android-article-aesthetics.css").readText()

        assertTrue(css.contains(".messagebox:not(.discord) a"))
        assertTrue(css.contains(".messagebox:not(.discord)"))
        assertTrue(css.contains("border-left-width: 6px"))
        assertTrue(css.contains("box-shadow: 0 2px 6px"))
        assertTrue(css.contains(".messagebox .messagebox-image"))
        assertTrue(css.contains("background-color: transparent !important"))
        assertTrue(css.contains("border: 0 !important"))
        assertTrue(css.contains(".collapsible-container"))
        assertTrue(css.contains(".collapsible-content"))
        assertTrue(css.contains(".scp img.mw-file-element"))
        assertTrue(css.contains(".coins .mw-default-size"))
        assertTrue(css.contains(".plinkp-template img.mw-file-element"))
        assertTrue(css.contains("margin: 0 !important"))
        assertTrue(css.contains(".infobox-bonuses"))
        assertTrue(css.contains(".questdetails"))
        assertTrue(css.contains(".mwe-math-element"))
        assertTrue(css.contains("audio.mw-file-element"))
        assertTrue(css.contains("table.musicplayer"))
        assertTrue(css.contains("float: none !important"))
        assertTrue(css.contains("min-width: min(160px, 72vw)"))
    }

    @Test
    fun collapseTransformerKeepsPrimaryArticleStructureExpandedAndMeaningfullyLabeled() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("deriveCaptionText"))
        assertTrue(source.contains("findContextHeading"))
        assertTrue(source.contains("collapsible-primary-infobox"))
        assertTrue(source.contains("collapsible-bonuses-infobox"))
        assertTrue(source.contains("isAlwaysExpandedContent"))
        assertTrue(source.contains("restoreDeferredImages"))
        assertTrue(source.contains("Event: CollapsibleTransformsComplete"))
        assertFalse(source.contains("Use generic labels for all collapsible containers"))
        assertFalse(source.contains("caption.style.display = 'none'"))
    }

    @Test
    fun tableScrollAffordanceSeparatesRootOverflowFromLocalTableOverflow() {
        val css = assetFile("styles/android-article-aesthetics.css").readText()
        val horizontalScrollScript = assetFile("web/horizontal_scroll_interceptor.js").readText()

        assertTrue(css.contains(".osrs-scroll-affordance"))
        assertTrue(css.contains(".osrs-scroll-affordance.osrs-scroll-can-right"))
        assertTrue(css.contains("linear-gradient(to left"))
        assertTrue(css.contains("box-shadow: inset -18px 0 16px"))

        assertTrue(horizontalScrollScript.contains("refreshHorizontalScrollAffordances"))
        assertTrue(horizontalScrollScript.contains("window.OSRSArticleMetrics"))
        assertTrue(horizontalScrollScript.contains("rootOverflowX"))
        assertTrue(horizontalScrollScript.contains("localTableOverflowCount"))
        assertTrue(horizontalScrollScript.contains("tableAffordanceCount"))
        assertTrue(horizontalScrollScript.contains("maxLocalTableOverflowX"))
    }

    private fun assetFile(path: String): File {
        return listOf(
            File("src/main/assets", path),
            File("app/src/main/assets", path)
        ).firstOrNull { it.exists() } ?: error("Missing Android asset: $path")
    }
}
