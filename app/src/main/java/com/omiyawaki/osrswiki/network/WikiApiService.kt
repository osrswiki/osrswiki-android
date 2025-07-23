package com.omiyawaki.osrswiki.network

import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.PageImagesInfo
import com.omiyawaki.osrswiki.page.ImageInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WikiApiService {
    @GET("api.php?action=query&list=search&format=json&srprop=snippet")
    suspend fun searchArticles(
        @Query("srsearch") query: String,
        @Query("srlimit") limit: Int,
        @Query("sroffset") offset: Int
    ): SearchApiResponse

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
