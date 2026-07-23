package com.omiyawaki.osrswiki.readinglist.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import com.omiyawaki.osrswiki.offline.db.OfflineObjectDao
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SavedPagesRepositoryTest {

    @Test
    fun searchSavedPagesCombinesTitleAndContentMatchesDedupedByIdAndSortedByAccessTime() = runTest {
        val vorkath = savedPage(
            id = 1,
            title = "Vorkath",
            apiTitle = "Vorkath",
            description = "Blue dragon boss",
            atime = 300
        )
        val dragonBoots = savedPage(
            id = 2,
            title = "Dragon boots",
            apiTitle = "Dragon_boots",
            atime = 200
        )
        val runeScimitar = savedPage(
            id = 3,
            title = "Rune scimitar",
            apiTitle = "Rune_scimitar",
            atime = 100
        )
        val readingListPageDao = FakeReadingListPageDao(listOf(vorkath, dragonBoots, runeScimitar))
        val ftsDao = FakeOfflinePageFtsDao(
            searchResults = listOf(
                OfflinePageFts(url = "/wiki/Vorkath", title = "Vorkath", body = "Dragonfire"),
                OfflinePageFts(url = "/wiki/Dragon_boots", title = "Dragon boots", body = "Strength"),
                OfflinePageFts(url = "/wiki/Missing", title = "Missing page", body = "Dragon")
            )
        )
        val repository = repository(readingListPageDao = readingListPageDao, ftsDao = ftsDao)

        val results = repository.searchSavedPages("dragon")

        assertEquals(listOf("Vorkath", "Dragon boots"), results.map { it.displayTitle })
        assertEquals(listOf("dragon"), ftsDao.searchQueries)
        assertEquals(
            listOf(
                ReadingListPage.STATUS_SAVED to true,
                ReadingListPage.STATUS_SAVED to true
            ),
            readingListPageDao.statusOfflineQueries
        )
    }

    @Test
    fun deleteSavedPageRemovesOfflineObjectsFtsEntryAndReadingListRow() = runTest {
        val page = savedPage(id = 8, title = "Abyssal whip", apiTitle = "Abyssal_whip", mediaWikiPageId = 4151)
        val readingListPageDao = FakeReadingListPageDao(listOf(page))
        val ftsDao = FakeOfflinePageFtsDao()
        val offlineObjectDao = FakeOfflineObjectDao()
        val articleMetaDao = FakeArticleMetaDao(
            ArticleMetaEntity(
                pageId = 4151,
                title = "Abyssal whip",
                wikiUrl = "https://oldschool.runescape.wiki/w/Abyssal_whip",
                localFilePath = "/tmp/abyssal-whip.html",
                lastFetchedTimestamp = 1_700_000_000L,
                revisionId = 123L,
                categories = null
            )
        )
        val repository = repository(
            readingListPageDao = readingListPageDao,
            ftsDao = ftsDao,
            offlineObjectDao = offlineObjectDao,
            articleMetaDao = articleMetaDao
        )

        repository.deleteSavedPage(page, ApplicationProvider.getApplicationContext())

        assertEquals(listOf(listOf(8L)), offlineObjectDao.deletedPageIds)
        assertEquals(listOf("https://oldschool.runescape.wiki/wiki/Abyssal_whip"), ftsDao.deletedUrls)
        assertEquals(listOf(4151), articleMetaDao.deletedPageIds)
        assertEquals(listOf(page), readingListPageDao.deletedPages)
    }

    @Test
    fun deleteSavedPagesRemovesEveryCacheAndReadingListRow() = runTest {
        val pages = listOf(
            savedPage(id = 8, title = "Abyssal whip", apiTitle = "Abyssal_whip", mediaWikiPageId = 4151),
            savedPage(id = 9, title = "Zulrah", apiTitle = "Zulrah", mediaWikiPageId = 2042)
        )
        val readingListPageDao = FakeReadingListPageDao(pages)
        val ftsDao = FakeOfflinePageFtsDao()
        val offlineObjectDao = FakeOfflineObjectDao()
        val articleMetaDao = FakeArticleMetaDao(
            ArticleMetaEntity(
                pageId = 4151,
                title = "Abyssal whip",
                wikiUrl = "url",
                localFilePath = "/tmp/a.html",
                lastFetchedTimestamp = 1L,
                revisionId = 1L,
                categories = null
            ),
            ArticleMetaEntity(
                pageId = 2042,
                title = "Zulrah",
                wikiUrl = "url",
                localFilePath = "/tmp/z.html",
                lastFetchedTimestamp = 1L,
                revisionId = 1L,
                categories = null
            )
        )
        val repository = repository(
            readingListPageDao = readingListPageDao,
            ftsDao = ftsDao,
            offlineObjectDao = offlineObjectDao,
            articleMetaDao = articleMetaDao
        )

        repository.deleteSavedPages(pages, ApplicationProvider.getApplicationContext())

        assertEquals(listOf(listOf(8L, 9L)), offlineObjectDao.deletedPageIds)
        assertEquals(
            listOf(
                "https://oldschool.runescape.wiki/wiki/Abyssal_whip",
                "https://oldschool.runescape.wiki/wiki/Zulrah"
            ),
            ftsDao.deletedUrls
        )
        assertEquals(listOf(4151, 2042), articleMetaDao.deletedPageIds)
        assertEquals(listOf(8L, 9L), readingListPageDao.deletedPageIds)
    }

    private fun repository(
        readingListPageDao: ReadingListPageDao = FakeReadingListPageDao(),
        ftsDao: OfflinePageFtsDao = FakeOfflinePageFtsDao(),
        offlineObjectDao: OfflineObjectDao = FakeOfflineObjectDao(),
        articleMetaDao: ArticleMetaDao = FakeArticleMetaDao()
    ): SavedPagesRepository {
        return SavedPagesRepository(
            readingListPageDao = readingListPageDao,
            offlinePageFtsDaoProvider = { ftsDao },
            offlineObjectDaoProvider = { offlineObjectDao },
            articleMetaDaoProvider = { articleMetaDao }
        )
    }

    private fun savedPage(
        id: Long,
        title: String,
        apiTitle: String,
        description: String? = null,
        atime: Long = 0,
        mediaWikiPageId: Int? = null
    ): ReadingListPage {
        return ReadingListPage(
            wiki = WikiSite.OSRS_WIKI,
            namespace = Namespace.MAIN,
            displayTitle = title,
            apiTitle = apiTitle,
            description = description,
            id = id,
            mtime = atime,
            atime = atime,
            offline = true,
            status = ReadingListPage.STATUS_SAVED,
            lang = "en",
            mediaWikiPageId = mediaWikiPageId
        )
    }

    private class FakeReadingListPageDao(
        initialPages: List<ReadingListPage> = emptyList()
    ) : ReadingListPageDao {
        private val pages = initialPages.associateBy { it.id }.toMutableMap()
        val deletedPages = mutableListOf<ReadingListPage>()
        val deletedPageIds = mutableListOf<Long>()
        val statusOfflineQueries = mutableListOf<Pair<Long, Boolean>>()

        override fun insertReadingListPage(page: ReadingListPage): Long {
            val id = page.id.takeIf { it != 0L } ?: ((pages.keys.maxOrNull() ?: 0L) + 1L)
            pages[id] = page.copy(id = id)
            return id
        }

        override fun updateReadingListPage(page: ReadingListPage) {
            pages[page.id] = page
        }

        override fun deleteReadingListPage(page: ReadingListPage) {
            deletedPages += page
            pages.remove(page.id)
        }

        override fun getAllPages(): List<ReadingListPage> = pages.values.toList()

        override fun getPageById(id: Long): ReadingListPage? = pages[id]

        override fun getPagesByListId(listId: Long, excludedStatus: Long): List<ReadingListPage> {
            return pages.values.filter { it.listId == listId && it.status != excludedStatus }
        }

        override fun getFullySavedPagesObservable(statusSaved: Long): Flow<List<ReadingListPage>> {
            return flowOf(pages.values.filter { it.offline && it.status == statusSaved })
        }

        override fun getPageByListIdAndTitle(
            wiki: WikiSite,
            lang: String,
            ns: Namespace,
            apiTitle: String,
            listId: Long,
            excludedStatus: Long
        ): ReadingListPage? {
            return pages.values.firstOrNull {
                it.wiki == wiki &&
                    it.lang == lang &&
                    it.namespace == ns &&
                    it.apiTitle == apiTitle &&
                    it.listId == listId &&
                    it.status != excludedStatus
            }
        }

        override suspend fun findPageInAnyList(
            wiki: WikiSite,
            lang: String,
            ns: Namespace,
            apiTitle: String,
            excludedStatus: Long
        ): ReadingListPage? {
            return pages.values.firstOrNull {
                it.wiki == wiki &&
                    it.lang == lang &&
                    it.namespace == ns &&
                    it.apiTitle == apiTitle &&
                    it.status != excludedStatus
            }
        }

        override suspend fun purgePagesByStatus(status: Long) {
            pages.values.removeAll { it.status == status }
        }

        override fun observePageByListIdAndTitle(
            wiki: WikiSite,
            lang: String,
            ns: Namespace,
            apiTitle: String,
            listId: Long
        ): Flow<ReadingListPage?> {
            return flowOf(
                pages.values.firstOrNull {
                    it.wiki == wiki &&
                        it.lang == lang &&
                        it.namespace == ns &&
                        it.apiTitle == apiTitle &&
                        it.listId == listId
                }
            )
        }

        override suspend fun getPagesToProcessForSaving(
            statusQueueForSave: Long,
            statusQueueForForcedSave: Long
        ): List<ReadingListPage> {
            return pages.values.filter {
                it.offline && (it.status == statusQueueForSave || it.status == statusQueueForForcedSave)
            }
        }

        override suspend fun getPagesToProcessForDeleting(statusQueueForDelete: Long): List<ReadingListPage> {
            return pages.values.filter { it.status == statusQueueForDelete }
        }

        override suspend fun updatePageSizeBytes(pageId: Long, newSizeBytes: Long) {
            pages[pageId]?.sizeBytes = newSizeBytes
        }

        override suspend fun updatePageAfterOfflineDeletion(pageId: Long, newStatus: Long, currentTimeMs: Long) {
            pages[pageId]?.let {
                it.status = newStatus
                it.offline = false
                it.sizeBytes = 0
                it.mtime = currentTimeMs
            }
        }

        override fun getPagesByStatusAndOffline(status: Long, offline: Boolean): List<ReadingListPage> {
            statusOfflineQueries += status to offline
            return pages.values.filter { it.status == status && it.offline == offline }
        }

        override fun getPagesByStatus(status: Long): List<ReadingListPage> {
            return pages.values.filter { it.status == status }
        }

        override suspend fun updateStatusForOfflinePages(oldStatus: Long, newStatus: Long, offline: Boolean) {
            pages.values.filter { it.status == oldStatus && it.offline == offline }.forEach {
                it.status = newStatus
            }
        }

        override suspend fun updatePageStatusToSavedAndMtime(
            pageId: Long,
            newStatus: Long,
            currentTimeMs: Long
        ) {
            updatePageStatusToSavedAndMtimeBlocking(pageId, newStatus, currentTimeMs)
        }

        override fun updatePageStatusToSavedAndMtimeBlocking(
            pageId: Long,
            newStatus: Long,
            currentTimeMs: Long
        ) {
            pages[pageId]?.let {
                it.status = newStatus
                it.mtime = currentTimeMs
            }
        }

        override suspend fun updateMediaWikiPageId(id: Long, mwPageId: Int) {
            pages[id]?.mediaWikiPageId = mwPageId
        }

        override suspend fun updatePageRevisionId(id: Long, revisionId: Long) {
            pages[id]?.revId = revisionId
        }

        override suspend fun updatePageDownloadProgress(id: Long, progress: Int) {
            pages[id]?.downloadProgress = progress
        }

        override suspend fun getTotalCacheSizeBytes(statusSaved: Long): Long? {
            return pages.values.filter { it.offline && it.status == statusSaved }.sumOf { it.sizeBytes }
        }

        override suspend fun getOldestSavedPages(statusSaved: Long): List<ReadingListPage> {
            return pages.values.filter { it.offline && it.status == statusSaved }.sortedBy { it.atime }
        }

        override suspend fun deletePagesByIds(pageIds: List<Long>) {
            deletedPageIds += pageIds
            pageIds.forEach { pages.remove(it) }
        }
    }

    private class FakeArticleMetaDao(
        vararg initialMetas: ArticleMetaEntity
    ) : ArticleMetaDao {
        private val metas = initialMetas.associateBy { it.pageId }.toMutableMap()
        val deletedPageIds = mutableListOf<Int>()

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
            deletedPageIds += meta.pageId
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
            return metas.values.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    private class FakeOfflinePageFtsDao(
        private val searchResults: List<OfflinePageFts> = emptyList(),
        private val allEntries: List<OfflinePageFts> = searchResults
    ) : OfflinePageFtsDao {
        val searchQueries = mutableListOf<String>()
        val deletedUrls = mutableListOf<String>()

        override suspend fun insertPageContent(item: OfflinePageFts) = Unit

        override suspend fun deletePageContentByUrl(url: String) {
            deletedUrls += url
        }

        override suspend fun searchAll(query: String): List<OfflinePageFts> {
            searchQueries += query
            return searchResults
        }

        override suspend fun getAll(): List<OfflinePageFts> = allEntries
    }

    private class FakeOfflineObjectDao : OfflineObjectDao {
        val deletedPageIds = mutableListOf<List<Long>>()

        override fun insertOfflineObject(obj: OfflineObject): Long = obj.id

        override fun updateOfflineObject(obj: OfflineObject) = Unit

        override fun getOfflineObject(url: String, lang: String): OfflineObject? = null

        override fun getOfflineObjectByUrl(url: String): OfflineObject? = null

        override fun getObjectsUsedByPageId(readingListPageId: Long): List<OfflineObject> = emptyList()

        override fun deleteObjectsForPageIds(readingListPageIds: List<Long>, context: Context) {
            deletedPageIds += readingListPageIds
        }

        override fun deleteOfflineObjectQuery(id: Long) = Unit

        override fun findByUrlAndLangAndSaveType(
            url: String,
            lang: String,
            saveType: String
        ): OfflineObject? = null
    }
}
