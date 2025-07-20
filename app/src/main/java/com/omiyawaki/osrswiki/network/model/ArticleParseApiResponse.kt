package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class ParseResult(
    val title: String,
    val pageid: Int,
    val revid: Long,
    val text: TextObject?,
    val displaytitle: String?
)

/**
 * Represents the nested "text" object in the API response.
 */
@Serializable
data class TextObject(
    @SerialName("*")
    val content: String
)
