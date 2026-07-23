package com.omiyawaki.osrswiki.savedpages

import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class SavedPageSyncStatusRecorderTest {

    @Test
    fun markSaveFailurePersistsRetryableErrorState() = runTest {
        val readingListPageDao = mock<ReadingListPageDao>()
        val page = ReadingListPage(
            wiki = WikiSite.OSRS_WIKI,
            namespace = Namespace.MAIN,
            displayTitle = "Dragon boots",
            apiTitle = "Dragon_boots",
            id = 77,
            offline = true,
            status = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
            lang = "en",
            downloadProgress = 70
        )

        SavedPageSyncStatusRecorder.markSaveFailure(
            readingListPageDao = readingListPageDao,
            page = page,
            currentTimeMs = 1_700_000_000L
        )

        verify(readingListPageDao).updatePageDownloadProgress(77, 0)
        verify(readingListPageDao).updatePageStatusToSavedAndMtime(
            77,
            ReadingListPage.STATUS_ERROR,
            1_700_000_000L
        )
        verifyNoMoreInteractions(readingListPageDao)
    }
}
