package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.PageImagesInfo
import com.omiyawaki.osrswiki.page.ImageInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WikiApiService {
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
