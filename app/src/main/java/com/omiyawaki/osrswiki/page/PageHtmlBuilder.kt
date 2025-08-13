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

    // NOTE: MediaWiki artifacts removed - using direct wiki page loading instead
    
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

    // NOTE: MediaWiki variables removed - wiki will provide them when loading directly

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

            Log.d(logTag, "Using direct wiki page loading - no local MediaWiki artifacts needed")
            
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
                </head>
                <body class="$themeClass" style="visibility: hidden;">
                    ${finalBodyContent}
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