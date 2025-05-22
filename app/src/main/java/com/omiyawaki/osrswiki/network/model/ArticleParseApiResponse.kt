package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.SerialName // Import for kotlinx.serialization
import kotlinx.serialization.Serializable // Import for kotlinx.serialization

/**
 * Represents the top-level response from the MediaWiki API's parse action.
 * Example: {"parse":{"title":"Dragon","pageid":13909,"text":{"*":"..."}}}
 */
@Serializable // Added for kotlinx.serialization
data class ArticleParseApiResponse(
    val parse: ParseResult? = null // Added default value
)

/**
 * Contains the actual parsed article data.
 */
@Serializable // Added for kotlinx.serialization
data class ParseResult(
    val title: String? = null, // Added default value
    val pageid: Int? = null, // Added default value
    val revid: Long? = null, // Added revision ID
    val text: String? = null // Added default value
)

