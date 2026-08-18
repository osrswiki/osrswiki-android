package com.omiyawaki.osrswiki.savedpages

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SavedPageSyncTerminalStatusDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun notificationCancellationWinsThenLatePhysicalWorkerCannotResurrectSaved() = runTest {
        val dao = database.readingListPageDao()
        val id = dao.insertReadingListPage(
            page(ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE).copy(sizeBytes = 2_048L)
        )

        assertEquals(1, dao.transitionQueuedSaveToError(id, currentTimeMs = 1_000L))
        assertEquals(0, dao.updateQueuedSaveDownloadProgress(id, 95))
        assertEquals(
            0,
            dao.transitionQueuedSaveToSaved(
                pageId = id,
                newSizeBytes = 99_999L,
                currentTimeMs = 2_000L
            )
        )

        val settled = requireNotNull(dao.getPageById(id))
        assertEquals(ReadingListPage.STATUS_ERROR, settled.status)
        assertEquals(0, settled.downloadProgress)
        assertEquals(1_000L, settled.mtime)
        assertEquals(2_048L, settled.sizeBytes)
        assertEquals(ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE, settled.durableSettlementVersion)
        assertEquals(true, settled.offline)
        assertEquals(true, settled.hasReadableOfflineSnapshot)
    }

    @Test
    fun physicalWorkerCompletionWinsThenLateCancellationCannotFlipSavedToError() = runTest {
        val dao = database.readingListPageDao()
        val id = dao.insertReadingListPage(page(ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE))

        assertEquals(
            1,
            dao.transitionQueuedSaveToSaved(
                pageId = id,
                newSizeBytes = 54_321L,
                currentTimeMs = 3_000L
            )
        )
        assertEquals(0, dao.transitionQueuedSaveToError(id, currentTimeMs = 4_000L))

        val settled = requireNotNull(dao.getPageById(id))
        assertEquals(ReadingListPage.STATUS_SAVED, settled.status)
        assertEquals(100, settled.downloadProgress)
        assertEquals(54_321L, settled.sizeBytes)
        assertEquals(3_000L, settled.mtime)
        assertEquals(
            ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION,
            settled.durableSettlementVersion
        )
    }

    @Test
    fun savedListKeepsPriorSnapshotVisibleDuringForcedRefreshAndRetryableError() = runTest {
        val dao = database.readingListPageDao()
        val forcedId = dao.insertReadingListPage(
            page(ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE).copy(sizeBytes = 8_192L)
        )
        val partialId = dao.insertReadingListPage(
            page(ReadingListPage.STATUS_QUEUE_FOR_SAVE).copy(
                displayTitle = "Brand-new partial",
                apiTitle = "Brand-new_partial",
                sizeBytes = 0L
            )
        )

        var visible = dao.getFullySavedPagesObservable().first()
        assertEquals(listOf(forcedId), visible.map { it.id })

        assertEquals(1, dao.transitionQueuedSaveToError(forcedId, currentTimeMs = 5_000L))
        visible = dao.getFullySavedPagesObservable().first()
        assertEquals(listOf(forcedId), visible.map { it.id })
        assertEquals(ReadingListPage.STATUS_ERROR, visible.single().status)
        assertEquals(false, visible.any { it.id == partialId })

        dao.updatePageStatusToSavedAndMtime(
            forcedId,
            ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
            currentTimeMs = 6_000L
        )
        assertEquals(
            1,
            dao.transitionQueuedSaveToSaved(
                pageId = forcedId,
                newSizeBytes = 9_999L,
                currentTimeMs = 7_000L
            )
        )
        val retried = requireNotNull(dao.getPageById(forcedId))
        assertEquals(ReadingListPage.STATUS_SAVED, retried.status)
        assertEquals(
            ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION,
            retried.durableSettlementVersion
        )
    }

    @Test
    fun cacheQuotaCountsEveryReadableSnapshotButEvictionSkipsActiveForcedRefresh() = runTest {
        val dao = database.readingListPageDao()
        val currentId = dao.insertReadingListPage(
            page(ReadingListPage.STATUS_SAVED).copy(sizeBytes = 100L, atime = 4L)
        )
        val forcedId = dao.insertReadingListPage(
            page(ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE).copy(
                displayTitle = "Forced",
                apiTitle = "Forced",
                sizeBytes = 200L,
                atime = 1L
            )
        )
        val errorId = dao.insertReadingListPage(
            page(ReadingListPage.STATUS_ERROR).copy(
                displayTitle = "Error",
                apiTitle = "Error",
                sizeBytes = 300L,
                atime = 2L
            )
        )
        dao.insertReadingListPage(
            page(ReadingListPage.STATUS_QUEUE_FOR_SAVE).copy(
                displayTitle = "Partial",
                apiTitle = "Partial",
                sizeBytes = 400L,
                atime = 0L
            )
        )

        assertEquals(600L, dao.getTotalCacheSizeBytes())
        assertEquals(
            listOf(errorId, currentId),
            dao.getOldestSavedPages().map { it.id }
        )
        assertEquals(false, dao.getOldestSavedPages().any { it.id == forcedId })
    }

    private fun page(status: Long) = ReadingListPage(
        wiki = WikiSite.OSRS_WIKI,
        namespace = Namespace.MAIN,
        displayTitle = "Terminal race",
        apiTitle = "Terminal_race",
        offline = true,
        status = status,
        lang = "en",
        downloadProgress = 95
    )
}
