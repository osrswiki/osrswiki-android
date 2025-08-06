package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.StringUtil
import kotlin.system.measureTimeMillis

class PageHtmlBuilder(private val context: Context) {

    private val logTag = "PageLoadTrace"

    private val styleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/fonts.css",
        "styles/layout.css",
        "styles/components.css",
        "styles/navbox_styles.css",
        "web/collapsible_tables.css",
        "web/collapsible_sections.css",
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(),
        "styles/fixes.css"
    )

    private val jsAssetPaths = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js", // Add the initialization script
        "web/collapsible_content.js",
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
        "web/horizontal_scroll_interceptor.js",
        "web/responsive_videos.js", // Make video embeds responsive
        "web/clipboard_bridge.js", // Android clipboard bridge for iframe support
        "web/clipboard_debug.js" // Debug clipboard API availability
    )

    private val timelineLoggerScript = """
        <script>
            document.addEventListener('DOMContentLoaded', function() {
                if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
                    window.RenderTimeline.log('Event: DOMContentLoaded');
                }
            });
            window.addEventListener('load', function() {
                if (window.RenderTimeline && typeof window.RenderTimeline.log === 'function') {
                    window.RenderTimeline.log('Event: window.load');
                }
            });
        </script>
    """.trimIndent()

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        var finalHtml: String
        val time = measureTimeMillis {
            val cleanedTitle = StringUtil.extractMainTitle(title)
            val documentTitle = if (cleanedTitle.isBlank()) "OSRS Wiki" else cleanedTitle
            val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
            
            // Clean any existing page-header titles from bodyContent to prevent duplication
            val cleanedBodyContent = removeDuplicatePageHeaders(bodyContent)
            val finalBodyContent = titleHeaderHtml + cleanedBodyContent
            val themeClass = when (theme) {
                Theme.OSRS_DARK -> "theme-osrs-dark"
                Theme.WIKI_LIGHT -> "theme-wikipedia-light"
                Theme.WIKI_DARK -> "theme-wikipedia-dark"
                Theme.WIKI_BLACK -> "theme-wikipedia-black"
                else -> "" // OSRS Light is the default theme in CSS, no class needed.
            }

            val cssLinks = styleSheetAssets.joinToString("\n") { assetPath ->
                // The URL must match the path handled by WebViewAssetLoader.
                "<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/$assetPath\">"
            }

            val jsScripts = jsAssetPaths.joinToString("\n") { assetPath ->
                // The URL must match the path handled by WebViewAssetLoader.
                "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>"
            }

            // Experiment: Preload the main web font to improve rendering performance.
            val fontPreloadLink = "<link rel=\"preload\" href=\"https://appassets.androidplatform.net/res/font/runescape_plain.ttf\" as=\"font\" type=\"font/ttf\" crossorigin=\"anonymous\">"

            finalHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${documentTitle}</title>
                    ${fontPreloadLink}
                    ${cssLinks}
                </head>
                <body class="$themeClass" style="visibility: hidden;">
                    ${finalBodyContent}
                    ${jsScripts}
                    ${timelineLoggerScript}
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
            // This is more efficient than parsing the entire HTML with Jsoup for this simple operation
            val pageHeaderRegex = Regex("<h1\\s+class=\"page-header\"[^>]*>.*?</h1>", RegexOption.DOT_MATCHES_ALL)
            htmlContent.replace(pageHeaderRegex, "")
        } catch (e: Exception) {
            Log.e(logTag, "Error removing duplicate page headers", e)
            htmlContent // Return original content if cleaning fails
        }
    }
}
