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
        val genericMessageboxRule = css.substringAfter(".messagebox:not(.discord) {")
            .substringBefore("}", missingDelimiterValue = "")
        assertFalse(
            "Generic quest/info banners must keep wiki parchment colors, not Wikipedia-info blue.",
            genericMessageboxRule.contains("messagebox-info-background")
        )
        assertTrue(css.contains("box-shadow: 0 2px 6px"))
        assertTrue(css.contains(".messagebox .messagebox-image"))
        assertTrue(css.contains("background-color: transparent !important"))
        assertTrue(css.contains("border: 0 !important"))
        assertTrue(css.contains(".collapsible-container"))
        assertTrue(css.contains(".collapsible-content"))
        assertTrue(css.contains(".osrs-disclosure-body"))
        assertTrue(css.contains("margin-inline: var(--osrs-disclosure-content-inline-inset, 12px) !important;"))
        assertTrue(css.contains("display: flow-root !important;"))
        assertTrue(css.contains("background-color: var(--osrs-disclosure-chrome-bg, var(--body-mid)) !important;"))
        assertTrue(css.contains(".collapsible-infobox:not(.collapsed) > .collapsible-content"))
        assertTrue(css.contains("max-width: min(18.75rem, 100%) !important;"))
        assertTrue(css.contains(":has(.mw-kartographer-map)"))
        assertTrue(css.contains("width: max-content !important;"))
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
        assertTrue(css.contains("--osrs-disclosure-control-padding-block: 6px !important;"))
        assertTrue(css.contains("--osrs-disclosure-control-padding-inline: 10px !important;"))
        assertTrue(css.contains("padding: 6px 10px !important;"))
        assertTrue(css.contains("padding: 12px !important;"))
        assertTrue(css.contains("height: auto !important;"))
        assertTrue(css.contains("min-height: 0 !important;"))
        assertTrue(css.contains("background-color: var(--osrs-disclosure-chrome-bg, var(--body-mid, #d0bd97)) !important;"))
        assertTrue(css.contains(".collapsible-close-button"))
        assertFalse(css.contains("min-height: 64px"))
        assertFalse(css.contains("padding: 16px 16px !important;"))
    }

    @Test
    fun headingFontsOptOutOfLateSwapToKeepPageTitleGeometry() {
        val fonts = assetFile("styles/fonts.css").readText()
        assertTrue(fonts.contains("font-display: optional"))
        assertTrue(fonts.contains("font-family: 'Alegreya'"))
    }

    @Test
    fun sharedTableDensityKeepsAndroidReferencePaddingAndDoesNotForce112vwBonuses() {
        val fixes = assetFile("styles/fixes.css").readText()
        assertTrue(fixes.contains("--osrs-table-cell-padding-block: 0.32em;"))
        assertTrue(fixes.contains("--osrs-infobox-cell-padding-block: 0.24em;"))
        assertTrue(fixes.contains("--osrs-bonuses-min-inline-size: 0;"))
        assertFalse(fixes.contains("112vw"))
        assertTrue(
            fixes.contains(
                ".recipe-table.osrs-recipe-unit > .collapsible-recipe-table:not(.collapsed) > .collapsible-content"
            )
        )
        assertTrue(fixes.contains("--osrs-disclosure-content-inline-inset: 12px;"))
        assertTrue(fixes.contains("--osrs-disclosure-content-block-inset: 8px;"))
        assertTrue(fixes.contains("--osrs-disclosure-control-padding-block: 6px;"))
        assertTrue(fixes.contains("html.osrs-table-cells-wrap"))
        assertTrue(fixes.contains("padding-block: 0 !important;"))
        assertTrue(fixes.contains(".osrs-disclosure-body"))
        assertTrue(fixes.contains("margin-inline: var(--osrs-disclosure-content-inline-inset) !important;"))
        assertTrue(fixes.contains("display: flow-root !important;"))
        assertTrue(fixes.contains("float: none !important;"))
        assertTrue(fixes.contains("background-color: var(--osrs-disclosure-chrome-bg, var(--body-mid)) !important;"))
        assertTrue(fixes.contains(".collapsible-infobox:not(.collapsed) > .collapsible-content"))
        assertTrue(fixes.contains("max-width: min(18.75rem, 100%) !important;"))
        assertTrue(fixes.contains(":has(.mw-kartographer-map)"))
        assertTrue(fixes.contains("width: max-content !important;"))
        assertTrue(fixes.contains("padding-inline: var(--osrs-disclosure-content-inline-inset) !important;"))
        assertTrue(fixes.contains("min-width: max-content !important;"))
        assertTrue(fixes.contains("hyphens: none !important;"))
        assertTrue(
            fixes.contains(".collapsible-primary-infobox table.infobox th:not(.infobox-header) {")
        )
        assertFalse(
            "Location infobox labels wrap when every primary-infobox th is forced to white-space:normal.",
            Regex(
                """\.collapsible-primary-infobox th,\s*\n\s*\.collapsible-primary-infobox td \{"""
            ).containsMatchIn(fixes)
        )
        assertFalse(
            "Location labels are th[colspan=2]; excluding colspan from nowrap wraps Inhabitants.",
            fixes.contains("table.infobox th:not(.infobox-header):not([colspan])")
        )
        val themes = assetFile("styles/themes.css").readText()
        assertTrue(themes.contains("html.theme-osrs-dark"))
        assertTrue(themes.contains("--osrs-disclosure-chrome-bg: var(--body-light);"))
        assertFalse(themes.contains("color-mix(in srgb, var(--body-main) 78%, var(--body-light) 22%)"))
        val tables = assetFile("web/collapsible_tables.css").readText()
        assertTrue(tables.contains(":not(.collapsible-infobox):not(.collapsible-primary-infobox)"))
        assertTrue(fixes.contains(".collapsible-container.collapsed > .collapsible-content"))
        assertFalse(fixes.contains("padding: 12px 12px 0 12px !important;"))
        assertFalse(fixes.contains("padding: 0 12px !important;"))
    }

    @Test
    fun collapseTransformerKeepsPrimaryArticleStructureExpandedAndMeaningfullyLabeled() {
        val source = assetFile("web/collapsible_content.js").readText()

        assertTrue(source.contains("osrsApplyAndroidDisclosureChrome"))
        assertTrue(source.contains("osrsApplyAndroidDisclosureChromeAll"))
        assertTrue(source.contains("osrsUsesAndroidDisclosureChrome"))
        assertFalse(source.contains("min-height', '64px'"))
        assertFalse(source.contains("padding', '16px 16px'"))
        assertTrue(source.contains("absorbDisclosureChildren"))
        assertTrue(source.contains("applyDisclosureInnerInset"))
        assertTrue(source.contains("scheduleDisclosureInnerInsets"))
        assertTrue(source.contains("osrs-disclosure-inset-target"))
        assertTrue(source.contains("osrs-disclosure-body"))
        assertTrue(source.contains("deriveCaptionText"))
        assertTrue(source.contains("findContextHeading"))
        assertTrue(source.contains("collapsible-primary-infobox"))
        assertTrue(source.contains("collapsible-bonuses-infobox"))
        assertTrue(source.contains("topLevelPrimaryInfobox"))
        assertTrue(source.contains("shouldStartCollapsed(isPrimary)"))
        assertTrue(source.contains("restoreDeferredImages"))
        assertTrue(source.contains("Array.from(content.querySelectorAll('.mw-kartographer-map'))"))
        assertTrue(source.contains("mapPlaceholders.forEach"))
        assertTrue(source.contains("directRecipeTables"))
        assertTrue(source.contains("recipeRoleForTable"))
        assertTrue(source.contains("dataset.osrsTableRole"))
        assertTrue(source.contains("collapsible-recipe-table"))
        assertTrue(source.contains("collapsible-map-table"))
        assertTrue(source.contains("Event: CollapsibleTransformsComplete"))
        assertTrue(source.contains("authoredMapId"))
        assertTrue(source.contains("dataset.mapid"))
        assertFalse(source.contains("Use generic labels for all collapsible containers"))
        assertFalse(source.contains("caption.style.display = 'none'"))
        assertFalse(source.contains("getArticleContext"))
    }

    @Test
    fun tableScrollAffordanceSeparatesRootOverflowFromLocalTableOverflow() {
        val css = assetFile("styles/android-article-aesthetics.css").readText()
        val fixes = assetFile("styles/fixes.css").readText()
        val horizontalScrollScript = assetFile("web/horizontal_scroll_interceptor.js").readText()

        assertTrue(css.contains(".osrs-scroll-affordance"))
        assertTrue(css.contains(".osrs-scroll-cue-layer"))
        assertTrue(css.contains("display: none !important"))
        assertTrue(css.contains("background-image: none !important"))
        assertTrue(css.contains(".collapsible-primary-infobox"))
        assertTrue(css.contains("overflow-x: hidden !important"))
        assertTrue(css.contains(".collapsible-container.collapsible-recipe-table"))
        assertTrue(css.contains("table.osrs-intrinsic-recipe-table"))
        assertTrue(css.contains("width: 100% !important"))
        assertTrue(css.contains("> caption"))

        assertTrue(horizontalScrollScript.contains("refreshHorizontalScrollAffordances"))
        assertTrue(horizontalScrollScript.contains("window.OSRSArticleMetrics"))
        assertTrue(horizontalScrollScript.contains("rootOverflowX"))
        assertTrue(horizontalScrollScript.contains("localTableOverflowCount"))
        assertTrue(horizontalScrollScript.contains("tableAffordanceCount"))
        assertTrue(horizontalScrollScript.contains("maxLocalTableOverflowX"))
        assertFalse(horizontalScrollScript.contains("'table.infobox-switch'"))
        assertTrue(horizontalScrollScript.contains("table.infobox-bonuses"))
        assertTrue(horizontalScrollScript.contains("osrs-local-scroll-surface"))
        assertTrue(horizontalScrollScript.contains("window.OSRSHorizontalGestureOwnership"))
        assertTrue(horizontalScrollScript.contains("latestTouchIsOwned"))
        assertTrue(horizontalScrollScript.contains("snapshotForSequence"))
        assertTrue(horizontalScrollScript.contains("setArticleTouchSequence"))
        assertTrue(horizontalScrollScript.contains("if (activeTouchSequence !== null) return"))
        assertFalse(horizontalScrollScript.contains("ensureScrollCueLayer"))
        assertFalse(horizontalScrollScript.contains("scrollCueLayers"))
        assertFalse(horizontalScrollScript.contains("dataset.osrsScrollCue"))
        assertTrue(fixes.contains(".osrs-local-scroll-surface > table"))
        assertTrue(fixes.contains(".collapsible-primary-infobox"))
        assertTrue(fixes.contains(".collapsible-map-table"))
        assertTrue(fixes.contains(":has(.mw-kartographer-map)"))
        assertTrue(fixes.contains(":not(.osrs-map-table)"))
        assertFalse(fixes.contains("table.osrs-map-table th:first-child"))
        assertFalse(fixes.contains("width: 42% !important;"))
        assertFalse(fixes.contains("width: 58% !important;"))
        assertTrue(fixes.contains("float: none !important"))
        assertTrue(fixes.contains("margin-inline: 0 !important"))
        assertTrue(fixes.contains("--osrs-bonuses-min-inline-size"))
        assertTrue(fixes.contains("--osrs-bonuses-label-inline-size"))
        assertTrue(fixes.contains("--osrs-bonuses-state-inline-size"))
        assertTrue(fixes.contains("--osrs-infobox-state-control-gap"))
        assertTrue(fixes.contains(".infobox-switch .infobox-buttons .button"))
        assertTrue(fixes.contains("min-width: 0 !important"))
        assertTrue(fixes.contains("table.infobox-bonuses :is(th, td).infobox-nested"))
        assertTrue(fixes.contains("table-layout: fixed !important"))
        assertTrue(fixes.contains("overflow: hidden !important"))
        assertTrue(fixes.contains("table.infobox-bonuses:not(.main-infobox)"))
        assertFalse(File("src/main/assets/web/switch_infobox_styles.css").let {
            if (it.exists()) it else File("app/src/main/assets/web/switch_infobox_styles.css")
        }.readText().contains("min-width: 4rem"))
        assertFalse(css.contains("min-width: 556px"))
        assertFalse(css.contains("padding: 12px 12px 0 12px !important;"))
        assertTrue(css.contains(".recipe-table.osrs-recipe-unit > .collapsible-recipe-table.collapsed > .collapsible-content"))
    }

    @Test
    fun infoboxesAreNotHiddenUntilTransformsComplete() {
        val tables = assetFile("web/collapsible_tables.css").readText()
        assertFalse(tables.contains("body:not(.js-transforms-complete) .infobox"))
        assertFalse(tables.contains("opacity: 0;"))
        assertTrue(tables.contains(".mw-parser-output > table.infobox:not(.skill-info):not(.infobox-bonuses)"))
        assertTrue(tables.contains("float: none !important"))

        val components = assetFile("styles/components.css").readText()
        val infoboxRule = components.substringAfter(".infobox {").substringBefore("}")
        assertTrue(infoboxRule.contains("float: none"))
        assertFalse(infoboxRule.contains("float: right"))
    }

    @Test
    fun searchResultsReanchorToTopAfterQueryChanges() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/search/SearchResultsFragment.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/search/SearchResultsFragment.kt")
        }.readText()
        assertTrue(source.contains("pendingScrollToTopQuery"))
        assertTrue(source.contains("maybeAnchorSearchResultsToTop"))
        assertTrue(source.contains("StateRestorationPolicy.PREVENT"))
    }

    @Test
    fun geChartInitDoesNotMarkRenderedBeforeSuccess() {
        val source = assetFile("web/ge_charts_init.js").readText()
        assertTrue(source.contains("osrsChartPending"))
        assertTrue(source.contains("Price history unavailable"))
        assertTrue(source.contains("cache: 'no-cache'"))
        assertFalse(source.contains("cache: 'force-cache'"))
        assertTrue(source.contains("chartEl.dataset.rendered = '1'"))
        assertTrue(source.contains("resolveHighcharts"))
        assertTrue(source.contains("AbortController"))
        assertTrue(source.contains("Highcharts never became available"))
        val renderedBeforeFetch = Regex(
            """dataset\.rendered = '1';[\s\S]{0,200}fetchSeries"""
        )
        assertFalse(renderedBeforeFetch.containsMatchIn(source))
    }

    @Test
    fun collapsibleHeadersStayOneLineAndUseMaskChevrons() {
        val fixes = assetFile("styles/fixes.css").readText()
        val tables = assetFile("web/collapsible_tables.css").readText()
        val collapsible = assetFile("web/collapsible_content.js").readText()
        val polish = assetFile("web/mobile_article_polish.js").readText()
        val interceptor = assetFile("web/horizontal_scroll_interceptor.js").readText()
        val downloader = File("src/main/java/com/omiyawaki/osrswiki/page/PageAssetDownloader.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/page/PageAssetDownloader.kt")
        }.readText()
        val historyManager = File("src/main/java/com/omiyawaki/osrswiki/page/PageHistoryManager.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/page/PageHistoryManager.kt")
        }.readText()

        assertFalse(collapsible.contains("Use generic labels for all collapsible containers"))
        assertTrue(collapsible.contains("Tap to collapse"))
        assertTrue(collapsible.contains("Tap to expand"))
        assertTrue(collapsible.contains("osrsMeasureDisclosureHeaderGaps"))
        assertTrue(collapsible.contains("collapsible-state"))
        assertTrue(collapsible.contains("collapsible-label"))
        assertTrue(fixes.contains("text-overflow: ellipsis"))
        assertTrue(fixes.contains("white-space: nowrap"))
        assertTrue(fixes.contains("mask-image:"))
        assertTrue(fixes.contains("padding-right: 0 !important"))
        assertTrue(fixes.contains("padding-inline-end: 0 !important"))
        assertTrue(fixes.contains("display: inline !important"))
        assertTrue(fixes.contains(".osrs-local-scroll-surface > table.infobox-bonuses"))
        assertTrue(fixes.contains("width: max-content !important"))
        assertTrue(fixes.contains(".osrs-mmg-rate-control"))
        assertTrue(fixes.contains("display: table !important"))
        assertTrue(tables.contains("mask-image:"))
        assertFalse(tables.contains("fill='currentColor'"))
        assertTrue(polish.contains("queueMicrotask"))
        assertTrue(polish.contains("viewportWidth"))
        assertFalse(polish.contains("requestAnimationFrame(applyPolish)"))
        assertTrue(interceptor.contains("canConsumeHorizontalDelta"))
        assertTrue(interceptor.contains("horizontalEdgeCapacity"))
        assertTrue(interceptor.contains("sequenceAxisLock"))
        assertTrue(interceptor.contains("const edgeSlop = 8"))
        assertTrue(interceptor.contains("isProseBannerTable"))
        assertFalse(interceptor.contains("if (!consume && isHorizontallyScrollable)"))
        assertTrue(polish.contains("isProseBannerTable"))
        assertTrue(polish.contains("unwrapGeneratedScrollSurface"))
        assertTrue(polish.contains("articleChromeOffsetPx"))
        assertTrue(polish.contains("osrsWrapperIsIconChrome"))
        assertTrue(polish.contains("osrs-inline-icon-prose"))
        assertTrue(fixes.contains(".osrs-inline-icon-prose"))
        assertTrue(fixes.contains("padding-block: 0 !important"))
        assertTrue(fixes.contains("floated vignette"))
        assertTrue(fixes.contains("width: 100% !important"))
        assertTrue(fixes.contains("clear: both !important"))
        assertTrue(fixes.contains("Prose banners"))
        assertTrue(fixes.contains("table-layout: fixed !important;"))
        assertTrue(fixes.contains(".mw-kartographer-container .thumbcaption"))
        assertTrue(fixes.contains("color: var(--text-color) !important;"))
        assertTrue(interceptor.contains("isOverflowingHorizontalScroller"))
        assertTrue(interceptor.contains("overflowingHorizontalOwner"))
        assertTrue(downloader.contains("parseBodyFragment"))
        assertFalse(downloader.contains("Parser.xmlParser()"))
        assertFalse(
            "Minerva mobileformat strips #toc; in-article Contents must use the desktop parse text.",
            downloader.contains("mobileformat")
        )
        assertTrue(historyManager.contains("fetchPagePreview"))
        assertTrue(historyManager.contains("getHistoryPreviewMetadata"))
        val historyViewModel = File("src/main/java/com/omiyawaki/osrswiki/history/HistoryViewModel.kt").let {
            if (it.exists()) it else File("app/src/main/java/com/omiyawaki/osrswiki/history/HistoryViewModel.kt")
        }.readText()
        assertTrue(historyViewModel.contains("enrichIncompleteHistory"))
        assertTrue(historyViewModel.contains("getHistoryPreviewMetadata"))
        assertTrue(downloader.contains("markInlineIcons"))
        assertTrue(downloader.contains("osrs-inline-icon-wrapper"))
        assertTrue(downloader.contains("osrsWrapperIsIconChrome"))
        assertTrue(downloader.contains("osrs-inline-icon-prose"))
    }

    @Test
    fun androidProseInlineIconsKeepTwoEmMiddleNotWebKitXHeight() {
        val fixes = assetFile("styles/fixes.css").readText()
        val androidAesthetics = assetFile("styles/android-article-aesthetics.css").readText()
        assertFalse(
            "WebKit x-height optical align belongs in iOS aesthetics, not shared/Android CSS.",
            fixes.contains("vertical-align: -0.2em")
        )
        assertFalse(
            "Forcing prose icons to 1em shrinks Android glyphs that used to cap at 2em.",
            Regex("""p img\.mw-file-element[\s\S]{0,500}height:\s*1em""").containsMatchIn(fixes)
        )
        val genericIcon = fixes.substringAfter("img.osrs-inline-icon {")
            .substringBefore("}", missingDelimiterValue = "")
        assertTrue(genericIcon.contains("max-height: 2em !important"))
        assertTrue(genericIcon.contains("max-width: 2em !important"))
        assertTrue(genericIcon.contains("vertical-align: middle !important"))
        assertFalse(androidAesthetics.contains("vertical-align: -0.2em"))
        assertFalse(
            Regex("""p img\.mw-file-element[\s\S]{0,500}height:\s*1em""").containsMatchIn(androidAesthetics)
        )
    }

    private fun assetFile(path: String): File {
        return listOf(
            File("src/main/assets", path),
            File("app/src/main/assets", path)
        ).firstOrNull { it.exists() } ?: error("Missing Android asset: $path")
    }
}
