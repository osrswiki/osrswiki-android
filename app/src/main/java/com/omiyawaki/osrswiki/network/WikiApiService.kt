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
     * Relevance-ranked fulltext search with Cirrus snippets and thumbnails.
     * Restricted to main (0) and Calculator (116). TextExtracts is intentionally omitted here:
     * the extension caps extracts at 20 pages, and generating them for a full result page
     * dominates typeahead latency. Prefix-only rows that have no Cirrus snippet get extracts
     * from [generatedTitlePrefixSearch].
     */
    @GET("api.php?action=query&format=json&formatversion=2&redirects=true" +
            "&generator=search" +
            "&gsrnamespace=0|116" +
            "&gsrprop=snippet|size|wordcount|timestamp" +
            "&gsrsort=relevance" +
            "&prop=pageimages" +
            "&piprop=thumbnail&pilicense=any") // pageimages properties
    suspend fun generatedPrefixSearch(
        @Query("gsrsearch") query: String,
        @Query("gsrlimit") limit: Int,
        @Query("gsroffset") offset: Int,
        @Query("pithumbsize") thumbSize: Int
    ): GeneratedSearchApiResponse

    /**
     * Title-prefix generator used by the website search box. Restricted to main (0)
     * and Calculator (116); Cirrus fulltext drops prefix hits such as "earth ru" → Earth rune.
     * Limited to 10 titles, so TextExtracts can fill preview gaps without the 20-extract cap
     * starving later rows.
     */
    @GET("api.php?action=query&format=json&formatversion=2&redirects=true" +
            "&generator=prefixsearch" +
            "&gpsnamespace=0|116" +
            "&prop=pageimages|extracts" +
            "&exintro=true&explaintext=true&exchars=160&exlimit=max" +
            "&piprop=thumbnail&pilicense=any")
    suspend fun generatedTitlePrefixSearch(
        @Query("gpssearch") query: String,
        @Query("gpslimit") limit: Int,
        @Query("pithumbsize") thumbSize: Int
    ): GeneratedSearchApiResponse

    /**
     * Fulltext search restricted to one MediaWiki namespace (Home "View more" uses 112).
     */
    @GET("api.php?action=query&format=json&formatversion=2" +
            "&generator=search" +
            "&gsrprop=snippet|size|wordcount|timestamp" +
            "&gsrsort=relevance" +
            "&prop=pageimages|extracts" +
            "&exintro=true&explaintext=true&exchars=160&exlimit=max" +
            "&piprop=thumbnail&pilicense=any")
    suspend fun generatedNamespacedSearch(
        @Query("gsrsearch") query: String,
        @Query("gsrnamespace") namespace: Int,
        @Query("gsrlimit") limit: Int,
        @Query("gsroffset") offset: Int,
        @Query("pithumbsize") thumbSize: Int
    ): GeneratedSearchApiResponse

    /**
     * Newest-first browse of new pages in a namespace when the Search query is empty.
     */
    @GET("api.php?action=query&format=json&formatversion=2" +
            "&generator=recentchanges" +
            "&grctype=new" +
            "&grcdir=older" +
            "&prop=pageimages|extracts" +
            "&exintro=true&explaintext=true&exchars=160&exlimit=max" +
            "&piprop=thumbnail&pilicense=any")
    suspend fun generatedRecentChanges(
        @Query("grcnamespace") namespace: Int,
        @Query("grclimit") limit: Int,
        @Query("grccontinue") continueToken: String? = null,
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

    @GET("api.php?action=opensearch&format=json&redirects=resolve&namespace=0|116")
    suspend fun openSearch(
        @Query("search") query: String,
        @Query("limit") limit: Int = 10
    ): okhttp3.ResponseBody

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

    @GET("api.php?action=query&prop=extracts&format=json&formatversion=2&exintro=true&explaintext=true&exchars=280")
    suspend fun getPageExtractsByTitles(
        @Query("titles") titles: String
    ): PageExtractsApiResponse

    @GET("api.php?action=query&prop=extracts|pageimages&format=json&formatversion=2&redirects=true&exintro=true&explaintext=true&exchars=280&exlimit=max&piprop=thumbnail&pilicense=any")
    suspend fun getHistoryPreviewMetadata(
        @Query("titles") titles: String,
        @Query("pithumbsize") thumbSize: Int = 160
    ): PageExtractsApiResponse

    @GET("api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true&maxage=300&smaxage=300")
    suspend fun getArticleParseDataByPageId(@Query("pageid") pageId: Int): ArticleParseApiResponse

    // Add back a method to get parse data by page title for PageRemoteDataSource.
    @GET("api.php?action=parse&format=json&formatversion=2&prop=text|revid|displaytitle&redirects=true&disableeditsection=true&disablelimitreport=true&maxage=300&smaxage=300")
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
