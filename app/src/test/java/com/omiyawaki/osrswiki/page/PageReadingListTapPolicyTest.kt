package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageReadingListTapPolicyTest {
    @Test
    fun queuedOrDownloadingTapCancelsAndCanNeverQueueDuplicate() {
        val entry = page(status = ReadingListPage.STATUS_QUEUE_FOR_SAVE)
        assertEquals(PageSaveTapAction.CANCEL_ACTIVE_SAVE, PageSaveTapPolicy.resolve(entry))

        entry.status = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
        assertEquals(PageSaveTapAction.CANCEL_ACTIVE_SAVE, PageSaveTapPolicy.resolve(entry))
    }

    @Test
    fun inAppCancelRemainsEnabledWithoutDependingOnNotificationPermission() {
        assertTrue(PageSaveButtonPolicy.isEnabled(PageActionBarManager.SaveState.DOWNLOADING))
        assertFalse(PageSaveButtonPolicy.isEnabled(PageActionBarManager.SaveState.CANCELLING))
    }

    @Test
    fun otherPersistedStatesRemainUnambiguous() {
        assertEquals(PageSaveTapAction.QUEUE_NEW_SAVE, PageSaveTapPolicy.resolve(null))
        assertEquals(
            PageSaveTapAction.REMOVE_SAVED,
            PageSaveTapPolicy.resolve(page(status = ReadingListPage.STATUS_SAVED))
        )
        assertEquals(
            PageSaveTapAction.RETRY_FAILED_SAVE,
            PageSaveTapPolicy.resolve(page(status = ReadingListPage.STATUS_ERROR))
        )
        assertEquals(
            PageSaveTapAction.NO_OP,
            PageSaveTapPolicy.resolve(page(status = ReadingListPage.STATUS_QUEUE_FOR_DELETE))
        )
    }

    @Test
    fun retryPreservesPriorSnapshotThroughForcedQueueButNewFailureUsesNormalQueue() {
        assertEquals(
            ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
            page(status = ReadingListPage.STATUS_ERROR).copy(sizeBytes = 2_048L).retryQueueStatus
        )
        assertEquals(
            ReadingListPage.STATUS_QUEUE_FOR_SAVE,
            page(status = ReadingListPage.STATUS_ERROR).copy(sizeBytes = 0L).retryQueueStatus
        )
    }

    private fun page(status: Long) = ReadingListPage(
        wiki = WikiSite.OSRS_WIKI,
        namespace = Namespace.MAIN,
        displayTitle = "Amulet of glory",
        apiTitle = "Amulet of glory",
        id = 91,
        offline = true,
        status = status,
        lang = "en"
    )
}
