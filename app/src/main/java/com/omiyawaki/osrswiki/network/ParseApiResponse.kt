package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable

/**
 * Represents the top-level response for a MediaWiki API "parse" action.
 * The relevant data is nested within the "parse" object.
 */
@Serializable
data class ParseApiResponse(
    val parse: ParseData? = null
)

/**
 * Contains the actual parsed data of an article, including its title, ID, revision ID, and HTML text.
 */
@Serializable
data class ParseData(
    val title: String? = null,
    val pageid: Int? = null, // The unique ID of the MediaWiki page
    val revid: Long? = null, // The revision ID of the page content
    val text: String? = null  // The parsed HTML content of the page
)
