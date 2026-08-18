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
