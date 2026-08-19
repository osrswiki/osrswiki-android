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

    // App-specific stylesheets (preserved from working version)
    private val styleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/fonts.css",
        "styles/layout.css",
        "styles/components.css",
        "styles/wiki-integration.css",
        "styles/navbox_styles.css",                         // Restored: Navbox styling
        JavaScriptActionHandler.getCollapsibleTableCssPath(), // Restored: Collapsible tables CSS
        "web/collapsible_sections.css",                 // Restored: Collapsible sections CSS
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(), // Restored: Infobox switcher CSS
        "styles/fixes.css",
        "styles/android-article-aesthetics.css"
    )

    // Simple MediaWiki ResourceLoader - let it work naturally
    private val mediawikiArtifacts = listOf(
        "startup.js"                                    // Core MediaWiki module loader - RLPAGEMODULES now inlined above
    )
    
    private val articleTransformJsAssetPaths = listOf(
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
        "web/collapsible_content.js",
        "web/mobile_article_polish.js",
        "web/horizontal_scroll_interceptor.js"
    )

    // Base JavaScript assets (before conditional GE charts addition)
    private val jsAssetPaths = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js",
        "web/tabber_init.js",
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
                    if (!body) return;
                    
                    // Remove existing theme classes
                    body.classList.remove('theme-osrs-dark');
                    
                    // Add dark theme class if needed
                    if (isDark) {
                        body.classList.add('theme-osrs-dark');
                    }
                    
                    // Force immediate style recalculation
                    body.offsetHeight;
                    
                    // Ensure page remains visible after theme change
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
                window.OSRS_ANDROID_DISCLOSURE_CHROME = true;
                console.log('PageHtmlBuilder: Set global collapse preference to ' + window.OSRS_TABLE_COLLAPSED);
            </script>
        """.trimIndent()
    }

    /**
     * Generate smart MediaWiki variables based on page content.
     * Uses WikiModuleRegistry for intelligent module detection.
     */
    private fun generateMediaWikiVariables(title: String, bodyContent: String): String {
        // Generate smart RLPAGEMODULES based on content analysis
        val detectedModules = WikiModuleRegistry.generateRLPAGEMODULES(bodyContent, title)
        val modulesList = detectedModules.joinToString(", ") { "\"$it\"" }
        
        // Use page title for MediaWiki variables
        val safetitle = title.replace("\"", "\\\"")
        
        return """
            <script>
                // Smart MediaWiki variables generated based on page content
                // Module detection via WikiModuleRegistry for scalable maintenance
                var RLCONF = {"wgBreakFrames": false, "wgSeparatorTransformTable": ["", ""], "wgDigitTransformTable": ["", ""], "wgDefaultDateFormat": "dmy", "wgMonthNames": ["", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"], "wgRequestId": "smart-module-loader", "wgCanonicalNamespace": "", "wgCanonicalSpecialPageName": false, "wgNamespaceNumber": 0, "wgPageName": "$safetitle", "wgTitle": "$safetitle", "wgCurRevisionId": 0, "wgRevisionId": 0, "wgArticleId": 1, "wgIsArticle": true, "wgIsRedirect": false, "wgAction": "view", "wgUserName": null, "wgUserGroups": ["*"], "wgPageViewLanguage": "en-gb", "wgPageContentLanguage": "en-gb", "wgPageContentModel": "wikitext", "wgRelevantPageName": "$safetitle", "wgRelevantArticleId": 1, "wgIsProbablyEditable": true, "wgRelevantPageIsProbablyEditable": true, "wgRestrictionEdit": [], "wgRestrictionMove": [], "wgServer": "https://oldschool.runescape.wiki", "wgServerName": "oldschool.runescape.wiki", "wgScriptPath": "", "wgScript": "/load.php"};
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
        readerTextScale: Float = Prefs.readerTextScale
    ): String {
        var finalHtml: String
        val floorClass = osrsArticleFloorConvention.resolved(deviceLocale()).bodyClass
        val wrapClass = if (Prefs.wrapTableCells) "osrs-table-cells-wrap" else ""
        val time = measureTimeMillis {
            // Preserved title logic from working version
            val cleanedTitle = StringUtil.extractMainTitle(title)
            val documentTitle = if (cleanedTitle.isBlank()) "OSRS Wiki" else cleanedTitle
            val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
            
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
                Log.d(logTag, "Detected GE chart markers in content; will include highcharts widget script.")
            }

            val cssLinks = styleSheetAssets.joinToString("\n") { assetPath ->
                "<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/$assetPath\">"
            }

            Log.d(logTag, "Using natural MediaWiki ResourceLoader with network-level caching")

            // Natural MediaWiki loading: Only startup.js, let ResourceLoader handle everything else
            // Network interceptor will cache load.php responses as needed
            
            val mediawikiScripts = mediawikiArtifacts.joinToString("\n") { assetPath ->
                "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
            }
            
            // Build the JS list, conditionally appending the GE charts widget
            val dynamicJsAssets = if (needsGECharts) {
                jsAssetPaths + listOf(
                    "web/highcharts-stock.js",
                    "web/ge_charts_init.js"
                )
            } else jsAssetPaths

            val jsScripts = dynamicJsAssets.joinToString("\n") { assetPath ->
                val tag = "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
                if (assetPath.endsWith("highcharts-stock.js")) {
                    // Highcharts' UMD build prefers AMD. MediaWiki defines `define`, so
                    // window.Highcharts never appears and the chart stays on "Loading...".
                    "<script>window.__osrsAmdDefine=window.define;try{window.define=undefined;}catch(e){}</script>\n$tag\n<script>if(typeof window.__osrsAmdDefine!=='undefined'){window.define=window.__osrsAmdDefine;}</script>"
                } else {
                    tag
                }
            }
            val transformScripts = articleTransformJsAssetPaths.joinToString("\n") { assetPath ->
                "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
            }
            val androidDisclosureChrome = """
                <style id="osrs-android-disclosure-chrome">
                .collapsible-header, .collapsible-close-button {
                    background-color: var(--body-mid, #d0bd97) !important;
                    box-sizing: border-box !important;
                    flex-shrink: 0 !important;
                    height: auto !important;
                    min-height: 64px !important;
                    padding: 16px 16px !important;
                }
                </style>
                <script>
                (function () {
                    function osrsApplyAndroidDisclosureChrome() {
                        document.querySelectorAll('.collapsible-header, .collapsible-close-button').forEach(function (el) {
                            el.style.setProperty('box-sizing', 'border-box', 'important');
                            el.style.setProperty('flex-shrink', '0', 'important');
                            el.style.setProperty('height', 'auto', 'important');
                            el.style.setProperty('min-height', '64px', 'important');
                            el.style.setProperty('padding', '16px 16px', 'important');
                            el.style.setProperty('background-color', 'var(--body-mid, #d0bd97)', 'important');
                        });
                    }
                    osrsApplyAndroidDisclosureChrome();
                    if (window.mw && mw.hook) {
                        mw.hook('wikipage.content').add(osrsApplyAndroidDisclosureChrome);
                    }
                    document.addEventListener('DOMContentLoaded', osrsApplyAndroidDisclosureChrome);
                })();
                </script>
            """.trimIndent()
            
            // Generate smart MediaWiki variables
            val smartMediawikiVariables = generateMediaWikiVariables(cleanedTitle, cleanedBodyContent)

            // Create table collapse preference script
            val tableCollapseScript = createTableCollapseScript(collapseTablesEnabled)
            val readerTextScaleBootstrap = readerTextScaleBootstrap(readerTextScale)

            // Preload the heading face used by h1.page-header so first paint
            // does not wait for a late @font-face swap that restyles the title.
            val fontPreloadLink = """
                <link rel="preload" href="https://appassets.androidplatform.net/res/font/alegreya_bold.ttf" as="font" type="font/ttf" crossorigin="anonymous">
                <link rel="preload" href="https://appassets.androidplatform.net/res/font/runescape_plain.ttf" as="font" type="font/ttf" crossorigin="anonymous">
            """.trimIndent()
            val articleFirstPaintStyle = articleFirstPaintStyle()

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
                .append("<html class=\"").append(wrapClass).append("\">\n")
                .append("<head>\n")
                .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">\n")
                .append("    <title>").append(documentTitle).append("</title>\n")
                .append("    ").append(articleFirstPaintStyle).append('\n')
                .append("    ").append(fontPreloadLink).append('\n')
                .append("    ").append(cssLinks).append('\n')
                .append("    ").append(readerTextScaleBootstrap).append('\n')
                .append("    ").append(themeUtilityScript).append('\n')
                .append("    ").append(tableCollapseScript).append('\n')
                .append("    ").append(smartMediawikiVariables).append('\n')
                .append("    ").append(androidDisclosureChrome).append('\n')
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
        return finalHtml
    }

    private fun deviceLocale(): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.size() > 0) locales[0] else Locale.getDefault()
    }
    
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
        if (htmlContent.contains("mw-body-content")) return htmlContent
        return """<div class="mw-body-content">$htmlContent</div>"""
    }

    companion object {
        private const val READER_STYLE_ID = "osrs-reader-text-scale-style"
        private const val READER_SCALE_VARIABLE = "--osrs-article-user-text-scale"

        internal fun articleFirstPaintStyle(chromeClearancePx: Int = 0): String {
            val chromePadding = if (chromeClearancePx > 0) {
                """
                    html {
                        padding-top: calc(env(safe-area-inset-top, 0px) + ${chromeClearancePx}px) !important;
                        padding-bottom: calc(env(safe-area-inset-bottom, 0px) + ${chromeClearancePx}px) !important;
                    }
                """.trimIndent()
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
                        min-height: 1.3em;
                        border-bottom: 1px solid var(--sidebar-color, currentColor);
                        box-sizing: border-box;
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
