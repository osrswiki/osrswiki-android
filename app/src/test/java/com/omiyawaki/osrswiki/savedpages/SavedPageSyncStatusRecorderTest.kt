package com.omiyawaki.osrswiki.savedpages

import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class SavedPageSyncStatusRecorderTest {

    @Test
    fun markSaveFailurePersistsRetryableErrorState() = runTest {
        val readingListPageDao = mock<ReadingListPageDao> {
            onBlocking { transitionQueuedSaveToError(77, 1_700_000_000L) } doReturn 1
        }

        assertTrue(
            SavedPageSyncStatusRecorder.markSaveFailure(
                readingListPageDao = readingListPageDao,
                pageId = 77,
                currentTimeMs = 1_700_000_000L
            )
        )

        verify(readingListPageDao).transitionQueuedSaveToError(77, 1_700_000_000L)
        verifyNoMoreInteractions(readingListPageDao)
    }

    @Test
    fun staleWorkerCompletionReportsCasLossAndCannotOverwriteCancel() = runTest {
        val readingListPageDao = mock<ReadingListPageDao> {
            onBlocking { transitionQueuedSaveToSaved(77, 456L, 1_700_000_001L) } doReturn 0
        }

        assertFalse(
            SavedPageSyncStatusRecorder.markSaveSuccess(
                readingListPageDao = readingListPageDao,
                pageId = 77,
                totalSizeBytes = 456L,
                currentTimeMs = 1_700_000_001L
            )
        )

        verify(readingListPageDao).transitionQueuedSaveToSaved(77, 456L, 1_700_000_001L)
        verifyNoMoreInteractions(readingListPageDao)
    }
}
