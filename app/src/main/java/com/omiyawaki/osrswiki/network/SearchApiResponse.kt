package com.omiyawaki.osrswiki.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SearchApiResponse(
    val query: Query? = null
)

@Serializable
data class Query(
    val searchinfo: SearchInfo? = null,
    val search: List<SearchResult>? = null
)

@Serializable
data class SearchInfo(
    val totalhits: Int? = null
)

/**
 * Represents a single search result. This has been updated to match the structure
 * returned by the generator=prefixsearch API call.
 */
@Serializable
data class SearchResult(
    val ns: Int,
    val title: String,
    val pageid: Int,
    val index: Int = 0, // The relevance index provided by the API for sorting.
    val size: Int? = null,
    val wordcount: Int? = null,
    // Relevance-ranked list and generator search responses both return `snippet`.
    @SerialName("snippet")
    val snippet: String? = null,
    // Prefixsearch has no Cirrus snippet; `prop=extracts` fills this on the same request.
    val extract: String? = null,
    val timestamp: String? = null,
    // The new API returns a nested 'thumbnail' object.
    val thumbnail: Thumbnail? = null,
    // This field is added locally, not from the network, so it must be marked @Transient.
    @Transient
    val isOfflineAvailable: Boolean = false
) {
    // Computed property to easily access the thumbnail URL.
    val thumbnailUrl: String?
        get() = thumbnail?.source
}

/**
 * Represents the nested thumbnail object in the API response.
 */
@Serializable
data class Thumbnail(
    val source: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
