package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import java.net.URLDecoder // For decoding path segments if necessary, though often not needed if just extracting

abstract class LinkHandler(protected val context: Context) {

    companion object {
        // Constants derived from URL analysis
        private const val WIKI_SCHEME_HTTPS = "https"
        private const val WIKI_SCHEME_HTTP = "http" // Allow http as well for flexibility
        private const val WIKI_HOST = "oldschool.runescape.wiki"
        private const val WIKI_ARTICLE_PATH_PREFIX = "/w/"
    }

    /**
     * Main entry point for processing a clicked URI.
     * It normalizes the URI and then determines if it's an internal or external link.
     */
    fun processUri(uri: Uri) {
        val processedUri = normalizeUri(uri)

        if (isInternalWikiLink(processedUri)) {
            val articleTitle = extractArticleTitleFromInternalLink(processedUri)
            if (articleTitle != null && articleTitle.isNotBlank()) {
                onInternalArticleLinkClicked(articleTitle, processedUri)
            } else {
                // An internal link, but not a standard article or title extraction failed.
                // This could be the main page itself (e.g. /w/ with no further segment) or other special cases.
                onNonArticleInternalLinkClicked(processedUri)
            }
        } else {
            onExternalLinkClicked(processedUri)
        }
    }

    /**
     * Normalizes a URI.
     * Ensures a scheme is present for scheme-relative URLs (e.g., "//domain.com").
     */
    protected open fun normalizeUri(uri: Uri): Uri {
        var newUri = uri
        val schemeSpecificPart = uri.encodedSchemeSpecificPart ?: ""

        if (newUri.scheme == null) {
            if (schemeSpecificPart.startsWith("//")) {
                // Handle scheme-relative URLs like //oldschool.runescape.wiki/w/Item
                newUri = Uri.parse("$WIKI_SCHEME_HTTPS:$schemeSpecificPart")
            } else if (newUri.authority != null) {
                // If scheme is missing but authority is present, default to https
                newUri = newUri.buildUpon().scheme(WIKI_SCHEME_HTTPS).build()
            }
            // Note: True relative paths (e.g., "Some_Page" or "./Some_Page") would require a base URI
            // from the current page to be resolved properly. For now, this focuses on
            // absolute or scheme-relative URIs typically intercepted by shouldOverrideUrlLoading.
        }
        return newUri
    }

    /**
     * Determines if the given URI is an internal link to the OSRS wiki.
     *
     * @param uri The normalized URI to check.
     * @return `true` if it's an internal wiki link, `false` otherwise.
     */
    protected open fun isInternalWikiLink(uri: Uri): Boolean {
        val scheme = uri.scheme
        val host = uri.host
        val path = uri.path

        return (scheme == WIKI_SCHEME_HTTPS || scheme == WIKI_SCHEME_HTTP) &&
                host.equals(WIKI_HOST, ignoreCase = true) &&
                path != null && path.startsWith(WIKI_ARTICLE_PATH_PREFIX)
    }

    /**
     * Extracts the article title (with underscores) from a recognized internal OSRS wiki link.
     *
     * @param uri The normalized internal wiki link URI.
     * @return The extracted article title (e.g., "Rune_scimitar"), or `null` if a title cannot be determined.
     */
    protected open fun extractArticleTitleFromInternalLink(uri: Uri): String? {
        val path = uri.path ?: return null

        if (path.startsWith(WIKI_ARTICLE_PATH_PREFIX) && path.length > WIKI_ARTICLE_PATH_PREFIX.length) {
            val titleWithPotentialFragment = path.substring(WIKI_ARTICLE_PATH_PREFIX.length)
            // The title is the part before any fragment ('#')
            // Titles themselves can contain characters that look like query params but are part of the path.
            // We typically don't URL-decode the path segment here for MediaWiki titles,
            // as underscores and parentheses are part of the canonical title in the URL.
            // Decoding (e.g. for %20 to space) is usually done when *displaying* the title.
            return titleWithPotentialFragment.substringBefore('#') // Remove fragment if present
        }
        return null
    }

    /**
     * Called when a link is identified as an internal wiki article.
     * The consuming class will replace underscores with spaces if needed for display.
     *
     * @param articleTitle The title of the internal article (e.g., "Rune_scimitar").
     * @param fullUri The complete URI that was clicked.
     */
    abstract fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri)

    /**
     * Called when a link is identified as external to the wiki.
     *
     * @param uri The URI of the external link.
     */
    abstract fun onExternalLinkClicked(uri: Uri)

    /**
     * Called for links that are identified as internal to the wiki but are not standard articles
     * (e.g., the main page at /w/, special pages, categories, or if title extraction failed).
     * The default implementation treats these as external links.
     *
     * @param uri The URI of the non-article internal link.
     */
    open fun onNonArticleInternalLinkClicked(uri: Uri) {
        // Default: treat as external, or log, or implement specific handling in subclass.
        // For example, you might want to navigate to a special "page not found" or "unsupported link"
        // screen within the app, or simply open it externally.
        onExternalLinkClicked(uri)
    }
}