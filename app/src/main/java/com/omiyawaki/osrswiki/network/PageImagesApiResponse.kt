package com.omiyawaki.osrswiki.network

import kotlinx.serialization.Serializable

/**
 * Data classes for parsing the response from a MediaWiki query with prop=pageimages.
 * The 'pages' object is a map where the keys are the dynamic page IDs.
 */
@Serializable
data class PageImagesApiResponse(val query: PageImagesQuery? = null)

@Serializable
data class PageImagesQuery(val pages: Map<String, PageImagesPageInfo>? = null)

@Serializable
data class PageImagesPageInfo(
    val pageid: Int,
    val title: String,
    val thumbnail: PageThumbnail? = null
)

@Serializable
data class PageThumbnail(val source: String? = null)
