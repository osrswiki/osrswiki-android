package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
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
    fun slice2DeferLiveWikiFidelityCssDefaultOnAndWired() {
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var deferLiveWikiFidelityCss: Boolean = true"))
        val loader = source("page/PageContentLoader.kt")
        assertTrue(loader.contains("deferWikiFidelityCss = Prefs.deferLiveWikiFidelityCss"))
        val builder = source("page/PageHtmlBuilder.kt")
        assertTrue(builder.contains("wikiFidelityDeferredStyleSheetAssets"))
        assertTrue(builder.contains("paintedPlatformAestheticsAsset"))
        assertTrue(builder.contains("styles/wiki-integration.css"))
        assertTrue(builder.contains("styles/navbox_styles.css"))
        assertTrue(builder.contains("styles/android-article-aesthetics.css"))
        assertTrue(builder.contains("data-osrs-defer-until"))
        assertTrue(builder.contains("osrs-first-view-complete"))
        val saved = source("savedpages/SavedPageSyncWorker.kt")
        assertFalse(
            "saved path must not opt into live wiki-integration deferral",
            saved.contains("deferLiveWikiFidelityCss")
        )
        val repository = source("page/PageRepository.kt")
        assertFalse(repository.contains("deferLiveWikiFidelityCss"))
    }

    @Test
    fun task10EarlyFirstViewSlotWarmWiredBeforeDocumentCommit() {
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var warmFirstViewportImagesEarly: Boolean = true"))
        val loader = source("page/PageContentLoader.kt")
        assertTrue(loader.contains("fun startFirstViewSlotWarm"))
        assertTrue(loader.contains("Prefs.warmFirstViewportImagesEarly"))
        assertTrue(loader.contains("osrsFirstViewAssetWarmer()"))
        assertTrue(loader.contains("LOAD-MINMAX first_view_slot_warm_start"))
        val successBranch = loader.substringAfter("is DownloadProgress.Success ->")
            .substringBefore("is DownloadProgress.Failure ->")
        assertTrue(successBranch.contains("startFirstViewSlotWarm"))
        assertTrue(
            successBranch.indexOf("startFirstViewSlotWarm") <
                successBranch.indexOf("onStateUpdated()")
        )
        assertFalse(successBranch.contains("startLiveArticleAssetWarm"))
        val fragment = source("page/PageFragment.kt")
        val readyCallback = fragment.substringAfter("override fun onPageReadyForDisplay()")
            .substringBefore("fun showFindInPage()")
        assertTrue(readyCallback.contains("startLiveArticleAssetWarm"))
        assertFalse(readyCallback.contains("startFirstViewSlotWarm"))
        val firstViewport = asset("web/first_viewport_assets.js")
        assertTrue(firstViewport.contains("notify(paintedUrls())"))
        assertTrue(firstViewport.contains("function paintedUrls"))
        assertTrue(firstViewport.contains("collectDefaultSwitcherPane"))
        assertTrue(firstViewport.contains("chosenElementUrls"))
        assertTrue(firstViewport.contains("currentSrc"))
        assertTrue(firstViewport.contains("var urls = paintedUrls()"))
        assertFalse(
            "watchComplete must not union the full switcher pool as the default painted set",
            Regex("""function watchComplete\(\) \{[\s\S]{0,200}?slotUrls\(\)\.concat\(collectIntersecting\(\)\)""")
                .containsMatchIn(firstViewport)
        )
    }

    @Test
    fun slice1NarrowFirstViewportPaintedSetDefaultOnAndWired() {
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var narrowFirstViewportPaintedSet: Boolean = true"))
        val htmlBuilder = source("page/PageHtmlBuilder.kt")
        assertTrue(htmlBuilder.contains("window.__osrsNarrowFirstViewportPaintedSet"))
        assertTrue(htmlBuilder.contains("Prefs.narrowFirstViewportPaintedSet"))
        val firstViewport = asset("web/first_viewport_assets.js")
        assertTrue(firstViewport.contains("__osrsNarrowFirstViewportPaintedSet"))
        assertTrue(firstViewport.contains("collectDefaultSwitcherPane"))
        assertTrue(firstViewport.contains("chosenElementUrls"))
        val switcher = asset("web/switch_infobox.js")
        assertTrue(switcher.contains("scheduleSwitcherPoolDecode"))
        assertTrue(switcher.contains("preloadSwitcherPool"))
        assertTrue(switcher.contains("osrs-first-view-complete"))
        assertTrue(switcher.contains("performSwitch(initialIndex)"))
        val initBody = switcher.substringAfter("function initializePage()")
            .substringBefore("function preloadSwitcherPool")
        assertTrue(initBody.contains("scheduleSwitcherPoolDecode()"))
        assertTrue(initBody.contains("performSwitch(initialIndex)"))
        assertTrue(
            initBody.indexOf("scheduleSwitcherPoolDecode()") <
                initBody.indexOf("performSwitch(initialIndex)")
        )
        assertFalse(
            "full-pool Image()+decode must not run inline before performSwitch",
            initBody.contains("preloader.decode()")
        )
        val preloadBody = switcher.substringAfter("function scheduleSwitcherPoolDecode()")
            .substringBefore("let pendingSwitcherScrollPin")
        assertTrue(preloadBody.contains("osrs-first-view-complete"))
        assertTrue(preloadBody.contains("preloadSwitcherPool()"))
        val warmer = source("page/osrsFirstViewAssetWarmer.kt")
        assertTrue(warmer.contains("extractFirstViewSlot"))
        assertTrue(warmer.contains("narrowFirstViewportPaintedSet"))
        val defaultOnBranch = warmer.substringAfter("narrowFirstViewportPaintedSet")
            .substringBefore("} else {")
        assertFalse(
            "early warmer default-on path must not Jsoup-extract the full document",
            defaultOnBranch.contains("ReadingListAssetUrlExtractor.extract(html")
        )
        val remainder = source("page/osrsLiveArticleAssetWarmer.kt")
        assertTrue(
            "remainder warmer still enumerates the full document after reveal",
            remainder.contains("ReadingListAssetUrlExtractor.extract(html")
        )
        val fragment = source("page/PageFragment.kt")
        val readyCallback = fragment.substringAfter("override fun onPageReadyForDisplay()")
            .substringBefore("fun showFindInPage()")
        assertTrue(readyCallback.contains("startLiveArticleAssetWarm"))
        val webViewManager = source("page/PageWebViewManager.kt")
        assertFalse(
            Regex("""FirstViewportSettled""" + """[\s\S]{0,120}?notifyFirstViewPainted""")
                .containsMatchIn(webViewManager)
        )
        val store = source("page/osrsPreparedArticleWebViewStore.kt")
        assertTrue(store.contains("private const val maxEntries = 2"))
        val builder = source("page/PageHtmlBuilder.kt")
        assertTrue(builder.contains("styles/fixes.css"))
        assertTrue(builder.contains("styles/gadget_calc.css"))
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
    fun slice4DeferLiveTableOfContentsExtractDefaultOnAndOffHtmlReady() {
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var deferLiveTableOfContentsExtract: Boolean = true"))
        val loader = source("page/PageContentLoader.kt")
        assertTrue(loader.contains("fun startDeferredTableOfContentsExtract"))
        assertTrue(loader.contains("LOAD-MINMAX toc_ready"))
        assertTrue(loader.contains("tocDeferred=\$deferToc"))
        assertTrue(loader.contains("Prefs.deferLiveTableOfContentsExtract"))
        val successBranch = loader.substringAfter("is DownloadProgress.Success ->")
            .substringBefore("is DownloadProgress.Failure ->")
        assertTrue(successBranch.contains("startFirstViewSlotWarm"))
        assertTrue(successBranch.contains("onStateUpdated()"))
        assertTrue(successBranch.contains("startDeferredTableOfContentsExtract"))
        assertTrue(
            "HTML commit / onStateUpdated must happen before deferred TOC extract",
            successBranch.indexOf("onStateUpdated()") <
                successBranch.indexOf("startDeferredTableOfContentsExtract")
        )
        val htmlReadyLine = loader.lineSequence()
            .first { it.contains("LOAD-MINMAX html_ready elapsedMs=") }
        assertFalse(
            "tocExtractionMs must not share the html_ready log line",
            htmlReadyLine.contains("tocExtractionMs")
        )
        val deferredFn = loader.substringAfter("fun startDeferredTableOfContentsExtract")
            .substringBefore("fun cancelActivePageWork")
        assertTrue(deferredFn.contains("PageTableOfContentsExtractor.extract"))
        assertTrue(deferredFn.contains("LOAD-MINMAX toc_ready"))
        assertTrue(loader.contains("tocExtractJob?.cancel()"))
        val fragment = source("page/PageFragment.kt")
        assertTrue(fragment.contains("contentsHandler?.setup(sections)"))
        assertTrue(fragment.contains("tableOfContentsSections"))
        val readyCallback = fragment.substringAfter("override fun onPageReadyForDisplay()")
            .substringBefore("fun showFindInPage()")
        assertTrue(readyCallback.contains("fetchTableOfContents()"))
    }

    @Test
    fun lazyOffscreenArticleImagesDefaultOnAndWired() {
        val prefs = source("settings/Prefs.kt")
        assertTrue(prefs.contains("var lazyOffscreenArticleImages: Boolean = true"))
        val htmlBuilder = source("page/PageHtmlBuilder.kt")
        assertTrue(htmlBuilder.contains("web/article_image_lazy.js"))
        assertTrue(htmlBuilder.contains("Prefs.lazyOffscreenArticleImages"))
        val downloader = source("page/PageAssetDownloader.kt")
        assertTrue(downloader.contains("applyLazyOffscreenArticleImages"))
        assertTrue(downloader.contains("deferHiddenSwitcherPoolImages"))
        assertTrue(downloader.contains("applySrcsetSizes"))
        val warmer = source("page/osrsFirstViewAssetWarmer.kt")
        assertTrue(warmer.contains("eagerOnly = Prefs.lazyOffscreenArticleImages"))
        val srcset = source("page/SrcsetParser.kt")
        assertTrue(srcset.contains("fun choose("))
        val lazyJs = asset("web/article_image_lazy.js")
        assertTrue(lazyJs.contains("osrsRestoreDeferredImage"))
        assertTrue(lazyJs.contains("data-osrs-deferred-src"))
        val switcher = asset("web/switch_infobox.js")
        assertTrue(switcher.contains("restoreDeferredImage"))
        val preload = switcher.substringAfter("function preloadSwitcherPool")
            .substringBefore("function scheduleSwitcherPoolDecode")
        assertFalse(
            "post-paint pool preload must not enqueue every srcset candidate",
            preload.contains("sources.forEach(sourceUrl => imageUrlsToPreload.add(sourceUrl))")
        )
        val sharedLazy = File(repositoryRoot(), "shared/js/article_image_lazy.js").readText()
        assertEquals(sharedLazy, lazyJs)
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
