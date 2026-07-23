package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for MediaWiki Search API (action=query&list=search).
 * This provides superior search relevance ranking and guaranteed snippet coverage.
 */
@Serializable
data class SearchApiResponse(
    val query: SearchQuery? = null
)

@Serializable
data class SearchQuery(
    val searchinfo: SearchInfo? = null,
    val search: List<SearchApiResult>? = null
)

@Serializable
data class SearchInfo(
    val totalhits: Int? = null
)

/**
 * Individual search result from MediaWiki Search API.
 * This model closely matches the API response structure and includes
 * rich metadata not available through the generator approach.
 */
@Serializable
data class SearchApiResult(
    val ns: Int,
    val title: String,
    val pageid: Int,
    val size: Int? = null,
    val wordcount: Int? = null,
    val snippet: String? = null,
    val timestamp: String? = null
)