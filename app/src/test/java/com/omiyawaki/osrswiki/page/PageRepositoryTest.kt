package com.omiyawaki.osrswiki.page

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.database.ArticleMetaDao
import com.omiyawaki.osrswiki.database.ArticleMetaEntity
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.network.PageExtractsApiResponse
import com.omiyawaki.osrswiki.network.PageImagesApiResponse
import com.omiyawaki.osrswiki.network.PrefixSearchApiResponse
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import com.omiyawaki.osrswiki.network.model.FallbackApiResponse
import com.omiyawaki.osrswiki.network.model.GeneratedSearchApiResponse
import com.omiyawaki.osrswiki.network.model.PageImagesInfo
import com.omiyawaki.osrswiki.network.model.ParseResult
import com.omiyawaki.osrswiki.network.model.SearchApiResponse
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PageRepositoryTest {

    @Test
    fun getArticleUsesSavedReadingListCacheBeforeNetworkWhenNotForced() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cachedFile = writeCachedArticle(context, "offline-vorkath.html", "<html>cached vorkath</html>")
        val articleMetaDao = FakeArticleMetaDao(
            articleMeta(
                pageId = 42,
                title = "Vorkath",
                localFilePath = cachedFile.absolutePath
            )
        )
        val readingListPageDao = FakeReadingListPageDao(
            savedPage(pageId = 42, title = "Vorkath")
        )
        val apiService = FakeWikiApiService(
            pageIdResult = parseResponse(pageId = 42, title = "Vorkath", body = "<p>network vorkath</p>")
        )
        val repository = repository(context, articleMetaDao, readingListPageDao, apiService)

        val states = repository.getArticle(42, Theme.DEFAULT_LIGHT, forceNetwork = false).toList()

        val success = states.filterIsInstance<Result.Success<PageUiState>>().single().data
        assertEquals("<html>cached vorkath</html>", success.htmlContent)
        assertEquals(true, success.isCurrentlyOffline)
        assertEquals(0, apiService.pageIdRequests)
        assertEquals(1, readingListPageDao.readableSnapshotQueries)
    }

    @Test
    fun getArticleKeepsPriorSnapshotReadableDuringForcedSettlement() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cachedFile = writeCachedArticle(context, "offline-legacy.html", "<html>legacy snapshot</html>")
        val articleMetaDao = FakeArticleMetaDao(
            articleMeta(pageId = 43, title = "Legacy page", localFilePath = cachedFile.absolutePath)
        )
        val readingListPageDao = FakeReadingListPageDao(
            savedPage(pageId = 43, title = "Legacy page").copy(
                status = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
                sizeBytes = 4_096L,
                durableSettlementVersion = ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE
            )
        )
        val apiService = FakeWikiApiService(
            pageIdResult = parseResponse(pageId = 43, title = "Legacy page", body = "<p>network</p>")
        )
        val repository = repository(context, articleMetaDao, readingListPageDao, apiService)

        val success = repository.getArticle(43, Theme.DEFAULT_LIGHT, forceNetwork = false)
            .toList()
            .filterIsInstance<Result.Success<PageUiState>>()
            .single()
            .data

        assertEquals("<html>legacy snapshot</html>", success.htmlContent)
        assertEquals(true, success.isCurrentlyOffline)
        assertEquals(0, apiService.pageIdRequests)
    }

    @Test
    fun brandNewPartialQueueNeverMasqueradesAsReadableOfflineSnapshot() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val articleMetaDao = FakeArticleMetaDao()
        val readingListPageDao = FakeReadingListPageDao(
            savedPage(pageId = 44, title = "Partial page").copy(
                status = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
                sizeBytes = 0L
            )
        )
        val apiService = FakeWikiApiService(
            pageIdResult = parseResponse(pageId = 44, title = "Partial page", body = "<p>network complete</p>")
        )
        val repository = repository(context, articleMetaDao, readingListPageDao, apiService)

        val success = repository.getArticle(44, Theme.DEFAULT_LIGHT, forceNetwork = false)
            .toList()
            .filterIsInstance<Result.Success<PageUiState>>()
            .single()
            .data

        assertTrue(success.htmlContent?.contains("network complete") == true)
        assertEquals(false, success.isCurrentlyOffline)
        assertEquals(1, apiService.pageIdRequests)
    }

    @Test
    fun getArticleCanBypassSavedCacheWhenForceNetworkIsTrue() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cachedFile = writeCachedArticle(context, "offline-zulrah.html", "<html>cached zulrah</html>")
        val articleMetaDao = FakeArticleMetaDao(
            articleMeta(
                pageId = 84,
                title = "Zulrah",
                localFilePath = cachedFile.absolutePath
            )
        )
        val readingListPageDao = FakeReadingListPageDao(
            savedPage(pageId = 84, title = "Zulrah")
        )
        val apiService = FakeWikiApiService(
            pageIdResult = parseResponse(pageId = 84, title = "Zulrah", body = "<p>network zulrah</p>")
        )
        val repository = repository(context, articleMetaDao, readingListPageDao, apiService)

        val states = repository.getArticle(84, Theme.DEFAULT_LIGHT, forceNetwork = true).toList()

        val success = states.filterIsInstance<Result.Success<PageUiState>>().single().data
        assertTrue(success.htmlContent?.contains("network zulrah") == true)
        assertEquals(false, success.isCurrentlyOffline)
        assertEquals(1, apiService.pageIdRequests)
        assertEquals(0, readingListPageDao.readableSnapshotQueries)
    }

    @Test
    fun getArticleByTitleFindsSavedPageWhenInternalLinkUsesSpacesAndApiTitleUsesUnderscores() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cachedFile = writeCachedArticle(context, "offline-dragon-boots.html", "<html>cached dragon boots</html>")
        val articleMetaDao = FakeArticleMetaDao(
            articleMeta(
                pageId = 126,
                title = "Dragon boots",
                localFilePath = cachedFile.absolutePath
            )
        )
        val readingListPageDao = FakeReadingListPageDao(
            savedPage(pageId = 126, title = "Dragon boots", apiTitle = "Dragon_boots")
        )
        val apiService = FakeWikiApiService(
            titleResult = parseResponse(pageId = 126, title = "Dragon boots", body = "<p>network dragon boots</p>")
        )
        val repository = repository(context, articleMetaDao, readingListPageDao, apiService)

        val states = repository.getArticleByTitle("Dragon boots", Theme.DEFAULT_LIGHT, forceNetwork = false).toList()

        val success = states.filterIsInstance<Result.Success<PageUiState>>().single().data
        assertEquals("<html>cached dragon boots</html>", success.htmlContent)
        assertEquals(true, success.isCurrentlyOffline)
        assertEquals(0, apiService.titleRequests)
        assertEquals(listOf("Dragon boots", "Dragon_boots"), readingListPageDao.titleQueries)
    }

    @Test
    fun getOfflineArticleByTitleReturnsNullWithoutNetworkFetchWhenTargetIsUncached() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val articleMetaDao = FakeArticleMetaDao()
        val readingListPageDao = FakeReadingListPageDao()
        val apiService = FakeWikiApiService(
            titleResult = parseResponse(pageId = 200, title = "Dragon equipment", body = "<p>network dragon equipment</p>")
        )
        val repository = repository(context, articleMetaDao, readingListPageDao, apiService)

        val offlineArticle = repository.getOfflineArticleByTitle("Dragon equipment")

        assertNull(offlineArticle)
        assertEquals(0, apiService.titleRequests)
        assertEquals(listOf("Dragon equipment", "Dragon_equipment"), readingListPageDao.titleQueries)
    }

    private fun repository(
        context: Context,
        articleMetaDao: ArticleMetaDao,
        readingListPageDao: ReadingListPageDao,
        apiService: FakeWikiApiService
    ): PageRepository {
        return PageRepository(
            localDataSource = PageLocalDataSource(articleMetaDao, context),
            remoteDataSource = PageRemoteDataSource(apiService),
            htmlBuilder = PageHtmlBuilder(context),
            readingListPageDao = readingListPageDao
        )
    }

    private fun writeCachedArticle(context: Context, fileName: String, content: String): File {
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        return file
    }

    private fun articleMeta(pageId: Int, title: String, localFilePath: String): ArticleMetaEntity {
        return ArticleMetaEntity(
            pageId = pageId,
            title = title,
            wikiUrl = "https://oldschool.runescape.wiki/w/${title.replace(' ', '_')}",
            localFilePath = localFilePath,
            lastFetchedTimestamp = 1_700_000_000L,
            revisionId = 123L,
            categories = null
        )
    }

    private fun savedPage(pageId: Int, title: String, apiTitle: String = title): ReadingListPage {
        return ReadingListPage(
            wiki = WikiSite.OSRS_WIKI,
            namespace = Namespace.MAIN,
            displayTitle = title,
            apiTitle = apiTitle,
            id = pageId.toLong(),
            offline = true,
            status = ReadingListPage.STATUS_SAVED,
            lang = "en",
            mediaWikiPageId = pageId
        )
    }

    private fun parseResponse(pageId: Int, title: String, body: String): ArticleParseApiResponse {
        return ArticleParseApiResponse(
            parse = ParseResult(
                title = title,
                pageid = pageId,
                revid = 456L,
                text = body,
                displaytitle = title
            )
        )
    }

    private class FakeArticleMetaDao(
        vararg initialMetas: ArticleMetaEntity
    ) : ArticleMetaDao {
        private val metas = initialMetas.associateBy { it.pageId }.toMutableMap()

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
            return metas.values.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    private class FakeReadingListPageDao(
        vararg initialPages: ReadingListPage
    ) : ReadingListPageDao {
        private val pages = initialPages.associateBy { it.id }.toMutableMap()
        val statusOfflineQueries = mutableListOf<Pair<Long, Boolean>>()
        val titleQueries = mutableListOf<String>()
        var readableSnapshotQueries = 0

        override fun insertReadingListPage(page: ReadingListPage): Long {
            val id = page.id.takeIf { it != 0L } ?: ((pages.keys.maxOrNull() ?: 0L) + 1L)
            pages[id] = page.copy(id = id)
            return id
        }

        override fun getAllPages(): List<ReadingListPage> = pages.values.toList()

        override fun getPageById(id: Long): ReadingListPage? = pages[id]

        override fun getPagesByListId(listId: Long, excludedStatus: Long): List<ReadingListPage> {
            return pages.values.filter { it.listId == listId && it.status != excludedStatus }
        }

        override fun getFullySavedPagesObservable(
            statusSaved: Long,
            statusForcedSave: Long,
            statusError: Long
        ): Flow<List<ReadingListPage>> {
            readableSnapshotQueries += 1
            return flowOf(pages.values.filter {
                it.offline && (
                    it.status == statusSaved ||
                        (it.sizeBytes > 0 && it.status in setOf(statusForcedSave, statusError))
                    )
            })
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
            titleQueries += apiTitle
            return pages.values.firstOrNull {
                it.wiki == wiki &&
                    it.lang == lang &&
                    it.namespace == ns &&
                    it.apiTitle == apiTitle &&
                    it.status != excludedStatus
            }
        }

        override fun transitionPageToRegularOfflineSave(
            pageId: Long,
            queuedStatus: Long,
            forcedQueuedStatus: Long
        ): Int {
            val page = pages[pageId] ?: return 0
            if (page.offline && page.status in setOf(queuedStatus, forcedQueuedStatus)) return 0
            page.offline = true
            page.status = queuedStatus
            page.downloadProgress = 0
            return 1
        }

        override fun transitionPageToForcedOfflineSave(
            pageId: Long,
            forcedQueuedStatus: Long
        ): Int {
            val page = pages[pageId] ?: return 0
            if (page.offline && page.status == forcedQueuedStatus) return 0
            page.offline = true
            page.status = forcedQueuedStatus
            page.downloadProgress = 0
            return 1
        }

        override fun transitionPageToOfflineDelete(pageId: Long, deleteStatus: Long): Int {
            val page = pages[pageId] ?: return 0
            if (!page.offline && page.status == deleteStatus) return 0
            page.offline = false
            page.status = deleteStatus
            page.downloadProgress = 0
            return 1
        }

        override fun transitionPageToDelete(pageId: Long, listId: Long, deleteStatus: Long): Int {
            val page = pages[pageId] ?: return 0
            if ((listId != -1L && page.listId != listId) || page.status == deleteStatus) return 0
            page.status = deleteStatus
            page.downloadProgress = 0
            return 1
        }

        override suspend fun transitionPagesToDelete(
            pageIds: List<Long>,
            deleteStatus: Long
        ): Int = pageIds.sumOf { transitionPageToDelete(it, -1L, deleteStatus) }

        override suspend fun getPagesByIdsAndStatus(
            pageIds: List<Long>,
            status: Long
        ): List<ReadingListPage> = pages.values.filter { it.id in pageIds && it.status == status }

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

        override suspend fun updatePageAfterOfflineDeletion(
            pageId: Long,
            newStatus: Long,
            currentTimeMs: Long,
            noSettlementVersion: Int
        ) {
            pages[pageId]?.let {
                it.status = newStatus
                it.offline = false
                it.sizeBytes = 0
                it.durableSettlementVersion = noSettlementVersion
                it.mtime = currentTimeMs
            }
        }

        override suspend fun queueExistingPageForSave(
            pageId: Long,
            currentTimeMs: Long,
            queuedStatus: Long
        ): Int {
            val page = pages[pageId] ?: return 0
            page.offline = true
            page.status = queuedStatus
            page.downloadProgress = 0
            page.mtime = currentTimeMs
            return 1
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

        override suspend fun transitionQueuedSaveToSaved(
            pageId: Long,
            newSizeBytes: Long,
            currentTimeMs: Long,
            savedStatus: Long,
            queuedStatus: Long,
            forcedQueuedStatus: Long,
            settlementVersion: Int
        ): Int {
            val page = pages[pageId]
            if (page == null || page.status !in setOf(queuedStatus, forcedQueuedStatus)) return 0
            page.status = savedStatus
            page.mtime = currentTimeMs
            page.downloadProgress = 100
            page.sizeBytes = newSizeBytes
            page.durableSettlementVersion = settlementVersion
            return 1
        }

        override suspend fun transitionQueuedSaveToError(
            pageId: Long,
            currentTimeMs: Long,
            errorStatus: Long,
            queuedStatus: Long,
            forcedQueuedStatus: Long
        ): Int {
            val page = pages[pageId]
            if (page == null || page.status !in setOf(queuedStatus, forcedQueuedStatus)) return 0
            page.status = errorStatus
            page.mtime = currentTimeMs
            page.downloadProgress = 0
            return 1
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

        override suspend fun updateQueuedSaveDownloadProgress(
            id: Long,
            progress: Int,
            queuedStatus: Long,
            forcedQueuedStatus: Long
        ): Int {
            val page = pages[id]
            if (page == null || page.status !in setOf(queuedStatus, forcedQueuedStatus)) return 0
            page.downloadProgress = progress
            return 1
        }

        override suspend fun getTotalCacheSizeBytes(
            statusSaved: Long,
            statusForcedSave: Long,
            statusError: Long
        ): Long? {
            return pages.values.filter {
                it.offline && (
                    it.status == statusSaved ||
                        (it.sizeBytes > 0 && it.status in setOf(statusForcedSave, statusError))
                    )
            }.sumOf { it.sizeBytes }
        }

        override suspend fun getOldestSavedPages(
            statusSaved: Long,
            statusError: Long
        ): List<ReadingListPage> {
            return pages.values.filter {
                it.offline && (
                    it.status == statusSaved ||
                        (it.sizeBytes > 0 && it.status == statusError)
                    )
            }.sortedBy { it.atime }
        }

        override suspend fun hasOfflineReferenceForMediaWikiPageId(
            mediaWikiPageId: Int,
            deleteStatus: Long
        ): Boolean = pages.values.any {
            it.offline && it.status != deleteStatus && it.mediaWikiPageId == mediaWikiPageId
        }

        override suspend fun hasOfflineReferenceForPageIdentity(
            wiki: WikiSite,
            lang: String,
            namespace: Namespace,
            apiTitle: String,
            deleteStatus: Long
        ): Boolean = pages.values.any {
            it.offline && it.status != deleteStatus && it.wiki == wiki && it.lang == lang &&
                it.namespace == namespace && it.apiTitle == apiTitle
        }

        override suspend fun purgeClaimedPagesByIds(
            pageIds: List<Long>,
            deleteStatus: Long
        ): Int {
            val matches = pages.values.filter { it.id in pageIds && it.status == deleteStatus }
            matches.forEach { pages.remove(it.id) }
            return matches.size
        }
    }

    private class FakeWikiApiService(
        private val pageIdResult: ArticleParseApiResponse = ArticleParseApiResponse(
            parse = ParseResult(
                title = "Unused",
                pageid = 1,
                revid = 1L,
                text = "<p>unused</p>",
                displaytitle = "Unused"
            )
        ),
        private val titleResult: ArticleParseApiResponse = pageIdResult
    ) : WikiApiService {
        var pageIdRequests = 0
            private set
        var titleRequests = 0
            private set

        override suspend fun generatedPrefixSearch(
            query: String,
            limit: Int,
            offset: Int,
            thumbSize: Int
        ): GeneratedSearchApiResponse = error("Not used")

        override suspend fun generatedTitlePrefixSearch(
            query: String,
            limit: Int,
            thumbSize: Int
        ): GeneratedSearchApiResponse = error("Not used")

        override suspend fun generatedNamespacedSearch(
            query: String,
            namespace: Int,
            limit: Int,
            offset: Int,
            thumbSize: Int
        ): GeneratedSearchApiResponse = error("Not used")

        override suspend fun generatedRecentChanges(
            namespace: Int,
            limit: Int,
            continueToken: String?,
            thumbSize: Int
        ): GeneratedSearchApiResponse = error("Not used")

        override suspend fun searchPages(query: String, limit: Int, offset: Int): SearchApiResponse {
            error("Not used")
        }

        override suspend fun getPageExtract(pageIds: String): FallbackApiResponse = error("Not used")

        override suspend fun prefixSearchArticles(query: String, limit: Int, offset: Int): PrefixSearchApiResponse {
            error("Not used")
        }

        override suspend fun getPageExtracts(pageIds: String): PageExtractsApiResponse = error("Not used")

        override suspend fun getPageExtractsByTitles(titles: String): PageExtractsApiResponse = error("Not used")

        override suspend fun getHistoryPreviewMetadata(titles: String, thumbSize: Int): PageExtractsApiResponse =
            error("Not used")

        override suspend fun openSearch(query: String, limit: Int): okhttp3.ResponseBody =
            error("Not used")

        override suspend fun getArticleParseDataByPageId(pageId: Int): ArticleParseApiResponse {
            pageIdRequests += 1
            return pageIdResult
        }

        override suspend fun getArticleParseDataByTitle(title: String): ArticleParseApiResponse {
            titleRequests += 1
            return titleResult
        }

        override suspend fun getImageInfo(titles: String): ImageInfoResponse = error("Not used")

        override suspend fun getPageThumbnails(pageIds: String, thumbSize: Int): PageImagesApiResponse {
            error("Not used")
        }

        override suspend fun getArticleImageInfo(pageId: Int): PageImagesInfo = error("Not used")
    }
}
