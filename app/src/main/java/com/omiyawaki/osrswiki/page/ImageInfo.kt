package com.omiyawaki.osrswiki.page

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageInfoResponse(
    val query: QueryContainer? = null
)

@Serializable
data class QueryContainer(
    val pages: List<PageInfo>? = null
)

@Serializable
data class PageInfo(
    val pageid: Int,
    val ns: Int,
    val title: String,
    @SerialName("imageinfo")
    val imageInfo: List<ImageInfo>? = null
)

@Serializable
data class ImageInfo(
    val url: String? = null,
    val descriptionurl: String? = null
)
