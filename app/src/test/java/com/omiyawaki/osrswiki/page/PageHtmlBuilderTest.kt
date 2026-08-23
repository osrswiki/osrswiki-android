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
import java.io.File

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
        assertTrue(document.selectFirst("html")!!.hasClass("theme-osrs-dark"))
        assertTrue(document.body().hasClass(osrsArticleFloorConvention.current().bodyClass))
        assertFalse(document.body().hasClass("osrs-table-cells-wrap"))
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
    fun buildFullHtmlDocumentInlinesFirstPaintCssAndLeavesScriptsLinked() {
        val html = builder.buildFullHtmlDocument(
            title = "Varrock",
            bodyContent = "<p>Capital of Misthalin.</p>",
            theme = Theme.OSRS_LIGHT,
            inlineFirstPaintCss = true,
            bakeChromeInsets = false
        )

        assertTrue(html.contains("data-osrs-inline-css=\"styles/themes.css\""))
        assertFalse(
            html.contains("<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/styles/themes.css\">")
        )
        assertTrue(html.contains("<script src=\"https://appassets.androidplatform.net/assets/js/tablesort.min.js\""))
        assertTrue(html.contains("id=\"osrs-article-first-paint\""))
        assertTrue(html.contains("background-color: #e2dbc8"))
        assertFalse(html.contains("padding-top: calc(env(safe-area-inset-top"))
        assertTrue(html.contains("data-osrs-inline-css=\"styles/wiki-integration.css\""))
        assertTrue(html.contains("osrsActivateDeferredStylesheet"))
        assertFalse(html.contains("data-osrs-css-href=\"styles/wiki-integration.css\""))
        assertFalse(html.contains("media=\"print\""))
    }

    @Test
    fun articleFirstPaintStyleIncludesBodyColorAndBackgroundFallbacks() {
        val light = PageHtmlBuilder.articleFirstPaintStyle()
        assertTrue(light.contains("background-color: #e2dbc8"))
        assertTrue(light.contains("color: #000000"))
        assertTrue(light.contains("html.theme-osrs-dark"))
        assertTrue(light.contains("body.theme-osrs-dark"))
        assertTrue(light.contains("background-color: #28221d"))
        assertTrue(light.contains("--body-main: #28221d"))
        assertFalse(light.contains("var(--body-main, #e2dbc8)"))
        assertFalse(light.contains("min-width: min(18.75rem, 100%)"))
        assertTrue(light.contains("table.infobox-bonuses"))
        assertTrue(light.contains("table-layout: fixed"))
        val darkUnscoped = PageHtmlBuilder.articleFirstPaintStyle(usesDarkTheme = true)
            .substringBefore("html.theme-osrs-dark")
        assertTrue(darkUnscoped.contains("background-color: #28221d"))
        assertTrue(darkUnscoped.contains("--body-main: #28221d"))
        assertFalse(darkUnscoped.substringBefore("html:not(.theme-osrs-dark)").contains("background-color: #e2dbc8"))
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
        assertTrue(html.contains("web/live_article_asset_warm.js"))
        assertTrue(html.contains("web/first_viewport_assets.js"))
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
        assertFalse(html.contains("osrs-android-disclosure-chrome"))
        assertFalse(html.contains("min-height: 64px !important"))
        assertFalse(html.contains("OSRS_ANDROID_DISCLOSURE_CHROME"))
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

        assertFalse(plainHtml.contains("web/chart.umd.min.js"))
        assertFalse(plainHtml.contains("web/ge_charts_init.js"))
        assertTrue(chartHtml.contains("web/chart.umd.min.js"))
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
    fun calculatorPagesLoadCalcCoreAndCalculatorNamespace() {
        val html = builder.buildFullHtmlDocument(
            title = "Calculator:Combat level",
            bodyContent = """<pre class="jcConfig">template = Calculator:Combat level/Template</pre>""",
            theme = Theme.OSRS_LIGHT
        )

        assertTrue(html.contains("\"oojs-ui-core\""))
        assertTrue(html.contains("\"oojs-ui-widgets\""))
        assertTrue(html.contains("\"mediawiki.widgets\""))
        assertTrue(html.contains("\"wgNamespaceNumber\": 116") || html.contains("\"wgNamespaceNumber\":116"))
        assertTrue(html.contains("mediawiki/gadget_calc_core.js"))
        assertTrue(html.contains("web/osrs_calculator_runtime.js"))
        assertTrue(html.contains("styles/gadget_calc.css"))
        assertTrue(html.contains("id=\"bodyContent\""))
        assertTrue(html.contains("--osrs-article-bottom-chrome"))
        assertTrue(html.contains("padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 96px)"))
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

    @Test
    fun liveHtmlKeepsCriticalThemeCssBlockingAndDefersWikiIntegration() {
        val html = builder.buildFullHtmlDocument(
            title = "Varrock",
            bodyContent = """<table class="infobox"><tr><td>Capital</td></tr></table>""",
            theme = Theme.OSRS_LIGHT,
            bakeChromeInsets = false
        )
        val document = Jsoup.parse(html)

        assertCriticalStylesheet(document, "styles/themes.css")
        assertCriticalStylesheet(document, "styles/base.css")
        assertCriticalStylesheet(document, "web/collapsible_tables.css")
        assertCriticalStylesheet(document, "web/switch_infobox_styles.css")
        assertDeferredStylesheet(document, "styles/wiki-integration.css")
        assertDeferredStylesheet(document, "styles/fixes.css")
        assertDeferredStylesheet(document, "styles/android-article-aesthetics.css")

        assertTrue(html.contains("id=\"osrs-article-first-paint\""))
        assertTrue(html.contains("background-color: #e2dbc8"))
        assertTrue(html.contains("background-color: #28221d"))
        assertTrue(html.contains("osrsActivateDeferredStylesheet"))
        assertTrue(html.contains("Event: ParseReady"))
        assertTrue(html.contains("Event: FirstPaint"))
        assertTrue(html.contains("Event: DeferredCssApplied:"))
        assertTrue(asset("styles/wiki-integration.css").contains("infobox"))
        assertTrue(asset("styles/fixes.css").contains("table.infobox"))
    }

    @Test
    fun sourcePriorityAndLoaderStayInLockstepAcrossPlatforms() {
        val root = repositoryRoot()
        val json = File(root, "shared/css/article-css-priority.json").readText()
        val androidBuilder = File(
            root,
            "platforms/android/app/src/main/java/com/omiyawaki/osrswiki/page/PageHtmlBuilder.kt"
        ).readText()
        val iosBuilder = File(
            root,
            "platforms/ios/osrswiki/Services/osrsPageHtmlBuilder.swift"
        ).readText()
        val androidLoader = File(
            root,
            "platforms/android/app/src/main/java/com/omiyawaki/osrswiki/page/PageContentLoader.kt"
        ).readText()

        assertTrue(json.contains("\"wiki-integration.css\""))
        assertTrue(json.contains("\"fixes.css\""))
        assertTrue(json.contains("\"themes.css\""))
        assertTrue(androidBuilder.contains("osrsActivateDeferredStylesheet"))
        assertTrue(iosBuilder.contains("osrsActivateDeferredStylesheet"))
        assertTrue(androidBuilder.contains("data-osrs-css=\"deferred\""))
        assertTrue(iosBuilder.contains("data-osrs-css=\"deferred\""))
        assertTrue(androidBuilder.contains("LOAD-MINMAX html_ready"))
        assertTrue(iosBuilder.contains("LOAD-MINMAX html_ready"))
        assertTrue(androidLoader.contains("LOAD-MINMAX open"))
        assertTrue(androidLoader.contains("LOAD-MINMAX ttfb"))
        assertTrue(androidLoader.contains("LOAD-MINMAX first_viewport"))
        assertTrue(androidBuilder.contains("Event: ParseReady"))
        assertTrue(iosBuilder.contains("Event: ParseReady"))
        assertFalse(androidBuilder.contains("Event: StylingScriptsComplete"))
        assertFalse(iosBuilder.contains("Event: StylingScriptsComplete"))
    }

    private fun assertCriticalStylesheet(document: Document, asset: String) {
        val links = stylesheetLinks(document, asset)
        assertTrue("expected blocking $asset", links.any {
            it.attr("rel") == "stylesheet" && it.attr("data-osrs-css") == "critical"
        })
        assertFalse("critical $asset must not use media=print", links.any { it.attr("media") == "print" })
    }

    private fun assertDeferredStylesheet(document: Document, asset: String) {
        val links = stylesheetLinks(document, asset)
        assertTrue("expected preload for $asset", links.any {
            it.attr("rel") == "preload" && it.attr("as") == "style"
        })
        assertTrue("expected deferred stylesheet for $asset", links.any {
            it.attr("rel") == "stylesheet" &&
                it.attr("media") == "print" &&
                it.attr("data-osrs-css") == "deferred"
        })
        assertFalse(
            "deferred $asset must not be a blocking stylesheet",
            links.any { it.attr("rel") == "stylesheet" && it.attr("media") != "print" }
        )
    }

    private fun stylesheetLinks(document: Document, asset: String) =
        document.select("link").filter { it.attr("href").contains(asset) }

    private fun repositoryRoot(): File {
        var dir = File(".").canonicalFile
        repeat(8) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "platforms/android").isDirectory) {
                return dir
            }
            dir = dir.parentFile ?: error("Could not locate repository root")
        }
        error("Could not locate repository root from ${File(".").canonicalFile}")
    }

    private fun asset(path: String): String {
        val file = File("src/main/assets/$path").takeIf { it.exists() }
            ?: File("app/src/main/assets/$path")
        return file.readText()
    }
}
