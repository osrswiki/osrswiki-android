package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class for the response from fallback page extract API calls.
 * This is used when the main search API doesn't return snippets for certain pages.
 */
@Serializable
data class FallbackApiResponse(
    val query: FallbackQueryResult?
)

@Serializable
data class FallbackQueryResult(
    @SerialName("pages")
    val pages: List<FallbackSearchResult>?
)

/**
 * Represents a search result from the fallback API that doesn't include the index field.
 * This is used to get page extracts for pages that didn't get snippets in the main search.
 */
@Serializable
data class FallbackSearchResult(
    val ns: Int,
    val title: String,
    val pageid: Int,
    // Note: No index field required since fallback API doesn't provide it
    val size: Int? = null,
    val wordcount: Int? = null,
    @SerialName("extract")
    val snippet: String? = null,
    val timestamp: String? = null
)