package com.omiyawaki.osrswiki.page

import android.net.Uri

/**
 * Rewrites local WebView origins to the live wiki for calculator, CORS, and
 * ResourceLoader requests. App articles load from appassets.androidplatform.net,
 * so gadget-relative `/api.php` and `/cors/` would otherwise 404 locally.
 */
object osrsWikiWebViewUrl {
    const val WIKI_HOST = "oldschool.runescape.wiki"
    const val WIKI_ORIGIN = "https://oldschool.runescape.wiki"
    const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"

    fun shouldProxy(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path ?: return false
        if (host != LOCAL_ASSET_HOST && host != "localhost") {
            return false
        }
        return path == "/api.php" ||
            path.endsWith("/api.php") ||
            path.startsWith("/cors/") ||
            path == "/load.php" ||
            path.endsWith("/load.php")
    }

    fun rewriteToWiki(url: String): String {
        return try {
            val uri = Uri.parse(url)
            if (!shouldProxy(uri)) {
                url
            } else {
                uri.buildUpon()
                    .scheme("https")
                    .encodedAuthority(WIKI_HOST)
                    .build()
                    .toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    fun isCalculatorNamespaceTitle(title: String): Boolean {
        return title.startsWith("Calculator:")
    }

    fun isUserFacingCalculator(title: String): Boolean {
        if (!isCalculatorNamespaceTitle(title)) {
            return false
        }
        if (title.contains("sandbox", ignoreCase = true)) {
            return false
        }
        val rest = title.removePrefix("Calculator:")
        return rest.split('/').none { part ->
            val loweredPart = part.lowercase()
            loweredPart.startsWith("template") ||
                loweredPart == "doc" ||
                loweredPart == "sandbox" ||
                loweredPart == "module"
        }
    }

    fun isIncludedInDefaultSearch(title: String): Boolean {
        return !isCalculatorNamespaceTitle(title) || isUserFacingCalculator(title)
    }

    data class osrsWikiPageConfig(
        val namespaceNumber: Int,
        val canonicalNamespace: String,
        val pageName: String,
        val title: String
    )

    fun mediaWikiPageConfig(canonicalTitle: String, displayTitle: String): osrsWikiPageConfig {
        val source = canonicalTitle.ifBlank { displayTitle }
        return if (isCalculatorNamespaceTitle(source)) {
            osrsWikiPageConfig(
                namespaceNumber = 116,
                canonicalNamespace = "Calculator",
                pageName = source.replace(" ", "_"),
                title = source.removePrefix("Calculator:")
            )
        } else {
            val display = displayTitle.ifBlank { source }
            osrsWikiPageConfig(
                namespaceNumber = 0,
                canonicalNamespace = "",
                pageName = display.replace(" ", "_"),
                title = display
            )
        }
    }
}
