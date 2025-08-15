package com.omiyawaki.osrswiki.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class to model the response for a prefixsearch query.
 * The structure is similar to SearchApiResponse but uses "prefixsearch" as the key.
 */
@Serializable
data class PrefixSearchApiResponse(
    val query: PrefixSearchQuery? = null
)

@Serializable
data class PrefixSearchQuery(
    // Use the annotation from kotlinx.serialization to match the JSON field name.
    @SerialName("prefixsearch")
    val prefixsearch: List<SearchResult>? = null
)
