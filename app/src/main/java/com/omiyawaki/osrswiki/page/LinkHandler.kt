package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import com.omiyawaki.osrswiki.util.log.L // Assuming L is your logging utility
import java.net.URLDecoder // For decoding path segments if necessary

abstract class LinkHandler(protected val context: Context) {

    companion object {
        private const val WIKI_SCHEME_HTTPS = "https"
        private const val WIKI_SCHEME_HTTP = "http"
        private const val WIKI_HOST = "oldschool.runescape.wiki"
        private const val WIKI_ARTICLE_PATH_PREFIX = "/w/"
        private const val WIKI_INDEX_PHP_PREFIX = "/index.php"
        private const val LH_DEBUG_TAG = "LinkHandler_DEBUG" // Tag for inclusion in message
    }

    fun processUri(uri: Uri) {
        L.d("[$LH_DEBUG_TAG] processUri: Received URI: $uri")
        val processedUri = normalizeUri(uri)
        L.d("[$LH_DEBUG_TAG] processUri: URI after normalization: $processedUri")

        if (isInternalWikiLink(processedUri)) {
            L.d("[$LH_DEBUG_TAG] processUri: URI classified as INTERNAL.")
            val articleTitle = extractArticleTitleFromInternalLink(processedUri)
            if (articleTitle != null && articleTitle.isNotBlank()) {
                L.d("[$LH_DEBUG_TAG] processUri: Extracted article title: '$articleTitle'. Calling onInternalArticleLinkClicked.")
                onInternalArticleLinkClicked(articleTitle, processedUri)
            } else {
                L.d("[$LH_DEBUG_TAG] processUri: Could not extract valid article title from internal link. Calling onNonArticleInternalLinkClicked for URI: $processedUri")
                onNonArticleInternalLinkClicked(processedUri)
            }
        } else {
            L.d("[$LH_DEBUG_TAG] processUri: URI classified as EXTERNAL. Calling onExternalLinkClicked for URI: $processedUri")
            onExternalLinkClicked(processedUri)
        }
    }

    protected open fun normalizeUri(uri: Uri): Uri {
        L.d("[$LH_DEBUG_TAG] normalizeUri: Input URI: $uri (Scheme: ${uri.scheme}, Authority: ${uri.authority}, Path: ${uri.path})")
        var newUri = uri
        val scheme = uri.scheme
        val authority = uri.authority
        val path = uri.path ?: ""
        val encodedSchemeSpecificPart = uri.encodedSchemeSpecificPart ?: ""

        if (scheme == null) {
            L.d("[$LH_DEBUG_TAG] normalizeUri: Scheme is null.")
            if (encodedSchemeSpecificPart.startsWith("//")) {
                L.d("[$LH_DEBUG_TAG] normalizeUri: Handling scheme-relative: $encodedSchemeSpecificPart")
                newUri = Uri.parse("$WIKI_SCHEME_HTTPS:$encodedSchemeSpecificPart")
            } else if (authority != null) {
                L.d("[$LH_DEBUG_TAG] normalizeUri: Authority present ('$authority'), adding default scheme.")
                newUri = newUri.buildUpon().scheme(WIKI_SCHEME_HTTPS).build()
            } else if (path.startsWith(WIKI_ARTICLE_PATH_PREFIX) || path.startsWith(WIKI_INDEX_PHP_PREFIX)) {
                L.d("[$LH_DEBUG_TAG] normalizeUri: Path looks like server-relative ('$path'). Building full URI.")
                newUri = Uri.Builder()
                    .scheme(WIKI_SCHEME_HTTPS)
                    .encodedAuthority(WIKI_HOST)
                    .encodedPath(path)
                    .encodedQuery(uri.encodedQuery)
                    .encodedFragment(uri.encodedFragment)
                    .build()
            } else {
                L.d("[$LH_DEBUG_TAG] normalizeUri: Scheme is null, not scheme-relative, no authority, and path ('$path') doesn't match known wiki prefixes. URI remains unchanged from input for this branch.")
            }
        } else {
            L.d("[$LH_DEBUG_TAG] normalizeUri: Scheme ('$scheme') is present. No scheme normalization needed for this branch.")
        }
        L.d("[$LH_DEBUG_TAG] normalizeUri: Output URI: $newUri")
        return newUri
    }

    protected open fun isInternalWikiLink(uri: Uri): Boolean {
        L.d("[$LH_DEBUG_TAG] isInternalWikiLink: Checking URI: $uri")
        val scheme = uri.scheme
        val host = uri.host
        val path = uri.path

        val result = (scheme == WIKI_SCHEME_HTTPS || scheme == WIKI_SCHEME_HTTP) &&
               host.equals(WIKI_HOST, ignoreCase = true) &&
               path != null && (path.startsWith(WIKI_ARTICLE_PATH_PREFIX) || path.startsWith(WIKI_INDEX_PHP_PREFIX))
        L.d("[$LH_DEBUG_TAG] isInternalWikiLink: Result: $result (Scheme: $scheme, Host: $host, Path: $path)")
        return result
    }

    protected open fun extractArticleTitleFromInternalLink(uri: Uri): String? {
        L.d("[$LH_DEBUG_TAG] extractArticleTitleFromInternalLink: URI: $uri")
        val path = uri.path ?: run {
            L.d("[$LH_DEBUG_TAG] extractArticleTitleFromInternalLink: Path is null.")
            return null
        }

        val titleWithPotentialFragment: String? = if (path.startsWith(WIKI_ARTICLE_PATH_PREFIX) && path.length > WIKI_ARTICLE_PATH_PREFIX.length) {
            path.substring(WIKI_ARTICLE_PATH_PREFIX.length)
        } else if (path.startsWith(WIKI_INDEX_PHP_PREFIX) && uri.getQueryParameter("title") != null) {
            uri.getQueryParameter("title")
        } else {
            null
        }
        L.d("[$LH_DEBUG_TAG] extractArticleTitleFromInternalLink: Title before fragment removal: '$titleWithPotentialFragment'")
        val finalTitle = titleWithPotentialFragment?.substringBefore('#')
        L.d("[$LH_DEBUG_TAG] extractArticleTitleFromInternalLink: Final extracted title: '$finalTitle'")
        return finalTitle
    }

    abstract fun onInternalArticleLinkClicked(articleTitle: String, fullUri: Uri)

    abstract fun onExternalLinkClicked(uri: Uri)

    open fun onNonArticleInternalLinkClicked(uri: Uri) {
        L.d("[$LH_DEBUG_TAG] onNonArticleInternalLinkClicked: URI: $uri. Defaulting to onExternalLinkClicked.")
        onExternalLinkClicked(uri)
    }
}
