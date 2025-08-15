package com.omiyawaki.osrswiki.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level response from a MediaWiki API query for image metadata.
 */
@Serializable
data class PageImagesInfo(
    val query: Query? = null
)

/**
 * Contains the main content of the API response, which is a list of pages.
 */
@Serializable
data class Query(
    val pages: List<Page>? = null
)

/**
 * Represents a single page (an image file) returned by the query.
 */
@Serializable
data class Page(
    @SerialName("pageid")
    val pageId: Int,
    val title: String,
    @SerialName("imageinfo")
    val imageInfo: List<ImageInfo>? = null
)

/**
 * Contains the specific metadata for an image, including its size and URL.
 */
@Serializable
data class ImageInfo(
    val size: Long,
    val url: String
)
