package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
// MODIFIED: Import for the new ImageInfoResponse data class
import com.omiyawaki.osrswiki.page.ImageInfoResponse

// SearchApiResponse, ParseApiResponse, and PageImagesApiResponse are expected
// to be in the same 'com.omiyawaki.osrswiki.network' package,
// defined in their respective .kt files (SearchApiResponse.kt, ParseApiResponse.kt, PageImagesApiResponse.kt).

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
        @Query("srlimit") limit: Int,        // Max number of results to return
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

}
