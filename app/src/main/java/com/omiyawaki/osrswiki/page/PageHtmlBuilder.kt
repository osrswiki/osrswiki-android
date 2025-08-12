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
        "web/mediawiki/shims/dependency_normalizer.js", // 1. Dependency normalization shim.
        "web/mediawiki/startup.js",                     // 2. The core ResourceLoader engine.
        "web/mediawiki/shims/package_module_executor.js", // 3. Package module execution shim.
        "web/mediawiki/page_bootstrap.js",              // 4. Page-specific config (RLCONF, RLSTATE, RLPAGEMODULES).
        "web/mediawiki/page_modules.js"                 // 5. The bundle of modules for the page.
    )

    fun buildFullHtmlDocument(title: String, bodyContent: String, theme: Theme): String {
        val documentTitle = title.ifBlank { "OSRS Wiki" }
        val themeClass = if (theme == Theme.OSRS_DARK) "theme-osrs-dark" else ""

        // Corrected: Removed unnecessary escaping from string literals.
        val cssLinks = styleSheetAssets.joinToString("\n") { assetPath ->
            "<link rel=\"stylesheet\" href=\"https://appassets.androidplatform.net/assets/$assetPath\">".trimIndent()
        }

        val mediawikiScripts = mediawikiArtifacts.joinToString("\n") { assetPath ->
            "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>".trimIndent()
        }

        val appScripts = appSpecificModules.joinToString("\n") { assetPath ->
            "<script src=\"https://appassets.androidplatform.net/assets/$assetPath\"></script>".trimIndent()
        }

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
