package com.omiyawaki.osrswiki.search

import androidx.paging.PagingSource
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.network.PageImagesApiResponse
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.SearchApiResponse
import com.omiyawaki.osrswiki.network.model.SearchApiResult
import com.omiyawaki.osrswiki.network.model.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SearchPagingSourceTest {

    @Test
    fun loadAssignsResultIndicesByApiOrderEvenWhenRowsCompareEqual() = runTest {
        val apiResult = SearchApiResult(
            ns = 0,
            title = "Logs",
            pageid = 101,
            size = 10,
            wordcount = 2,
            snippet = "Logs",
            timestamp = "2026-07-05T00:00:00Z"
        )
        val apiService = mock<WikiApiService>()
        whenever(apiService.searchPages("logs", 2, 0)).thenReturn(
            SearchApiResponse(query = SearchQuery(search = listOf(apiResult, apiResult)))
        )
        whenever(apiService.getPageThumbnails("101|101", 240)).thenReturn(PageImagesApiResponse())

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
        assertEquals(listOf(1, 2), page.data.map { it.index })
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
