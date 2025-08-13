package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.StringUtil
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
        "web/app/collapsible_sections.css",                 // Restored: Collapsible sections CSS
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(), // Restored: Infobox switcher CSS
        "styles/fixes.css"
    )

    // Simple MediaWiki ResourceLoader - let it work naturally
    private val mediawikiArtifacts = listOf(
        "startup.js"                                    // Core MediaWiki module loader - RLPAGEMODULES now inlined above
    )
    
    // App-specific utility scripts (preserved from working version)
    private val appSpecificModules = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js",
        "web/app/collapsible_content.js",
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(), // Restored: Infobox switcher bootstrap
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),          // Restored: Infobox switcher main script
        "web/app/horizontal_scroll_interceptor.js",
        "web/app/responsive_videos.js",
        "web/app/clipboard_bridge.js"
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

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        var finalHtml: String
        val time = measureTimeMillis {
            // Preserved title logic from working version
            val cleanedTitle = StringUtil.extractMainTitle(title)
            val documentTitle = if (cleanedTitle.isBlank()) "OSRS Wiki" else cleanedTitle
            val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
            
            // Clean any existing page-header titles from bodyContent to prevent duplication
            val cleanedBodyContent = removeDuplicatePageHeaders(bodyContent)
            val finalBodyContent = titleHeaderHtml + cleanedBodyContent
            val themeClass = when (theme) {
                Theme.OSRS_DARK -> "theme-osrs-dark"
                else -> "" // OSRS Light is the default theme in CSS, no class needed.
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
            
            // App-specific scripts (preserved from working version)
            val appScripts = appSpecificModules.joinToString("\n") { assetPath ->
                "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
            }
            
            // Generate smart MediaWiki variables
            val smartMediawikiVariables = generateMediaWikiVariables(cleanedTitle, cleanedBodyContent)

            // Preload the main web font to improve rendering performance
            val fontPreloadLink = "<link rel=\"preload\" href=\"https://appassets.androidplatform.net/res/font/runescape_plain.ttf\" as=\"font\" type=\"font/ttf\" crossorigin=\"anonymous\">"

            // FOUC fix (preserved from working version)
            val foucFix = "<script>document.body.style.visibility = 'visible';</script>"

            finalHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${documentTitle}</title>
                    ${fontPreloadLink}
                    ${cssLinks}
                    ${themeUtilityScript}
                    ${smartMediawikiVariables}
                </head>
                <body class="$themeClass" style="visibility: hidden;">
                    ${finalBodyContent}
                    ${mediawikiScripts}
                    ${appScripts}
                    ${foucFix}
                </body>
                </html>
            """.trimIndent()
        }
        Log.d(logTag, "buildFullHtmlDocument() took ${time}ms")
        return finalHtml
    }
    
    /**
     * Removes any existing page-header titles from HTML content to prevent duplication.
     * This is useful for cleaning content that may have been processed multiple times.
     */
    private fun removeDuplicatePageHeaders(htmlContent: String): String {
        return try {
            // Use a regex to remove h1 elements with class="page-header"
            val pageHeaderRegex = Regex("<h1\\s+class=\"page-header\"[^>]*>.*?</h1>", RegexOption.DOT_MATCHES_ALL)
            htmlContent.replace(pageHeaderRegex, "")
        } catch (e: Exception) {
            Log.e(logTag, "Error removing duplicate page headers", e)
            htmlContent // Return original content if cleaning fails
        }
    }
}