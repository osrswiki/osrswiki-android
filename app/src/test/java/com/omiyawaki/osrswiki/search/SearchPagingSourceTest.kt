package com.omiyawaki.osrswiki.search

import androidx.paging.PagingSource
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.network.SearchResult
import com.omiyawaki.osrswiki.network.Thumbnail
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.GeneratedSearchApiResponse
import com.omiyawaki.osrswiki.network.model.GeneratedSearchContinuation
import com.omiyawaki.osrswiki.network.model.QueryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SearchPagingSourceTest {

    @Test
    fun loadReranksExactTitleAheadOfNoisierServerResultAndPreservesApiMetadata() = runTest {
        val apiResult = SearchResult(
            ns = 0,
            title = "Logs",
            pageid = 101,
            index = 2,
            size = 10,
            wordcount = 2,
            snippet = "Logs",
            timestamp = "2026-07-05T00:00:00Z",
            thumbnail = Thumbnail(source = "https://example.test/logs.png")
        )
        val rankedFirst = apiResult.copy(index = 1, pageid = 102, title = "Logs (first)")
        val apiService = mock<WikiApiService>()
        whenever(apiService.generatedPrefixSearch("logs*", 2, 0, 240)).thenReturn(
            GeneratedSearchApiResponse(
                continuation = GeneratedSearchContinuation(gsroffset = 2),
                query = QueryResult(pages = listOf(apiResult, rankedFirst))
            )
        )

        val source = SearchPagingSource(
            apiService = apiService,
            query = "logs",
            articleMetaDao = FakeArticleMetaDao()
        )

        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf(2, 1), page.data.map { it.index })
        assertEquals(listOf(101, 102), page.data.map { it.pageid })
        assertEquals("https://example.test/logs.png", page.data.first().thumbnailUrl)
        assertEquals(2, page.nextKey)
    }

    @Test
    fun scopedEmptyQueryBrowsesNewestUpdatePages() = runTest {
        val apiService = mock<WikiApiService>()
        whenever(apiService.generatedRecentChanges(112, 2, null, 240)).thenReturn(
            GeneratedSearchApiResponse(
                continuation = GeneratedSearchContinuation(grccontinue = "next-token"),
                query = QueryResult(
                    pages = listOf(
                        SearchResult(
                            ns = 112,
                            title = "Update:Varlamore",
                            pageid = 7,
                            snippet = "latest"
                        )
                    )
                )
            )
        )
        val source = osrsScopedSearchPagingSource(
            apiService = apiService,
            query = "",
            scope = osrsSearchScope.UPDATES,
            articleMetaDao = FakeArticleMetaDao()
        )
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        )
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf("Update:Varlamore"), page.data.map { it.title })
        assertEquals("next-token", page.nextKey)
    }

    @Test
    fun scopedTypedQueryUsesNamespacedSearch() = runTest {
        val apiQuery = SearchQueryPolicy.networkQuery("varlamore")
        val apiService = mock<WikiApiService>()
        whenever(apiService.generatedNamespacedSearch(apiQuery, 112, 2, 0, 240)).thenReturn(
            GeneratedSearchApiResponse(
                continuation = GeneratedSearchContinuation(gsroffset = 2),
                query = QueryResult(
                    pages = listOf(
                        SearchResult(
                            ns = 112,
                            title = "Update:Varlamore: The Rising Darkness",
                            pageid = 9,
                            snippet = "quest"
                        )
                    )
                )
            )
        )
        val source = osrsScopedSearchPagingSource(
            apiService = apiService,
            query = "varlamore",
            scope = osrsSearchScope.UPDATES,
            articleMetaDao = FakeArticleMetaDao()
        )
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        )
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf("Update:Varlamore: The Rising Darkness"), page.data.map { it.title })
        assertEquals("2", page.nextKey)
    }

    @Test
    fun scopedTypedQueryDropsRedirectedMainNamespaceHits() = runTest {
        val apiQuery = SearchQueryPolicy.networkQuery("sailing")
        val apiService = mock<WikiApiService>()
        whenever(apiService.generatedNamespacedSearch(apiQuery, 112, 2, 0, 240)).thenReturn(
            GeneratedSearchApiResponse(
                continuation = null,
                query = QueryResult(
                    pages = listOf(
                        SearchResult(
                            ns = 0,
                            title = "Lunar Diplomacy",
                            pageid = 1,
                            snippet = "sailing on a boat"
                        ),
                        SearchResult(
                            ns = 112,
                            title = "Update:The new Sailing skill is out today",
                            pageid = 2,
                            snippet = "brand new Sailing skill"
                        )
                    )
                )
            )
        )
        val source = osrsScopedSearchPagingSource(
            apiService = apiService,
            query = "sailing",
            scope = osrsSearchScope.UPDATES,
            articleMetaDao = FakeArticleMetaDao()
        )
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        )
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf("Update:The new Sailing skill is out today"), page.data.map { it.title })
    }

    @Test
    fun scopedBrowseFillsBlankSnippetsFromFullExtractThenParseHtml() = runTest {
        val apiService = mock<WikiApiService>()
        whenever(apiService.generatedRecentChanges(112, 2, null, 240)).thenReturn(
            GeneratedSearchApiResponse(
                continuation = null,
                query = QueryResult(
                    pages = listOf(
                        SearchResult(ns = 112, title = "Update:Blank intro", pageid = 11, snippet = null, extract = null),
                        SearchResult(ns = 112, title = "Update:Has snippet", pageid = 12, snippet = "already"),
                        SearchResult(
                            ns = 112,
                            title = "Update:Chrome snippet",
                            pageid = 13,
                            snippet = "CLICK HERE TO SHOW THIS CONTENT",
                            extract = "If you can't see the podcast, click here."
                        )
                    )
                )
            )
        )
        whenever(apiService.getPageExtract("11|13")).thenReturn(
            com.omiyawaki.osrswiki.network.model.FallbackApiResponse(
                query = com.omiyawaki.osrswiki.network.model.FallbackQueryResult(
                    pages = listOf(
                        com.omiyawaki.osrswiki.network.model.FallbackSearchResult(
                            ns = 112,
                            title = "Update:Blank intro",
                            pageid = 11,
                            snippet = "This official news post is copied verbatim from the website."
                        )
                    )
                )
            )
        )
        whenever(apiService.getArticleParseDataByPageId(13)).thenReturn(
            com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse(
                parse = com.omiyawaki.osrswiki.network.model.ParseResult(
                    title = "Update:Chrome snippet",
                    pageid = 13,
                    revid = 1,
                    text = "<p>CLICK HERE TO SHOW THIS CONTENT</p><p>Regional servers are coming to South Africa.</p>",
                    displaytitle = "Update:Chrome snippet"
                )
            )
        )
        whenever(apiService.getArticleParseDataByPageId(11)).thenReturn(
            com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse(
                parse = com.omiyawaki.osrswiki.network.model.ParseResult(
                    title = "Update:Blank intro",
                    pageid = 11,
                    revid = 1,
                    text = "<p>This official news post is copied verbatim.</p><p>Diango is giving out hats in Draynor.</p>",
                    displaytitle = "Update:Blank intro"
                )
            )
        )
        val source = osrsScopedSearchPagingSource(
            apiService = apiService,
            query = "",
            scope = osrsSearchScope.UPDATES,
            articleMetaDao = FakeArticleMetaDao()
        )
        val result = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        )
        val page = result as PagingSource.LoadResult.Page
        assertEquals("Diango is giving out hats in Draynor.", page.data.first { it.pageid == 11 }.snippet)
        assertEquals("already", page.data.first { it.pageid == 12 }.snippet)
        assertEquals("Regional servers are coming to South Africa.", page.data.first { it.pageid == 13 }.snippet)
    }

    private class FakeArticleMetaDao : ArticleMetaDao {
        override suspend fun getMetaByExactTitle(title: String): ArticleMetaEntity? = null
        override suspend fun insert(meta: ArticleMetaEntity) = Unit
        override suspend fun update(meta: ArticleMetaEntity) = Unit
        override suspend fun delete(meta: ArticleMetaEntity) = Unit
        override fun getMetaByPageIdFlow(pageId: Int): Flow<ArticleMetaEntity?> = flowOf(null)
        override suspend fun getMetaByPageId(pageId: Int): ArticleMetaEntity? = null
        override suspend fun getMetasByPageIds(pageIds: List<Int>): List<ArticleMetaEntity> = emptyList()
        override suspend fun searchByTitle(query: String): List<ArticleMetaEntity> = emptyList()
    }
}
