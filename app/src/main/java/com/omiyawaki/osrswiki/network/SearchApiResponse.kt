package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable

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

@Serializable
data class SearchResult(
    val ns: Int,
    val title: String,
    val pageid: Int,
    val size: Int? = null,
    val wordcount: Int? = null,
    val snippet: String? = null,
    val timestamp: String? = null,
    // Add a field for the thumbnail URL, with a default value for serialization.
    val thumbnailUrl: String? = null,
    val isOfflineAvailable: Boolean = false
)
