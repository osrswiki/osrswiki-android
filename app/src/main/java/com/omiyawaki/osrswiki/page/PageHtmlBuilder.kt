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

    private val mediawikiVariables = """
        <script>
            // MediaWiki variables - must be defined BEFORE startup.js loads
            // This fixes the loading order issue where startup.js runs before RLPAGEMODULES is defined
            var RLCONF = {"wgBreakFrames": false, "wgSeparatorTransformTable": ["", ""], "wgDigitTransformTable": ["", ""], "wgDefaultDateFormat": "dmy", "wgMonthNames": ["", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"], "wgRequestId": "56a1e1798881a7699726f1d86607870f", "wgCanonicalNamespace": "", "wgCanonicalSpecialPageName": false, "wgNamespaceNumber": 0, "wgPageName": "Logs", "wgTitle": "Logs", "wgCurRevisionId": 14959299, "wgRevisionId": 14959299, "wgArticleId": 10005, "wgIsArticle": true, "wgIsRedirect": false, "wgAction": "view", "wgUserName": null, "wgUserGroups": ["*"], "wgPageViewLanguage": "en-gb", "wgPageContentLanguage": "en-gb", "wgPageContentModel": "wikitext", "wgRelevantPageName": "Logs", "wgRelevantArticleId": 10005, "wgIsProbablyEditable": true, "wgRelevantPageIsProbablyEditable": true, "wgRestrictionEdit": [], "wgRestrictionMove": [], "simpleBatchUploadMaxFilesPerBatch": {"*": 1000}, "wgMediaViewerOnClick": true, "wgMediaViewerEnabledByDefault": true, "wgCiteReferencePreviewsActive": false, "wgVisualEditor": {"pageLanguageCode": "en-GB", "pageLanguageDir": "ltr", "pageVariantFallbacks": "en-gb"}, "wgMFMode": "stable", "wgMFAmc": false, "wgMFAmcOutreachActive": false, "wgMFAmcOutreachUserEligible": false, "wgMFLazyLoadImages": false, "wgMFEditNoticesFeatureConflict": false, "wgMFDisplayWikibaseDescriptions": {"search": false, "watchlist": false, "tagline": false}, "wgMFIsSupportedEditRequest": true, "wgMFScriptPath": "", "wgPopupsFlags": 0, "wgKartographerLiveData": {}, "wgEditSubmitButtonLabelPublish": false, "wgCheckUserClientHintsHeadersJsApi": ["architecture", "bitness", "brands", "fullVersionList", "mobile", "model", "platform", "platformVersion"], "wgMinervaPermissions": {"watchable": true, "watch": false}, "wgMinervaFeatures": {"beta": false, "donate": false, "mobileOptionsLink": true, "categories": false, "pageIssues": true, "talkAtTop": false, "historyInPageActions": false, "overflowSubmenu": false, "tabsOnSpecials": true, "personalMenu": false, "mainMenuExpanded": false, "echo": true, "nightMode": false}, "wgMinervaDownloadNamespaces": [0], "wgServer": "https://oldschool.runescape.wiki", "wgServerName": "oldschool.runescape.wiki", "wgScriptPath": "", "wgScript": "/load.php"};
            var RLSTATE = {"ext.gadget.switch-infobox-styles": "ready", "ext.gadget.articlefeedback-styles": "ready", "ext.gadget.falseSubpage": "ready", "ext.gadget.headerTargetHighlight": "ready", "site.styles": "ready", "user.styles": "ready", "user": "ready", "user.options": "loading", "ext.cite.styles": "ready", "ext.kartographer.style": "ready", "skins.minerva.base.styles": "ready", "skins.minerva.content.styles.images": "ready", "mediawiki.hlist": "ready", "skins.minerva.codex.styles": "ready", "skins.minerva.icons.wikimedia": "ready", "skins.minerva.mainMenu.icons": "ready", "skins.minerva.mainMenu.styles": "ready", "jquery.tablesorter.styles": "ready", "ext.embedVideo.styles": "ready", "mobile.init.styles": "ready"};
            var RLPAGEMODULES = ["ext.cite.ux-enhancements", "ext.kartographer.link", "ext.scribunto.logs", "site", "mediawiki.page.ready", "jquery.tablesorter", "skins.minerva.scripts", "ext.gadget.rsw-util", "ext.gadget.switch-infobox", "ext.gadget.exchangePages", "ext.gadget.GECharts", "ext.gadget.highlightTable", "ext.gadget.titleparenthesis", "ext.gadget.tooltips", "ext.gadget.Username", "ext.gadget.countdown", "ext.gadget.checkboxList", "ext.gadget.Charts", "ext.gadget.navbox-tracking", "ext.gadget.wikisync", "ext.gadget.smwlistsfull", "ext.gadget.jsonDoc", "ext.gadget.articlefeedback", "ext.gadget.calc", "ext.gadget.calculatorNS", "ext.gadget.dropDisplay", "ext.gadget.mmgkc", "ext.gadget.fightcaverotations", "ext.gadget.livePricesMMG", "ext.gadget.skinTogglesMobile", "ext.gadget.relativetime", "ext.gadget.navboxToggle", "ext.gadget.audioplayer", "ext.gadget.musicmap", "ext.gadget.equipment", "ext.gadget.fileDownload", "ext.gadget.oswf", "ext.gadget.tilemarkers", "ext.gadget.loadout", "ext.gadget.leaguefilter", "ext.embedVideo.overlay", "mobile.init", "ext.checkUser.clientHints", "ext.popups", "ext.smw.purge"];
        </script>
    """.trimIndent()

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
                    ${mediawikiVariables}
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