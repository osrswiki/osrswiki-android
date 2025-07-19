package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.theme.Theme
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
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(),
        "styles/fixes.css"
    )

    private val jsAssetPaths = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js", // Add the initialization script
        "web/collapsible_content.js",
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
        "web/horizontal_scroll_interceptor.js"
    )

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        var finalHtml: String
        val time = measureTimeMillis {
            val documentTitle = if (title.isBlank()) "OSRS Wiki" else title
            val titleHeaderHtml = "<h1 class=\"page-header\">${documentTitle}</h1>"
            val finalBodyContent = titleHeaderHtml + bodyContent
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
                </body>
                </html>
            """.trimIndent()
        }
        Log.d(logTag, "buildFullHtmlDocument() took ${time}ms")
        return finalHtml
    }
}
