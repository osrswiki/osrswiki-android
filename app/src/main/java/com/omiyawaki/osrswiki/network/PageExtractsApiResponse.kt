package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable

/**
 * Data class to model the response from a query for page extracts (snippets).
 * The API returns a list of page objects when using formatversion=2.
 */
@Serializable
data class PageExtractsApiResponse(
    val query: ExtractsQuery? = null
)

@Serializable
data class ExtractsQuery(
    val pages: List<PageExtract>? = null,
    val redirects: List<WikiTitleRedirect>? = null
)

@Serializable
data class WikiTitleRedirect(
    val from: String? = null,
    val to: String? = null
)

@Serializable
data class PageExtract(
    val pageid: Int? = null,
    val title: String? = null,
    val extract: String? = null,
    val thumbnail: PageThumbnail? = null,
    val missing: Boolean? = null
)
