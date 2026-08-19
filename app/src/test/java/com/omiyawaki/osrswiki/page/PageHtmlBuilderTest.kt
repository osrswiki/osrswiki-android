package com.omiyawaki.osrswiki.page

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.theme.Theme
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PageHtmlBuilderTest {

    private val builder = PageHtmlBuilder(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun buildFullHtmlDocumentCreatesSingleCleanPageHeader() {
        val html = builder.buildFullHtmlDocument(
            title = """<span class="mw-page-title-main">Rune scimitar</span>""",
            bodyContent = """
                <h1 class="page-header">Duplicate title</h1>
                <p>Slash bonuses and market data.</p>
            """.trimIndent(),
            theme = Theme.OSRS_LIGHT
        )

        val document = Jsoup.parse(html)

        assertEquals("Rune scimitar", document.title())
        assertEquals(listOf("Rune scimitar"), document.select("h1.page-header").eachText())
        assertTrue(document.select("body").text().contains("Slash bonuses and market data."))
        assertFalse(document.select("body").text().contains("Duplicate title"))
    }

    @Test
    fun buildFullHtmlDocumentAppliesDarkThemeAndCollapsePreference() {
        val html = builder.buildFullHtmlDocument(
            title = "Varrock",
            bodyContent = "<p>Capital of Misthalin.</p>",
            theme = Theme.OSRS_DARK,
            collapseTablesEnabled = false
        )

        val document = Jsoup.parse(html)

        assertTrue(document.body().hasClass("theme-osrs-dark"))
        assertTrue(document.body().hasClass(osrsArticleFloorConvention.current().bodyClass))
        assertFalse(document.body().hasAttr("style"))
        assertTrue(html.contains("window.OSRS_TABLE_COLLAPSED = false;"))
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun usDeviceLocaleAppliesUsFloorBodyClass() {
        val html = builder.buildFullHtmlDocument(
            title = "Heroes' Guild",
            bodyContent = "<p>Burthorpe.</p>",
            theme = Theme.OSRS_LIGHT
        )
        assertTrue(Jsoup.parse(html).body().hasClass("floornumber-setting-us"))
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun gbDeviceLocaleAppliesGbFloorBodyClass() {
        val html = builder.buildFullHtmlDocument(
            title = "Heroes' Guild",
            bodyContent = "<p>Burthorpe.</p>",
            theme = Theme.OSRS_LIGHT
        )
        assertTrue(Jsoup.parse(html).body().hasClass("floornumber-setting-gb"))
    }

    @Test
    fun buildFullHtmlDocumentIncludesBaseAssetsAndMediaWikiVariables() {
        val html = builder.buildFullHtmlDocument(
            title = "Combat guide",
            bodyContent = """<div class="tabber"><table class="wikitable sortable"></table></div>""",
            theme = Theme.OSRS_LIGHT
        )

        assertTrue(html.contains("styles/themes.css"))
        assertTrue(html.contains("styles/wiki-integration.css"))
        assertTrue(html.contains("startup.js"))
        assertTrue(html.contains("js/tablesort.min.js"))
        assertTrue(html.contains("web/collapsible_content.js"))
        assertTrue(html.contains("var RLCONF ="))
        assertTrue(html.contains("var RLPAGEMODULES ="))
        assertTrue(html.contains("\"ext.Tabber\""))
        assertTrue(html.contains("id=\"osrs-article-first-paint\""))
        assertTrue(html.contains("h1.page-header"))
        assertTrue(html.contains("min-height: 1.3em"))
        assertTrue(html.contains("alegreya_bold.ttf"))
        assertFalse(html.contains("padding-top: calc(env(safe-area-inset-top"))
    }

    @Test
    fun buildFullHtmlDocumentLoadsAndroidArticleAestheticOverridesAfterSharedFixes() {
        val html = builder.buildFullHtmlDocument(
            title = "Abyssal whip",
            bodyContent = """<table class="infobox infobox-bonuses"></table>""",
            theme = Theme.OSRS_LIGHT
        )

        val fixesIndex = html.indexOf("styles/fixes.css")
        val androidArticleIndex = html.indexOf("styles/android-article-aesthetics.css")

        assertTrue("shared fixes stylesheet should still be loaded", fixesIndex >= 0)
        assertTrue("Android article aesthetic override stylesheet should be loaded", androidArticleIndex >= 0)
        assertTrue(
            "Android article aesthetic overrides must load after shared fixes",
            androidArticleIndex > fixesIndex
        )
    }

    @Test
    fun buildFullHtmlDocumentOnlyLoadsGeChartAssetsWhenMarkersArePresent() {
        val plainHtml = builder.buildFullHtmlDocument(
            title = "Bones",
            bodyContent = "<p>No price chart here.</p>",
            theme = Theme.OSRS_LIGHT
        )
        val chartHtml = builder.buildFullHtmlDocument(
            title = "Dragon bones",
            bodyContent = """<div class="GEChartBox" data-item="Dragon bones"></div>""",
            theme = Theme.OSRS_LIGHT
        )

        assertFalse(plainHtml.contains("web/highcharts-stock.js"))
        assertFalse(plainHtml.contains("web/ge_charts_init.js"))
        assertTrue(chartHtml.contains("web/highcharts-stock.js"))
        assertTrue(chartHtml.contains("web/ge_charts_init.js"))
        assertTrue(chartHtml.contains("__osrsAmdDefine"))
    }

    @Test
    fun buildFullHtmlDocumentDoesNotRequestGeResourceLoaderModulesWithoutChartMarkers() {
        val html = builder.buildFullHtmlDocument(
            title = "Trailblazer Reloaded League/Tasks",
            bodyContent = """<table class="wikitable"><tr><td>Task</td></tr></table>""",
            theme = Theme.OSRS_LIGHT
        )

        assertFalse(html.contains("\"ext.gadget.GECharts\""))
        assertFalse(html.contains("\"ext.gadget.GECharts-core\""))
    }

    @Test
    fun preprocessingDefersLargeArticleTableImagesUntilExpansion() {
        val tableImages = (1..1001).joinToString("\n") { index ->
            """<img src="/images/task-$index.png" srcset="/images/task-$index.png 1x, /images/task-${index}@2x.png 2x" width="24" height="24" alt="Task $index">"""
        }
        val document = Jsoup.parse(
            """
                <div class="mw-parser-output">
                    <p><img src="/images/lead.png" width="32" height="32" alt="Lead">Readable lead text.</p>
                    <table class="wikitable"><tr><td>$tableImages</td></tr></table>
                </div>
            """.trimIndent()
        )

        val processedHtml = invokePreprocessHtml(document)
        val processedDocument = Jsoup.parse(processedHtml)
        val leadImage = processedDocument.selectFirst("p img")!!
        val deferredImage = processedDocument.selectFirst("table.wikitable img")!!

        assertEquals("https://oldschool.runescape.wiki/images/lead.png", leadImage.attr("src"))
        assertEquals("https://oldschool.runescape.wiki/images/task-1.png", deferredImage.attr("data-osrs-deferred-src"))
        assertEquals(
            "https://oldschool.runescape.wiki/images/task-1.png 1x, https://oldschool.runescape.wiki/images/task-1@2x.png 2x",
            deferredImage.attr("data-osrs-deferred-srcset")
        )
        assertTrue(deferredImage.attr("src").startsWith("data:image/svg+xml"))
        assertFalse(deferredImage.hasAttr("srcset"))
        assertTrue(deferredImage.hasClass("osrs-deferred-table-image"))
    }

    @Test
    fun preprocessingKeepsSmallArticleTableImagesEager() {
        val document = Jsoup.parse(
            """
                <table class="wikitable">
                    <tr><td><img src="/images/small-table.png" width="24" height="24" alt="Small"></td></tr>
                </table>
            """.trimIndent()
        )

        val processedHtml = invokePreprocessHtml(document)
        val tableImage = Jsoup.parse(processedHtml).selectFirst("table.wikitable img")!!

        assertEquals("https://oldschool.runescape.wiki/images/small-table.png", tableImage.attr("src"))
        assertFalse(tableImage.hasAttr("data-osrs-deferred-src"))
    }

    private fun invokePreprocessHtml(document: Document): String {
        val downloader = PageAssetDownloader(OkHttpClient())
        val normalizeUrls = PageAssetDownloader::class.java.getDeclaredMethod(
            "normalizeRelativeUrls",
            Document::class.java,
            String::class.java
        )
        val deferTableImages = PageAssetDownloader::class.java.getDeclaredMethod(
            "deferLargeArticleTableImages",
            Document::class.java
        )

        normalizeUrls.isAccessible = true
        deferTableImages.isAccessible = true
        normalizeUrls.invoke(downloader, document, "https://oldschool.runescape.wiki")
        deferTableImages.invoke(downloader, document)
        return document.outerHtml()
    }

    @Test
    fun markInlineIconsDoesNotTreatAuthoredProseGroupingSpansAsIconChrome() {
        val document = Jsoup.parse(
            """
            <p>
              <span id="group" style="padding:25.6px; font-size:10pt;">
                <span><img class="mw-file-element" width="18" height="17" src="book.png"></span>
                <i>The following lore is sourced from the Varrock Museum</i>.
              </span>
            </p>
            <p>Walk <span id="iconOnly" style="padding:25.6px"><span><img class="mw-file-element" width="24" height="24" src="icon.png"></span></span> north.</p>
            """.trimIndent()
        )
        invokeMarkInlineIcons(document)
        val group = document.selectFirst("#group")!!
        assertTrue(group.hasClass("osrs-inline-icon-prose"))
        assertFalse(group.hasClass("osrs-inline-icon-wrapper"))
        assertTrue(document.selectFirst("#iconOnly")!!.hasClass("osrs-inline-icon-wrapper"))
        assertFalse(document.selectFirst("#iconOnly")!!.hasClass("osrs-inline-icon-prose"))
    }

    private fun invokeMarkInlineIcons(document: Document) {
        val downloader = PageAssetDownloader(OkHttpClient())
        val markInlineIcons = PageAssetDownloader::class.java.getDeclaredMethod(
            "markInlineIcons",
            Document::class.java
        )
        markInlineIcons.isAccessible = true
        markInlineIcons.invoke(downloader, document)
    }
}
