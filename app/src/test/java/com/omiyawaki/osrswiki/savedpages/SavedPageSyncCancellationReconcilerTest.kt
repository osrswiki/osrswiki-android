package com.omiyawaki.osrswiki.savedpages

import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SavedPageSyncCancellationReconcilerTest {
    @Test
    fun cancelledQueuedPageBecomesRetryableAndOtherPendingWorkResumes() = runTest {
        val remaining = page(8, ReadingListPage.STATUS_QUEUE_FOR_SAVE)
        val dao = mock<ReadingListPageDao> {
            onBlocking { transitionQueuedSaveToError(7, 1234L) } doReturn 1
            onBlocking { getPagesToProcessForSaving() } doReturn listOf(remaining)
            onBlocking { getPagesToProcessForDeleting() } doReturn emptyList()
        }

        val outcome = SavedPageSyncCancellationReconciler.reconcile(dao, 7, 1234L)
        assertTrue(outcome.terminalTransitionWon)
        assertTrue(outcome.shouldResumeQueue)
        verify(dao).transitionQueuedSaveToError(7, 1234L)
    }

    @Test
    fun completionThatBeatCancellationStaysSavedAndDoesNotCreateMoreWork() = runTest {
        val dao = mock<ReadingListPageDao> {
            onBlocking { transitionQueuedSaveToError(7, 1234L) } doReturn 0
            onBlocking { getPagesToProcessForSaving() } doReturn emptyList()
            onBlocking { getPagesToProcessForDeleting() } doReturn emptyList()
        }

        val outcome = SavedPageSyncCancellationReconciler.reconcile(dao, 7, 1234L)
        assertFalse(outcome.terminalTransitionWon)
        assertFalse(outcome.shouldResumeQueue)
        verify(dao).transitionQueuedSaveToError(7, 1234L)
    }

    private fun page(id: Long, status: Long) = ReadingListPage(
        wiki = WikiSite.OSRS_WIKI,
        namespace = Namespace.MAIN,
        displayTitle = "Page $id",
        apiTitle = "Page $id",
        id = id,
        offline = true,
        status = status,
        lang = "en"
    )
}
