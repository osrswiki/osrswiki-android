package com.omiyawaki.osrswiki.page

import android.net.Uri

/**
 * Rewrites local WebView origins to the live wiki for calculator, CORS, and
 * ResourceLoader requests. App articles load from appassets.androidplatform.net,
 * so relative `/api.php` and `/cors/` would otherwise 404 locally.
 *
 * MediaWiki gadget modules are never fetched. Wiki gadget JavaScript is not FLOSS
 * and is not bundled or executed.
 */
object osrsWikiWebViewUrl {
    const val WIKI_HOST = "oldschool.runescape.wiki"
    const val WIKI_ORIGIN = "https://oldschool.runescape.wiki"
    const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
    const val REJECTED_GADGET_LOAD_JS = "/* osrs: MediaWiki gadgets are not loaded */\n"

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

    fun isMediaWikiGadgetModule(name: String): Boolean {
        return name.trim().startsWith("ext.gadget.")
    }

    fun withoutMediaWikiGadgets(modules: Iterable<String>): List<String> {
        return modules.filterNot { isMediaWikiGadgetModule(it) }
    }

    fun loadPhpModuleNames(url: String): List<String> {
        return try {
            val modules = Uri.parse(url).getQueryParameter("modules") ?: return emptyList()
            modules.split('|', ',').map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isRejectedMediaWikiGadgetLoad(url: String): Boolean {
        if (!url.contains("/load.php")) {
            return false
        }
        val modules = loadPhpModuleNames(url)
        return modules.isNotEmpty() && modules.all { isMediaWikiGadgetModule(it) }
    }

    /**
     * Drop `ext.gadget.*` from a load.php URL. Returns null when nothing FLOSS
     * remains to fetch.
     */
    fun withoutMediaWikiGadgetLoadModules(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val path = uri.path ?: return url
            if (path != "/load.php" && !path.endsWith("/load.php")) {
                return url
            }
            val modules = loadPhpModuleNames(url)
            if (modules.isEmpty()) {
                return url
            }
            val kept = withoutMediaWikiGadgets(modules)
            if (kept.size == modules.size) {
                return url
            }
            if (kept.isEmpty()) {
                return null
            }
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames.forEach { name ->
                if (name == "modules") {
                    builder.appendQueryParameter("modules", kept.joinToString("|"))
                } else {
                    uri.getQueryParameters(name).forEach { value ->
                        builder.appendQueryParameter(name, value)
                    }
                }
            }
            builder.build().toString()
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
