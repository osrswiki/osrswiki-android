package com.omiyawaki.osrswiki.network.model

import com.omiyawaki.osrswiki.network.SearchResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class for the response from a generated prefix search.
 * The results are contained within a 'pages' array in the 'query' object.
 */
@Serializable
data class GeneratedSearchApiResponse(
    val query: QueryResult?
)

@Serializable
data class QueryResult(
    // The API returns 'pages' as an array, so the type must be a List, not a Map.
    @SerialName("pages")
    val pages: List<SearchResult>?
)
