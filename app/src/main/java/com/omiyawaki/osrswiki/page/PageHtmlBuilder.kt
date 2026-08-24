package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.settings.ReaderTextScale
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.StringUtil
import java.util.Locale
import kotlin.system.measureTimeMillis

class PageHtmlBuilder(private val context: Context) {

    private val logTag = "PageLoadTrace"

    // Render-blocking first-paint CSS: theme tokens, typography, above-fold chrome.
    // See shared/css/article-css-priority.json.
    private val criticalStyleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/fonts.css",
        "styles/layout.css",
        "styles/components.css",
        JavaScriptActionHandler.getCollapsibleTableCssPath(),
        "web/collapsible_sections.css",
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(),
        // Table/infobox/bonuses + calculator chrome must apply before first layout.
        // Early-paint table-layout:fixed without these rules crushes bonus columns.
        "styles/gadget_calc.css",
        "styles/fixes.css"
    )

    // Heavy wiki fidelity sheets. Downloaded immediately but applied after first paint.
    private val deferredStyleSheetAssets = listOf(
        "styles/wiki-integration.css",
        "styles/navbox_styles.css",
        "styles/android-article-aesthetics.css"
    )

    private val styleSheetAssets: List<String>
        get() = criticalStyleSheetAssets + deferredStyleSheetAssets

    // Simple MediaWiki ResourceLoader - let it work naturally
    private val mediawikiArtifacts = listOf(
        "startup.js",
        "mediawiki/gadget_calc_core.js"
    )
    
    private val articleTransformJsAssetPaths = listOf(
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
        "web/collapsible_content.js",
        "web/first_viewport_assets.js",
        "web/live_article_asset_warm.js",
        "web/mobile_article_polish.js",
        "web/horizontal_scroll_interceptor.js",
        "web/image_area_cap.js"
    )

    // Base JavaScript assets (before conditional GE charts addition)
    private val jsAssetPaths = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js",
        "web/tabber_init.js",
        "web/osrs_calculator_runtime.js",
        "web/responsive_videos.js",
        "web/clipboard_bridge.js",
        "web/table_column_normalize.js"
    )
    
    private val themeUtilityScript = """
        <script>
            // Theme switching utility for instant theme changes (preserved from working version)
            window.OSRSWikiTheme = {
                switchTheme: function(isDark) {
                    var body = document.body;
                    var root = document.documentElement;
                    if (!body || !root) return;
                    body.classList.toggle('theme-osrs-dark', !!isDark);
                    root.classList.toggle('theme-osrs-dark', !!isDark);
                    root.style.colorScheme = isDark ? 'dark' : 'light';
                    body.offsetHeight;
                    if (body.style.visibility !== 'visible') {
                        body.style.visibility = 'visible';
                    }
                }
            };
        </script>
    """.trimIndent()

    private fun createTableCollapseScript(collapseTablesEnabled: Boolean): String {
        return """
            <script>
                // Global variable for table collapse preference that collapsible_content.js can read
                window.OSRS_TABLE_COLLAPSED = $collapseTablesEnabled;
                console.log('PageHtmlBuilder: Set global collapse preference to ' + window.OSRS_TABLE_COLLAPSED);
            </script>
        """.trimIndent()
    }

    /**
     * Generate smart MediaWiki variables based on page content.
     * Uses WikiModuleRegistry for intelligent module detection.
     */
    private fun generateMediaWikiVariables(title: String, bodyContent: String, canonicalTitle: String? = null): String {
        // Generate smart RLPAGEMODULES based on content analysis
        val detectedModules = WikiModuleRegistry.generateRLPAGEMODULES(bodyContent, canonicalTitle ?: title)
        val modulesList = detectedModules.joinToString(", ") { "\"$it\"" }
        val pageConfig = osrsWikiWebViewUrl.mediaWikiPageConfig(
            canonicalTitle = canonicalTitle ?: title,
            displayTitle = title
        )
        val safetitle = pageConfig.pageName.replace("\"", "\\\"")
        val safeVisibleTitle = pageConfig.title.replace("\"", "\\\"")
        val namespaceNumber = pageConfig.namespaceNumber
        val canonicalNamespace = pageConfig.canonicalNamespace
        
        return """
            <script>
                // Smart MediaWiki variables generated based on page content
                // Module detection via WikiModuleRegistry for scalable maintenance
                var RLCONF = {"wgBreakFrames": false, "wgSeparatorTransformTable": ["", ""], "wgDigitTransformTable": ["", ""], "wgDefaultDateFormat": "dmy", "wgMonthNames": ["", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"], "wgRequestId": "smart-module-loader", "wgCanonicalNamespace": "$canonicalNamespace", "wgCanonicalSpecialPageName": false, "wgNamespaceNumber": $namespaceNumber, "wgPageName": "$safetitle", "wgTitle": "$safeVisibleTitle", "wgCurRevisionId": 0, "wgRevisionId": 0, "wgArticleId": 1, "wgIsArticle": true, "wgIsRedirect": false, "wgAction": "view", "wgUserName": null, "wgUserGroups": ["*"], "wgPageViewLanguage": "en-gb", "wgPageContentLanguage": "en-gb", "wgPageContentModel": "wikitext", "wgRelevantPageName": "$safetitle", "wgRelevantArticleId": 1, "wgIsProbablyEditable": true, "wgRelevantPageIsProbablyEditable": true, "wgRestrictionEdit": [], "wgRestrictionMove": [], "wgServer": "https://oldschool.runescape.wiki", "wgServerName": "oldschool.runescape.wiki", "wgScriptPath": "", "wgScript": "/index.php", "wgLoadScript": "/load.php"};
                var RLSTATE = {"ext.gadget.switch-infobox-styles": "ready", "ext.gadget.articlefeedback-styles": "ready", "ext.gadget.falseSubpage": "ready", "ext.gadget.headerTargetHighlight": "ready", "site.styles": "ready", "user.styles": "ready", "user": "ready", "user.options": "loading", "ext.cite.styles": "ready", "ext.kartographer.style": "ready", "skins.minerva.base.styles": "ready", "skins.minerva.content.styles.images": "ready", "mediawiki.hlist": "ready", "skins.minerva.codex.styles": "ready", "skins.minerva.icons.wikimedia": "ready", "skins.minerva.mainMenu.icons": "ready", "skins.minerva.mainMenu.styles": "ready", "jquery.tablesorter.styles": "ready", "ext.embedVideo.styles": "ready", "mobile.init.styles": "ready"};
                var RLPAGEMODULES = [$modulesList];
                
                // Log detected modules for debugging
                console.log('WikiModuleRegistry detected modules for "$safetitle":', RLPAGEMODULES);
            </script>
        """.trimIndent()
    }

    fun buildFullHtmlDocument(
        title: String,
        bodyContent: String,
        theme: Theme,
        collapseTablesEnabled: Boolean = true,
        readerTextScale: Float = Prefs.readerTextScale,
        canonicalTitle: String? = null,
        inlineFirstPaintCss: Boolean = false,
        bakeChromeInsets: Boolean = true
    ): String {
        var finalHtml: String
        val floorClass = osrsArticleFloorConvention.resolved(deviceLocale()).bodyClass
        val wrapClass = if (Prefs.wrapTableCells) "osrs-table-cells-wrap" else ""
        val time = measureTimeMillis {
            // Preserved title logic from working version
            val cleanedTitle = StringUtil.extractMainTitle(title)
            val documentTitle = if (cleanedTitle.isBlank()) "OSRS Wiki" else cleanedTitle
            // Wave2c/wave4 residual: Calculator:Sailing is one CSS word and mid-wraps
            // under overflow-wrap:break-word. Wave6: Construction/Materials does the
            // same after the slash. Prefer breaks after the namespace colon and
            // subpage slash; Genie-style spaced titles still wrap on spaces.
            val titleHeaderHtml = "<h1 class=\"page-header\">${softWrapNamespaceTitle(documentTitle)}</h1>"
            
            // Clean any existing page-header titles from bodyContent to prevent duplication
            val cleanedBodyContent = removeDuplicatePageHeaders(bodyContent)
            val articleBodyContent = wrapArticleBodyContent(cleanedBodyContent)
            val themeClass = when (theme) {
                Theme.OSRS_DARK -> "theme-osrs-dark"
                else -> "" // OSRS Light is the default theme in CSS, no class needed.
            }

            // Detect presence of GE price charts in the content and include widget script when needed
            val needsGECharts = cleanedBodyContent.contains("GEChartBox") ||
                    cleanedBodyContent.contains("GEdatachart") ||
                    cleanedBodyContent.contains("GEdataprices")
            if (needsGECharts) {
                Log.d(logTag, "Detected GE chart markers in content; will include Chart.js widget script.")
            }

            val cssLinks = stylesheetMarkup(inlineFirstPaintCss)

            Log.d(logTag, "Using natural MediaWiki ResourceLoader with network-level caching")

            // Natural MediaWiki loading: Only startup.js, let ResourceLoader handle everything else
            // Network interceptor will cache load.php responses as needed
            
            val mediawikiScripts = mediawikiArtifacts.joinToString("\n") { assetPath ->
                "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
            }
            
            // Build the JS list, conditionally appending the GE charts widget
            val dynamicJsAssets = if (needsGECharts) {
                jsAssetPaths + listOf(
                    "web/chart.umd.min.js",
                    "web/ge_charts_init.js"
                )
            } else jsAssetPaths

            val jsScripts = dynamicJsAssets.joinToString("\n") { assetPath ->
                val tag = "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
                if (assetPath.endsWith("chart.umd.min.js")) {
                    // Chart.js UMD prefers AMD when present. MediaWiki defines `define`, so
                    // window.Chart never appears unless we temporarily clear it.
                    "<script>window.__osrsAmdDefine=window.define;try{window.define=undefined;}catch(e){}</script>\n$tag\n<script>if(typeof window.__osrsAmdDefine!=='undefined'){window.define=window.__osrsAmdDefine;}</script>"
                } else {
                    tag
                }
            }
            val transformScripts = articleTransformJsAssetPaths.joinToString("\n") { assetPath ->
                "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
            }
            
            // Generate smart MediaWiki variables
            val smartMediawikiVariables = generateMediaWikiVariables(
                cleanedTitle,
                cleanedBodyContent,
                canonicalTitle ?: title
            )

            // Create table collapse preference script
            val tableCollapseScript = createTableCollapseScript(collapseTablesEnabled)
            val readerTextScaleBootstrap = readerTextScaleBootstrap(readerTextScale)

            // Preload the heading face used by h1.page-header so first paint
            // does not wait for a late @font-face swap that restyles the title.
            val fontPreloadLink = """
                <link rel="preload" href="https://appassets.androidplatform.net/res/font/alegreya_bold.ttf" as="font" type="font/ttf" crossorigin="anonymous">
                <link rel="preload" href="https://appassets.androidplatform.net/res/font/runescape_plain.ttf" as="font" type="font/ttf" crossorigin="anonymous">
            """.trimIndent()
            val isCalculatorPage = cleanedBodyContent.contains("jcConfig") ||
                osrsWikiWebViewUrl.isCalculatorNamespaceTitle(canonicalTitle ?: cleanedTitle)
            val firstPaintStyle = articleFirstPaintStyle(
                bottomChromePx = if (bakeChromeInsets && isCalculatorPage) 96 else 0,
                usesDarkTheme = theme == Theme.OSRS_DARK
            )

            // Body visibility handled by RenderTimeline when JavaScript completes
            // No inline FOUC fix needed - prevents flash of untransformed content

            val estimatedSize = cleanedBodyContent.length +
                titleHeaderHtml.length +
                cssLinks.length +
                mediawikiScripts.length +
                jsScripts.length +
                smartMediawikiVariables.length +
                themeUtilityScript.length +
                tableCollapseScript.length +
                readerTextScaleBootstrap.length +
                2_048
            finalHtml = StringBuilder(estimatedSize)
                .append("<!DOCTYPE html>\n")
                .append("<html class=\"").append(themeClass).append(" ").append(wrapClass).append("\">\n")
                .append("<head>\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">\n")
                .append("    <title>").append(documentTitle).append("</title>\n")
                .append("    ").append(firstPaintStyle).append('\n')
                .append("    <meta name=\"ResourceLoaderDynamicStyles\" content=\"\">\n")
                .append("    ").append(fontPreloadLink).append('\n')
                .append("    ").append(cssLinks).append('\n')
                .append("    ").append(readerTextScaleBootstrap).append('\n')
                .append("    ").append(themeUtilityScript).append('\n')
                .append("    ").append(tableCollapseScript).append('\n')
                .append("    ").append(smartMediawikiVariables).append('\n')
                .append("</head>\n")
                .append("<body class=\"").append(themeClass).append(" ").append(floorClass)
                .append(" ").append(wrapClass).append("\">\n")
                .append(titleHeaderHtml)
                .append(articleBodyContent)
                .append('\n')
                .append(transformScripts).append('\n')
                .append(mediawikiScripts).append('\n')
                .append(jsScripts).append('\n')
                .append("</body>\n")
                .append("</html>")
                .toString()
        }
        Log.d(logTag, "buildFullHtmlDocument() took ${time}ms")
        Log.d(
            logTag,
            "LOAD-MINMAX html_ready buildMs=$time htmlChars=${finalHtml.length} " +
                "inlineFirstPaintCss=$inlineFirstPaintCss"
        )
        return finalHtml
    }

    private fun stylesheetMarkup(inlineFirstPaintCss: Boolean): String {
        val useBundle = Prefs.useCriticalArticleBundle
        if (inlineFirstPaintCss) {
            val criticalPart = if (useBundle) {
                inlineCriticalBundleOrFallback()
            } else {
                criticalStyleSheetAssets.joinToString("\n") { assetPath ->
                    inlineStylesheetOrLink(assetPath)
                }
            }
            val deferredPart = deferredStyleSheetAssets.joinToString("\n") { assetPath ->
                inlineStylesheetOrLink(assetPath)
            }
            return "$criticalPart\n$deferredPart\n$ARTICLE_CSS_LOADER_SCRIPT"
        }
        val critical = if (useBundle) {
            blockingStylesheetLink(CRITICAL_ARTICLE_BUNDLE_ASSET, ANDROID_ASSET_HREF_PREFIX)
        } else {
            criticalStyleSheetAssets.joinToString("\n") { assetPath ->
                blockingStylesheetLink(assetPath, ANDROID_ASSET_HREF_PREFIX)
            }
        }
        val deferred = deferredStyleSheetAssets.joinToString("\n") { assetPath ->
            deferredStylesheetLinks(assetPath, ANDROID_ASSET_HREF_PREFIX)
        }
        return "$critical\n$ARTICLE_CSS_LOADER_SCRIPT\n$deferred"
    }

    private fun inlineStylesheetOrLink(assetPath: String): String {
        val css = loadAssetText(assetPath)
        return if (css.isNullOrEmpty()) {
            blockingStylesheetLink(assetPath, ANDROID_ASSET_HREF_PREFIX)
        } else {
            """<style data-osrs-inline-css="$assetPath">$css</style>"""
        }
    }

    private fun inlineCriticalBundleOrFallback(): String {
        val css = loadAssetText(CRITICAL_ARTICLE_BUNDLE_ASSET)
        return if (css.isNullOrEmpty()) {
            // Bundle missing from assets — fall back to per-file critical so paint stays correct.
            criticalStyleSheetAssets.joinToString("\n") { assetPath ->
                inlineStylesheetOrLink(assetPath)
            }
        } else {
            """<style data-osrs-inline-css="$CRITICAL_ARTICLE_BUNDLE_ASSET">$css</style>"""
        }
    }

    fun loadAssetText(assetPath: String): String? {
        return runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    private fun deviceLocale(): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.size() > 0) locales[0] else Locale.getDefault()
    }
    
    /**
     * Insert <wbr> after wiki namespace colons and subpage slashes when the
     * following char is non-space so long unbroken tokens wrap at those
     * separators instead of mid-word (Calculator:Sailing,
     * Calculator:Construction/Materials). Safe for plain extractMainTitle output.
     */
    private fun softWrapNamespaceTitle(title: String): String {
        return SUBPAGE_SLASH_WBR.replace(
            NAMESPACE_COLON_WBR.replace(title, "$1<wbr>"),
            "$1<wbr>"
        )
    }

    private val NAMESPACE_COLON_WBR =
        Regex("""\b([A-Za-z][A-Za-z ]{0,40}:)(?=\S)""")

    private val SUBPAGE_SLASH_WBR =
        Regex("""(/)(?=[A-Za-z])""")

    /**
     * Removes any existing page-header titles from HTML content to prevent duplication.
     * This is useful for cleaning content that may have been processed multiple times.
     */
    private fun removeDuplicatePageHeaders(htmlContent: String): String {
        if (!htmlContent.contains("page-header")) {
            return htmlContent
        }
        return try {
            // Use a regex to remove h1 elements with class="page-header"
            val pageHeaderRegex = Regex("<h1\\s+class=\"page-header\"[^>]*>.*?</h1>", RegexOption.DOT_MATCHES_ALL)
            htmlContent.replace(pageHeaderRegex, "")
        } catch (e: Exception) {
            Log.e(logTag, "Error removing duplicate page headers", e)
            htmlContent // Return original content if cleaning fails
        }
    }

    private fun wrapArticleBodyContent(htmlContent: String): String {
        if (htmlContent.contains("id=\"bodyContent\"") || htmlContent.contains("id='bodyContent'")) {
            return htmlContent
        }
        return """<div id="bodyContent" class="mw-body-content">$htmlContent</div>"""
    }

    companion object {
        const val CRITICAL_ARTICLE_BUNDLE_ASSET = "styles/critical-article.min.css"
        private const val READER_STYLE_ID = "osrs-reader-text-scale-style"
        private const val READER_SCALE_VARIABLE = "--osrs-article-user-text-scale"
        internal const val ANDROID_ASSET_HREF_PREFIX = "https://appassets.androidplatform.net/assets/"

        /**
         * Shared head loader for deferred CSS activation and load-minmax timeline events.
         * Keep in lockstep with osrsPageHtmlBuilder.articleCssLoaderScript on iOS.
         */
        internal val ARTICLE_CSS_LOADER_SCRIPT = """
<script id="osrs-article-css-loader">
(function() {
  window.osrsActivateDeferredStylesheet = function(link) {
    if (!link || link.getAttribute('data-osrs-css-activated') === '1') { return; }
    link.media = 'all';
    link.onload = null;
    link.setAttribute('data-osrs-css-activated', '1');
    var href = link.getAttribute('data-osrs-css-href') || link.getAttribute('href') || '';
    if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
      window.RenderTimeline.log('Event: DeferredCssApplied:' + href);
    }
  };
  function osrsActivatePendingDeferredStylesheets() {
    var nodes = document.querySelectorAll('link[data-osrs-css="deferred"]');
    for (var i = 0; i < nodes.length; i++) {
      if (nodes[i].media !== 'all') {
        window.osrsActivateDeferredStylesheet(nodes[i]);
      }
    }
  }
  document.addEventListener('DOMContentLoaded', function() {
    osrsActivatePendingDeferredStylesheets();
    if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
      window.RenderTimeline.log('Event: ParseReady');
    }
  });
  if (window.requestAnimationFrame) {
    requestAnimationFrame(function() {
      requestAnimationFrame(function() {
        if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
          window.RenderTimeline.log('Event: FirstPaint');
        }
      });
    });
  }
})();
</script>
        """.trimIndent()

        internal fun blockingStylesheetLink(asset: String, hrefPrefix: String): String {
            return """<link rel="stylesheet" href="$hrefPrefix$asset" data-osrs-css="critical">"""
        }

        internal fun deferredStylesheetLinks(asset: String, hrefPrefix: String): String {
            val href = "$hrefPrefix$asset"
            return """<link rel="preload" as="style" href="$href">
<link rel="stylesheet" href="$href" media="print" onload="osrsActivateDeferredStylesheet(this)" data-osrs-css="deferred" data-osrs-css-href="$asset">"""
        }

        internal fun articleFirstPaintStyle(
            chromeClearancePx: Int = 0,
            bottomChromePx: Int = 0,
            usesDarkTheme: Boolean = false
        ): String {
            val chromePadding = buildString {
                if (chromeClearancePx > 0 || bottomChromePx > 0) {
                    appendLine("html:root {")
                    if (bottomChromePx > 0) {
                        appendLine("    --osrs-article-bottom-chrome: ${bottomChromePx}px;")
                    }
                    appendLine("}")
                    appendLine("html {")
                    if (chromeClearancePx > 0) {
                        appendLine(
                            "    padding-top: calc(env(safe-area-inset-top, 0px) + ${chromeClearancePx}px) !important;"
                        )
                    }
                    if (bottomChromePx > 0) {
                        appendLine(
                            "    padding-bottom: calc(env(safe-area-inset-bottom, 0px) + ${bottomChromePx}px) !important;"
                        )
                    } else if (chromeClearancePx > 0) {
                        appendLine(
                            "    padding-bottom: calc(env(safe-area-inset-bottom, 0px) + ${chromeClearancePx}px) !important;"
                        )
                    }
                    appendLine("}")
                }
            }
            val bodyMain = if (usesDarkTheme) "#28221d" else "#e2dbc8"
            val bodyLight = if (usesDarkTheme) "#3e362f" else "#d8ccb4"
            val textColor = if (usesDarkTheme) "#f4eaea" else "#000000"
            val lightFallback = if (usesDarkTheme) {
                """
                    html:not(.theme-osrs-dark),
                    html:not(.theme-osrs-dark) body {
                        --body-main: #e2dbc8;
                        --body-light: #d8ccb4;
                        --text-color: #000000;
                        background-color: #e2dbc8 !important;
                        color: #000000 !important;
                    }
                """
            } else {
                ""
            }
            return """
                <style id="osrs-article-first-paint">
                    $chromePadding
                    h1.page-header {
                        font-family: 'Alegreya', 'Palatino', 'Georgia', serif !important;
                        font-weight: bold !important;
                        font-size: 1.8em !important;
                        line-height: 1.3 !important;
                        margin-top: 0 !important;
                        margin-bottom: 0.6em !important;
                        padding-bottom: 0.2em !important;
                        min-height: 0;
                        max-width: 100% !important;
                        white-space: normal !important;
                        overflow-wrap: break-word !important;
                        word-break: normal !important;
                        overflow: visible !important;
                        border-bottom: 1px solid var(--sidebar-color, currentColor);
                        box-sizing: border-box;
                    }
                    html, body, .mw-parser-output, .mw-content-text {
                        line-height: 1.35 !important;
                    }
                    html {
                        --body-main: $bodyMain;
                        --body-light: $bodyLight;
                        --text-color: $textColor;
                    }
                    html, body {
                        background-color: $bodyMain !important;
                        color: $textColor !important;
                    }
                    $lightFallback
                    html.theme-osrs-dark {
                        --body-main: #28221d;
                        --body-light: #3e362f;
                        --text-color: #f4eaea;
                    }
                    html.theme-osrs-dark,
                    html.theme-osrs-dark body,
                    body.theme-osrs-dark {
                        background-color: #28221d !important;
                        color: #f4eaea !important;
                    }
                    table.infobox:not(.infobox-bonuses),
                    .infobox-switch:not(.infobox-bonuses),
                    .collapsible-primary-infobox {
                        max-width: 100%;
                        min-width: min(18.75rem, 100%);
                        box-sizing: border-box;
                    }
                    /* Bonuses column sizing lives in critical fixes.css.
                       Do not force table-layout:fixed here alone — that crushed
                       Attack/Defence/Other columns before polish applied. */
                    table.infobox-bonuses {
                        max-width: 100%;
                        min-width: 0;
                        box-sizing: border-box;
                    }
                    .mw-parser-output p,
                    .mw-parser-output > ul,
                    .mw-parser-output > ol,
                    .mw-parser-output > dl,
                    .mw-content-text p {
                        line-height: 1.35 !important;
                    }
                </style>
            """.trimIndent()
        }

        internal fun readerTextScaleBootstrap(scale: Float): String {
            val cssValue = readerTextScaleCssValue(scale)
            return """
                <style id="$READER_STYLE_ID">
                    :root {
                        $READER_SCALE_VARIABLE: $cssValue;
                    }
                </style>
                <script>
                    document.documentElement.style.setProperty('$READER_SCALE_VARIABLE', '$cssValue');
                </script>
            """.trimIndent()
        }

        internal fun readerTextScaleRuntimeScript(scale: Float): String {
            val cssValue = readerTextScaleCssValue(scale)
            return """
                (function() {
                    var style = document.getElementById('$READER_STYLE_ID');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = '$READER_STYLE_ID';
                        style.textContent = ':root { $READER_SCALE_VARIABLE: $cssValue; }';
                        document.head.appendChild(style);
                    }
                    document.documentElement.style.setProperty('$READER_SCALE_VARIABLE', '$cssValue');
                })();
            """.trimIndent()
        }

        internal fun floorNumberingRuntimeScript(bodyClass: String): String {
            val sanitized = when (bodyClass) {
                osrsArticleFloorConvention.US.bodyClass -> osrsArticleFloorConvention.US.bodyClass
                else -> osrsArticleFloorConvention.GB.bodyClass
            }
            return """
                (function() {
                    var body = document.body;
                    if (!body) return;
                    body.classList.remove(
                        '${osrsArticleFloorConvention.GB.bodyClass}',
                        '${osrsArticleFloorConvention.US.bodyClass}'
                    );
                    body.classList.add('$sanitized');
                })();
            """.trimIndent()
        }

        internal fun wrapTableCellsRuntimeScript(enabled: Boolean): String = """
            (function() {
                var enabled = $enabled;
                if (typeof window.osrsApplyTableCellWrapPreference === 'function') {
                    window.osrsApplyTableCellWrapPreference(enabled);
                    return;
                }
                [document.documentElement, document.body].forEach(function(element) {
                    if (element) {
                        element.classList.toggle('osrs-table-cells-wrap', enabled);
                    }
                });
            })();
        """.trimIndent()

        internal fun tableCollapseRuntimeScript(collapseTablesEnabled: Boolean): String = """
            (function() {
                var shouldCollapse = $collapseTablesEnabled;
                window.OSRS_TABLE_COLLAPSED = shouldCollapse;
                document.querySelectorAll('.collapsible-container').forEach(function(container) {
                    var isPrimary = container.classList.contains('primary-collapsible') ||
                        container.classList.contains('collapsible-primary-infobox');
                    var desiredCollapsed = shouldCollapse && !isPrimary;
                    var isCollapsed = container.classList.contains('collapsed');
                    if (desiredCollapsed !== isCollapsed) {
                        var header = container.querySelector(':scope > .collapsible-header');
                        if (header) {
                            header.click();
                        } else {
                            container.classList.toggle('collapsed', desiredCollapsed);
                        }
                    }
                });
            })();
        """.trimIndent()

        internal fun readerTextScaleCssValue(scale: Float): String =
            String.format(Locale.US, "%.2f", ReaderTextScale.clamp(scale))
    }
}
