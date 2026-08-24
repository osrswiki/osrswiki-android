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
        assertTrue(firstViewport.contains("Event: FirstViewportSettled"))
        assertTrue(firstViewport.contains("__osrsFirstViewportSettled"))
        assertTrue(firstViewport.contains("osrs-first-viewport-settled"))
        assertTrue(firstViewport.contains("osrsWatchFirstViewportSettled") || firstViewport.contains("reportSettled"))
        val webViewManager = source("page/PageWebViewManager.kt")
        assertTrue(webViewManager.contains("notifyFirstViewPainted"))
        assertTrue(webViewManager.contains("Event: FirstViewPainted"))
        assertTrue(webViewManager.contains("onFirstViewComplete"))
        assertTrue(webViewManager.contains("Event: FirstViewportSettled"))
        val pageContentLoader = source("page/PageContentLoader.kt")
        assertTrue(pageContentLoader.contains("LOAD-MINMAX first_viewport_settled"))
        assertTrue(pageContentLoader.contains("articleOpenAtElapsed"))
        assertTrue(pageContentLoader.contains("markFirstViewportSettled"))
        assertTrue(
            webViewManager.contains("first_viewport_settled") ||
                pageContentLoader.contains("first_viewport_settled")
        )
        assertFalse(
            Regex("""FirstViewportSettled""" + """[\s\S]{0,120}?notifyFirstViewPainted""")
                .containsMatchIn(webViewManager)
        )
        assertFalse(pageContentLoader.contains("readinessTracker"))
        val nativeMapHandler = source("page/NativeMapHandler.kt")
        assertTrue(nativeMapHandler.contains("firstViewportSettled"))
        val collapsible = asset("web/collapsible_content.js")
        assertTrue(collapsible.contains("scheduleCollapseAndMapWork"))
        assertTrue(collapsible.contains("osrs-first-view-complete"))
        val pageFragment = source("page/PageFragment.kt")
        assertTrue(pageFragment.contains("notifyFirstViewPainted"))
        assertTrue(scoped.contains("enrichMissingPreviews"))
        assertTrue(layout.contains("?attr/colorOnSurface"))
        assertFalse(layout.contains("?attr/colorPrimary"))
        assertTrue(fragment.contains("emptyQueryBrowsesNewest"))
        assertTrue(activity.contains("emptyQueryBrowsesNewest"))
    }

    @Test
    fun livePageContentLoaderPassesInlineLiveFirstPaintCssPref() {
        val loader = source("page/PageContentLoader.kt")
        assertTrue(
            "live buildFullHtmlDocument must pass Prefs.inlineLiveFirstPaintCss",
            loader.contains("inlineFirstPaintCss = Prefs.inlineLiveFirstPaintCss")
        )
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var inlineLiveFirstPaintCss: Boolean = true"))
    }

    @Test
    fun criticalArticleBundleFlagDefaultsOnAndBuilderWiresIt() {
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var useCriticalArticleBundle: Boolean = true"))
        val builder = source("page/PageHtmlBuilder.kt")
        assertTrue(builder.contains("Prefs.useCriticalArticleBundle"))
        assertTrue(builder.contains("CRITICAL_ARTICLE_BUNDLE_ASSET = \"styles/critical-article.min.css\""))
        val candidates = listOf(
            File("src/main/assets/styles/critical-article.min.css"),
            File("../main/assets/styles/critical-article.min.css"),
            File("app/src/main/assets/styles/critical-article.min.css")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Missing critical-article.min.css in android assets")
        val css = file.readText()
        assertTrue("bundle must keep table polish tokens", css.contains("infobox-bonuses") || css.contains("table-layout"))
        assertTrue(css.contains("AUTO-GENERATED"))
    }


    @Test
    fun task8BonusesMinCellGuardrailProbeAndAntiCrushCssLocked() {
        val root = repositoryRoot()
        val probe = File(root, "tools/qa/bonuses-min-cell-guardrail-probe.js").readText()
        assertTrue(probe.contains("table.infobox-bonuses"))
        assertTrue(probe.contains("minW >= 28"))
        assertTrue(probe.contains("getBoundingClientRect"))
        assertTrue(probe.contains("minCellWidth"))

        val guide = File(root, "tools/qa/article-load-fvs-task8-guardrail.md").readText()
        assertTrue(guide.contains("bonuses-min-cell-guardrail-probe.js"))
        assertTrue(guide.contains("Abyssal whip"))

        val fixesCandidates = listOf(
            File("src/main/assets/styles/fixes.css"),
            File("app/src/main/assets/styles/fixes.css"),
            File(root, "shared/css/fixes.css"),
            File(root, "platforms/android/app/src/main/assets/styles/fixes.css")
        )
        val fixes = fixesCandidates.firstOrNull { it.exists() }?.readText()
            ?: error("Missing fixes.css for Task 8 anti-crush lock")
        assertTrue(fixes.contains("--osrs-bonuses-min-inline-size"))
        assertTrue(fixes.contains("table.infobox-bonuses:not(.main-infobox)"))
        assertTrue(fixes.contains("min-width: var(--osrs-bonuses-min-inline-size)"))
    }

    private fun repositoryRoot(): File {
        var dir = File(".").canonicalFile
        repeat(12) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "platforms/android").isDirectory) {
                return dir
            }
            dir = dir.parentFile ?: error("Could not locate repository root")
        }
        error("Could not locate repository root from cwd")
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
