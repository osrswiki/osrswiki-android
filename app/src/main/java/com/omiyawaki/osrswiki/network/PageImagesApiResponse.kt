package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable

/**
 * Represents the top-level response for a MediaWiki API "query" action with "prop=pageimages".
 */
@Serializable
data class PageImagesApiResponse(
    val batchcomplete: Boolean? = null, // Indicates if the batch of operations is complete
    val query: PageImagesQuery? = null
)

/**
 * Contains the list of pages returned by the "query" action.
 * For a single page ID request, this list typically contains one item.
 */
@Serializable
data class PageImagesQuery(
    val pages: List<PageImageInfo>? = null
)

/**
 * Holds information about a specific page, including its ID, title, and image details.
 */
@Serializable
data class PageImageInfo(
    val pageid: Int? = null,       // The unique ID of the page
    val ns: Int? = null,           // Namespace ID of the page
    val title: String? = null,     // Title of the page
    val thumbnail: PageThumbnail? = null, // Information about the page's thumbnail image
    val pageimage: String? = null  // The filename of the main image associated with the page (e.g., "Giant_mole.png")
)

/**
 * Describes the thumbnail image, including its source URL, width, and height.
 */
@Serializable
data class PageThumbnail(
    val source: String? = null,    // URL of the thumbnail image
    val width: Int? = null,        // Width of the thumbnail in pixels
    val height: Int? = null       // Height of the thumbnail in pixels
)
