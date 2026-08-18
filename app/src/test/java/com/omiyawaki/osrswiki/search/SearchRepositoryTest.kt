package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.search.db.RecentSearch
import com.omiyawaki.osrswiki.search.db.RecentSearchDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchRepositoryTest {

    @Test
    fun offlineTitleSearchWrapsTrimmedQueryAndEmitsDaoMatches() = runTest {
        val articleMetaDao = FakeArticleMetaDao(
            titleResults = listOf(articleMeta(pageId = 42, title = "Rune scimitar"))
        )
        val repository = repository(articleMetaDao = articleMetaDao)

        val results = repository.searchOfflineArticlesByTitle("  rune  ").first()

        assertEquals(listOf("%rune%"), articleMetaDao.titleQueries)
        assertEquals(listOf(articleMeta(pageId = 42, title = "Rune scimitar")), results)
    }

    @Test
    fun offlineTitleSearchDoesNotQueryDaoForBlankInput() = runTest {
        val articleMetaDao = FakeArticleMetaDao(
            titleResults = listOf(articleMeta(pageId = 99, title = "Should not appear"))
        )
        val repository = repository(articleMetaDao = articleMetaDao)

        val results = repository.searchOfflineArticlesByTitle("   ").first()

        assertTrue(results.isEmpty())
        assertTrue(articleMetaDao.titleQueries.isEmpty())
    }

    @Test
    fun offlineTitleSearchFallsBackToEmptyListWhenDaoFails() = runTest {
        val articleMetaDao = FakeArticleMetaDao(throwOnTitleSearch = true)
        val repository = repository(articleMetaDao = articleMetaDao)

        val results = repository.searchOfflineArticlesByTitle("dragon").first()

        assertEquals(listOf("%dragon%"), articleMetaDao.titleQueries)
        assertTrue(results.isEmpty())
    }

    @Test
    fun offlineFtsSearchUsesTrimmedQueryAndEmitsMatches() = runTest {
        val ftsDao = FakeOfflinePageFtsDao(
            ftsResults = listOf(OfflinePageFts(url = "/w/Varrock", title = "Varrock", body = "Grand Exchange"))
        )
        val repository = repository(ftsDao = ftsDao)

        val results = repository.searchOfflineFtsContent("  exchange  ").first()

        assertEquals(listOf("exchange"), ftsDao.ftsQueries)
        assertEquals(listOf(OfflinePageFts(url = "/w/Varrock", title = "Varrock", body = "Grand Exchange")), results)
    }

    @Test
    fun offlineFtsSearchFallsBackToEmptyListWhenDaoFails() = runTest {
        val ftsDao = FakeOfflinePageFtsDao(throwOnSearch = true)
        val repository = repository(ftsDao = ftsDao)

        val results = repository.searchOfflineFtsContent("varrock").first()

        assertEquals(listOf("varrock"), ftsDao.ftsQueries)
        assertTrue(results.isEmpty())
    }

    @Test
    fun recentSearchesCanBeInsertedReplacedAndCleared() = runTest {
        val recentSearchDao = FakeRecentSearchDao()
        val repository = repository(recentSearchDao = recentSearchDao)

        repository.insertRecentSearch("zulrah")
        repository.insertRecentSearch("vorkath")
        repository.insertRecentSearch("zulrah")

        val searches = repository.getRecentSearches().first()
        assertEquals(listOf("zulrah", "vorkath"), searches.map { it.query })

        repository.clearAllRecentSearches()

        assertTrue(repository.getRecentSearches().first().isEmpty())
    }

    @Test
    fun recentSearchesDecodeHtmlEntitiesAtStorageAndReadBoundaries() = runTest {
        val recentSearchDao = FakeRecentSearchDao(
            initial = listOf(RecentSearch("Wyrmscraig &amp; Sailing Changes", 1L))
        )
        val repository = repository(recentSearchDao = recentSearchDao)

        assertEquals(
            listOf("Wyrmscraig & Sailing Changes"),
            repository.getRecentSearches().first().map { it.query }
        )

        repository.insertRecentSearch("Araxxor &amp; Updates")
        assertEquals("Araxxor & Updates", recentSearchDao.lastInserted?.query)
    }

    private fun repository(
        articleMetaDao: ArticleMetaDao = FakeArticleMetaDao(),
        ftsDao: OfflinePageFtsDao = FakeOfflinePageFtsDao(),
        recentSearchDao: RecentSearchDao = FakeRecentSearchDao()
    ): SearchRepository {
        return SearchRepository(
            apiService = mock<WikiApiService>(),
            articleMetaDao = articleMetaDao,
            offlinePageFtsDao = ftsDao,
            recentSearchDao = recentSearchDao
        )
    }

    private fun articleMeta(pageId: Int, title: String): ArticleMetaEntity {
        return ArticleMetaEntity(
            pageId = pageId,
            title = title,
            wikiUrl = "https://oldschool.runescape.wiki/w/${title.replace(' ', '_')}",
            localFilePath = "/tmp/$pageId.html",
            lastFetchedTimestamp = 1_700_000_000L,
            revisionId = 1L,
            categories = null
        )
    }

    private class FakeArticleMetaDao(
        private val titleResults: List<ArticleMetaEntity> = emptyList(),
        private val throwOnTitleSearch: Boolean = false
    ) : ArticleMetaDao {
        val titleQueries = mutableListOf<String>()
        private val metas = mutableMapOf<Int, ArticleMetaEntity>()

        override suspend fun getMetaByExactTitle(title: String): ArticleMetaEntity? {
            return metas.values.firstOrNull { it.title == title }
        }

        override suspend fun insert(meta: ArticleMetaEntity) {
            metas[meta.pageId] = meta
        }

        override suspend fun update(meta: ArticleMetaEntity) {
            metas[meta.pageId] = meta
        }

        override suspend fun delete(meta: ArticleMetaEntity) {
            metas.remove(meta.pageId)
        }

        override fun getMetaByPageIdFlow(pageId: Int): Flow<ArticleMetaEntity?> {
            return flowOf(metas[pageId])
        }

        override suspend fun getMetaByPageId(pageId: Int): ArticleMetaEntity? {
            return metas[pageId]
        }

        override suspend fun getMetasByPageIds(pageIds: List<Int>): List<ArticleMetaEntity> {
            return pageIds.mapNotNull { metas[it] }
        }

        override suspend fun searchByTitle(query: String): List<ArticleMetaEntity> {
            titleQueries += query
            if (throwOnTitleSearch) error("title search failed")
            return titleResults
        }
    }

    private class FakeOfflinePageFtsDao(
        private val ftsResults: List<OfflinePageFts> = emptyList(),
        private val throwOnSearch: Boolean = false
    ) : OfflinePageFtsDao {
        val ftsQueries = mutableListOf<String>()
        private val indexedItems = mutableListOf<OfflinePageFts>()

        override suspend fun insertPageContent(item: OfflinePageFts) {
            indexedItems += item
        }

        override suspend fun deletePageContentByUrl(url: String) {
            indexedItems.removeAll { it.url == url }
        }

        override suspend fun searchAll(query: String): List<OfflinePageFts> {
            ftsQueries += query
            if (throwOnSearch) error("FTS search failed")
            return ftsResults
        }

        override suspend fun getAll(): List<OfflinePageFts> {
            return indexedItems.toList()
        }
    }

    private class FakeRecentSearchDao(
        initial: List<RecentSearch> = emptyList()
    ) : RecentSearchDao {
        private val searches = MutableStateFlow(initial)
        var lastInserted: RecentSearch? = null

        override suspend fun insert(recentSearch: RecentSearch) {
            lastInserted = recentSearch
            searches.value = listOf(recentSearch) + searches.value.filterNot {
                it.query == recentSearch.query
            }
        }

        override fun getAll(): Flow<List<RecentSearch>> = searches

        override suspend fun clearAll() {
            searches.value = emptyList()
        }
    }
}
