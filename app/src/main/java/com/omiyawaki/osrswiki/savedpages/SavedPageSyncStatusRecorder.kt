package com.omiyawaki.osrswiki.savedpages

import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao

internal object SavedPageSyncStatusRecorder {
    suspend fun markSaveFailure(
        readingListPageDao: ReadingListPageDao,
        page: ReadingListPage,
        currentTimeMs: Long
    ) {
        readingListPageDao.updatePageDownloadProgress(page.id, 0)
        readingListPageDao.updatePageStatusToSavedAndMtime(
            page.id,
            ReadingListPage.STATUS_ERROR,
            currentTimeMs
        )
    }
}
