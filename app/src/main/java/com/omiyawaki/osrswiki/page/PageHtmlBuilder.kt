package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.theme.Theme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

class PageHtmlBuilder(private val context: Context) {

    private val styleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/fonts.css", // Added fonts
        "styles/layout.css",
        "styles/components.css",
        "styles/navbox_styles.css",
        "web/collapsible_tables.css",
        JavaScriptActionHandler.getInfoboxSwitcherCssPath(), // Added infobox styles
        "styles/fixes.css"
    )

    private val jsAssetPaths = listOf(
        "js/tablesort.min.js",
        "web/collapsible_content.js",
        JavaScriptActionHandler.getInfoboxSwitcherBootstrapJsPath(),
        JavaScriptActionHandler.getInfoboxSwitcherJsPath(),
        "web/horizontal_scroll_interceptor.js"
    )

    private val allInlinedCss: String by lazy {
        styleSheetAssets.joinToString(separator = "\n") { assetPath ->
            readAsset(assetPath)
        }
    }

    private val allInlinedJs: String by lazy {
        jsAssetPaths.joinToString(separator = "\n") { assetPath ->
            readAsset(assetPath)
        }
    }

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
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

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${documentTitle}</title>
                <style>
                    ${allInlinedCss}
                </style>
            </head>
            <body class="$themeClass" style="visibility: hidden;">
                ${finalBodyContent}
                <script>
                    ${allInlinedJs}
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun readAsset(assetPath: String): String {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: IOException) {
            android.util.Log.e("PageHtmlBuilder", "Failed to read asset: $assetPath", e)
            ""
        }
    }
}
