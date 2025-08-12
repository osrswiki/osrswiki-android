package com.omiyawaki.osrswiki.page

import android.content.Context
import com.omiyawaki.osrswiki.theme.Theme

class PageHtmlBuilder(private val context: Context) {

    // App-specific stylesheets
    private val styleSheetAssets = listOf(
        "styles/themes.css",
        "styles/base.css",
        "styles/fonts.css",
        "styles/layout.css",
        "styles/components.css",
        "styles/wiki-integration.css",
        "styles/fixes.css"
    )

    // App-specific utility scripts that run after the MediaWiki environment is established.
    private val appSpecificModules = listOf(
        "js/tablesort.min.js",
        "js/tablesort_init.js",
        "web/app/collapsible_content.js",
        "web/app/horizontal_scroll_interceptor.js",
        "web/app/responsive_videos.js",
        "web/app/clipboard_bridge.js"
    )

    // Captured MediaWiki artifacts. The loading order is critical.
    private val mediawikiArtifacts = listOf(
        "web/mediawiki/startup.js",         // 1. The core ResourceLoader engine.
        "web/mediawiki/page_bootstrap.js",  // 2. Page-specific config (RLCONF, RLSTATE, RLPAGEMODULES).
        "web/mediawiki/page_modules.js"     // 3. The bundle of modules for the page.
    )

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        val documentTitle = title.ifBlank { "OSRS Wiki" }
        val themeClass = if (theme == Theme.OSRS_DARK) "theme-osrs-dark" else ""

        val cssLinks = styleSheetAssets.joinToString("\n") { assetPath ->
            "<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/$assetPath">".trimIndent()
        }

        // Load the captured MediaWiki engine first.
        val mediawikiScripts = mediawikiArtifacts.joinToString("\n") { assetPath ->
            "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>".trimIndent()
        }

        // Load our app-specific utilities last.
        val appScripts = appSpecificModules.joinToString("\n") { assetPath ->
            "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>".trimIndent()
        }

        // A simple script to prevent Flash of Unstyled Content (FOUC).
        val foucFix = "<script>document.body.style.visibility = 'visible';</script>"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$documentTitle</title>
                $cssLinks
                $mediawikiScripts
            </head>
            <body class=\"$themeClass\" style=\"visibility: hidden;">
                $bodyContent
                $appScripts
                $foucFix
            </body>
            </html>
        """.trimIndent()
    }
}