package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import com.omiyawaki.osrswiki.util.log.L // Assuming L is your logging utility
import java.net.URLDecoder // For decoding path segments if necessary

abstract class LinkHandler(protected val context: Context) {

    companion object {
        private const val WIKI_SCHEME_HTTPS = "https"
        private const val WIKI_HOST = "oldschool.runescape.wiki"
        private const val WIKI_ARTICLE_PATH_PREFIX = "/w/"
        private const val WIKI_INDEX_PHP_PREFIX = "/index.php"
    }

    fun processUri(uri: Uri) {
        val processedUri = normalizeUri(uri)

        if (isInternalWikiLink(processedUri)) {
            val articleTitle = extractArticleTitleFromInternalLink(processedUri)
            if (articleTitle != null && articleTitle.isNotBlank()) {
                onInternalArticleLinkClicked(articleTitle, processedUri)
            } else {
                onNonArticleInternalLinkClicked(processedUri)
            }
        } else {
            onExternalLinkClicked(processedUri)
        }
    }

    protected open fun normalizeUri(uri: Uri): Uri {
        var newUri = uri
        val scheme = uri.scheme
        val authority = uri.authority
        val path = uri.path ?: ""
        val encodedSchemeSpecificPart = uri.encodedSchemeSpecificPart ?: ""

        if (scheme == null) {
            if (encodedSchemeSpecificPart.startsWith("//")) {
                newUri = Uri.parse("$WIKI_SCHEME_HTTPS:$encodedSchemeSpecificPart")
            } else if (authority != null) {
                newUri = newUri.buildUpon().scheme(WIKI_SCHEME_HTTPS).build()
            } else if (path.startsWith(WIKI_ARTICLE_PATH_PREFIX) || path.startsWith(WIKI_INDEX_PHP_PREFIX)) {
                newUri = Uri.Builder()
                    .scheme(WIKI_SCHEME_HTTPS)
                    .encodedAuthority(WIKI_HOST)
                    .encodedPath(path)
                    .encodedQuery(uri.encodedQuery)
                    .encodedFragment(uri.encodedFragment)
                    .build()
            }
        }
        return newUri
    }

    /**
     * Determines if a given URI is internal to the wiki.
     * A URI is considered internal if its host matches the wiki's host, regardless of path.
     * This ensures that all pages on the wiki domain (articles, special pages, etc.) are handled
     * within the app.
     *
     * @param uri The normalized URI to check.
     * @return True if the link is internal, false otherwise.
     */
    protected open fun isInternalWikiLink(uri: Uri): Boolean {
        // A link is internal if its host matches the wiki's host. This is a safer and
        // more correct check than also checking the path, as it keeps all navigation
        // on the wiki's domain inside the app. Using the constant as the subject of the
        // equals call prevents a NullPointerException if uri.host is null.
        return WIKI_HOST.equals(uri.host, ignoreCase = true)
    }

    protected open fun extractArticleTitleFromInternalLink(uri: Uri): String? {
        val path = uri.path ?: return null

        val titleWithPotentialFragment: String? = if (path.startsWith(WIKI_ARTICLE_PATH_PREFIX) && path.length > WIKI_ARTICLE_PATH_PREFIX.length) {
            path.substring(WIKI_ARTICLE_PATH_PREFIX.length)
        } else if (path.startsWith(WIKI_INDEX_PHP_PREFIX) && uri.getQueryParameter("title") != null) {
            uri.getQueryParameter("title")
        } else {
            null
        }
        return titleWithPotentialFragment?.substringBefore('#')
    }

    abstract fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri)

    abstract fun onExternalLinkClicked(uri: Uri)

    open fun onNonArticleInternalLinkClicked(uri: Uri) {
        // Though the link is on the wiki's domain, it doesn't appear to be a standard
        // article. For now, we will attempt to open it externally as a fallback.
        // A more advanced implementation might have special handling for these cases
        // (e.g., viewing files, special pages).
        onExternalLinkClicked(uri)
    }
}
