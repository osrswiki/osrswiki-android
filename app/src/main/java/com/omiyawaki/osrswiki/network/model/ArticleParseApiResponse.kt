package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.Serializable // Import for kotlinx.serialization

/**
 * Represents the top-level response from the MediaWiki API's parse action.
 */
@Serializable
data class ArticleParseApiResponse(
    val parse: ParseResult? = null
)

/**
 * Contains the actual parsed article data.
 */
@Serializable
@Suppress("unused")
data class ParseResult(
    val title: String? = null,
    val pageid: Int? = null,
    val revid: Long? = null,
    val text: String? = null, // Comma added as displaytitle follows
    val displaytitle: String? = null // New field
)
