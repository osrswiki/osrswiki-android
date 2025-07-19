package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.PageImagesInfo
import com.omiyawaki.osrswiki.page.ImageInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WikiApiService {

    /**
     * Searches for articles on the wiki.
     * Uses MediaWiki API action=query, list=search.
     * Example: https://oldschool.runescape.wiki/api.php?action=query&list=search&format=json&srprop=snippet&srsearch=dragon&srlimit=10&sroffset=0
     */
    @GET("api.php?action=query&list=search&format=json&srprop=snippet")
    suspend fun searchArticles(
        @Query("srsearch") query: String,    // The search term
        @Query("srlimit") limit: Int,         // Max number of results to return
        @Query("sroffset") offset: Int        // Offset for pagination
    ): SearchApiResponse // Defined in SearchApiResponse.kt

    /**
     * Fetches the parseable HTML content of a specific article by its page ID.
     * Uses MediaWiki API action=parse.
     * formatversion=2 returns HTML directly in the 'text' property.
     * disableeditsection=true removes "edit" links from the content.
     * disablelimitreport=true removes comment like ""
     * Example: https://oldschool.runescape.wiki/api.php?action=parse&pageid=PAGE_ID&prop=text&formatversion=2&format=json&disableeditsection=true&disablelimitreport=true
     */

    // For fetching article text content by title
    @GET("api.php?action=parse&prop=text|revid|displaytitle&formatversion=2&format=json&disableeditsection=true&disablelimitreport=true")
    suspend fun getArticleTextContentByTitle(@Query("page") title: String): ArticleParseApiResponse

    // New method to fetch parse data by Page ID
    // Includes title, pageid, revid, and HTML text content.
    @GET("api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true")
    suspend fun getArticleParseDataByPageId(@Query("pageid") pageId: Int): ArticleParseApiResponse


    // MODIFIED: New function to get image metadata for File: pages
    /**
     * Fetches image metadata, including the direct file URL.
     * Uses MediaWiki API action=query, prop=imageinfo.
     * This is used for File: pages to get the actual image URL.
     * Example: https://oldschool.runescape.wiki/api.php?action=query&titles=File:Abyssal_whip.png&prop=imageinfo&iiprop=url&format=json&formatversion=2
     */
    @GET("api.php?action=query&prop=imageinfo&iiprop=url&format=json&formatversion=2")
    suspend fun getImageInfo(@Query("titles") titles: String): ImageInfoResponse


    @Suppress("unused")
    @GET("api.php?action=query&prop=pageimages&formatversion=2&format=json")
    suspend fun getArticleImageUrlById(
        @Query("pageids") pageId: Int,
        @Query("pithumbsize") pithumbsize: Int = 500
    ): PageImagesApiResponse

    /**
     * Fetches metadata (URL and size) for all images on a given page.
     * Uses MediaWiki API action=query with a generator for images. This is the most
     * efficient way to get all image sizes for progress calculation.
     * Example: https://oldschool.runescape.wiki/api.php?action=query&pageids=PAGE_ID&prop=imageinfo&iiprop=url|size&format=json&formatversion=2&generator=images
     */
    @GET("api.php?action=query&prop=imageinfo&iiprop=url|size&format=json&formatversion=2&generator=images")
    suspend fun getArticleImageInfo(@Query("pageids") pageId: Int): PageImagesInfo
}
