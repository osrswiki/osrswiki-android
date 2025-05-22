package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable

/**
 * Data classes to represent the JSON response from the OSRS Wiki API search endpoint.
 * The structure is based on typical MediaWiki API search results.
 * Example: https://oldschool.runescape.wiki/api.php?action=query&list=search&srsearch=goblin&format=json
 */

@Serializable
data class SearchApiResponse(
    // The "batchcomplete" field is often present, indicating the batch of API actions is complete.
    // We can ignore it if not directly needed, thanks to ignoreUnknownKeys = true in Json parser.
    // val batchcomplete: String? = null, // Example, can be omitted
    val query: Query? = null
)

@Serializable
data class Query(
    val searchinfo: SearchInfo? = null,
    val search: List<SearchResult>? = null
    // The API might also include 'continue' block for pagination if not using sroffset directly
    // val continue: ContinueInfo? = null, // Example, can be omitted or added if needed
)

@Serializable
data class SearchInfo(
    val totalhits: Int? = null,
    // May also include "suggestion" and "suggestionsnippet" if a typo was corrected.
    // val suggestion: String? = null,
    // val suggestionsnippet: String? = null
)

@Serializable
data class SearchResult(
    val ns: Int, // Namespace ID
    val title: String,
    val pageid: Int,
    val size: Int? = null, // Size of the page in bytes
    val wordcount: Int? = null,
    val snippet: String? = null, // HTML snippet of the search result context
    val timestamp: String? = null, // ISO 8601 timestamp of last edit
    val isOfflineAvailable: Boolean = false // New field for offline status
    // Other fields like "score", "titlesnippet", "redirecttitle", "redirectsnippet",
    // "sectiontitle", "sectionsnippet", "isfilematch", "categorysnippet" might exist.
    // Add them if needed.
)

// Example for a 'continue' block if ever needed for deep pagination handling manually
// @Serializable
// data class ContinueInfo(
//    @SerialName("sroffset") val sroffset: Int? = null,
//    @SerialName("continue") val continueValue: String? = null // e.g., "-||"
// )

