package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.FallbackApiResponse
import com.omiyawaki.osrswiki.network.model.GeneratedSearchApiResponse
import com.omiyawaki.osrswiki.network.model.PageImagesInfo
import com.omiyawaki.osrswiki.network.model.SearchApiResponse
import com.omiyawaki.osrswiki.page.ImageInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WikiApiService {
    /**
     * Performs a prefix search and simultaneously fetches extracts and thumbnails for the results.
     * This uses 'prefixsearch' as a generator for a 'query' prop action, which is much more
     * efficient than making separate API calls.
     */
    @GET("api.php?action=query&format=json&formatversion=2&redirects=true" +
            "&generator=prefixsearch" +
            "&prop=extracts|pageimages" +
            "&exintro=true&explaintext=true&exchars=280" + // extract properties
            "&piprop=thumbnail&pilicense=any") // pageimages properties
    suspend fun generatedPrefixSearch(
        @Query("gpssearch") query: String,
        @Query("gpslimit") limit: Int,
        @Query("gpsoffset") offset: Int,
        @Query("pithumbsize") thumbSize: Int
    ): GeneratedSearchApiResponse

    /**
     * MediaWiki Search API for intelligent search results with proper relevance ranking.
     * This replaces the generator=prefixsearch approach to provide superior user experience
     * with guaranteed snippet coverage and better result ordering.
     */
    @GET("api.php?action=query&format=json&formatversion=2" +
            "&list=search" +
            "&srprop=snippet|size|wordcount|timestamp" +
            "&srsort=relevance") // explicit relevance sorting
    suspend fun searchPages(
        @Query("srsearch") query: String,
        @Query("srlimit") limit: Int,
        @Query("sroffset") offset: Int
    ): SearchApiResponse

    /**
     * Fallback API call for pages that didn't get extracts with exintro=true.
     * Uses exintro=false to get content from anywhere in the page.
     */
    @GET("api.php?action=query&format=json&formatversion=2" +
            "&prop=extracts" +
            "&explaintext=true&exchars=280") // No exintro=true for fallback
    suspend fun getPageExtract(
        @Query("pageids") pageIds: String
    ): FallbackApiResponse

    @GET("api.php?action=query&list=prefixsearch&format=json")
    suspend fun prefixSearchArticles(
        @Query("pssearch") query: String,
        @Query("pslimit") limit: Int,
        @Query("psoffset") offset: Int
    ): PrefixSearchApiResponse

    @GET("api.php?action=query&prop=extracts&format=json&formatversion=2&exintro=true&explaintext=true&exchars=280")
    suspend fun getPageExtracts(
        @Query("pageids") pageIds: String
    ): PageExtractsApiResponse

    @GET("api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true")
    suspend fun getArticleParseDataByPageId(@Query("pageid") pageId: Int): ArticleParseApiResponse

    // Add back a method to get parse data by page title for PageRemoteDataSource.
    @GET("api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true")
    suspend fun getArticleParseDataByTitle(@Query("page") title: String): ArticleParseApiResponse

    @GET("api.php?action=query&prop=imageinfo&iiprop=url&format=json&formatversion=2")
    suspend fun getImageInfo(@Query("titles") titles: String): ImageInfoResponse

    @GET("api.php?action=query&prop=pageimages&format=json&pilicense=any")
    suspend fun getPageThumbnails(
        @Query("pageids") pageIds: String,
        @Query("pithumbsize") thumbSize: Int
    ): PageImagesApiResponse

    @GET("api.php?action=query&prop=imageinfo&iiprop=url|size&format=json&formatversion=2&generator=images")
    suspend fun getArticleImageInfo(@Query("pageids") pageId: Int): PageImagesInfo
}
