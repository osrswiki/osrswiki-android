package com.omiyawaki.osrswiki.savedpages

import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao

internal object SavedPageSyncStatusRecorder {
    suspend fun markSaveSuccess(
        readingListPageDao: ReadingListPageDao,
        pageId: Long,
        totalSizeBytes: Long,
        currentTimeMs: Long
    ): Boolean = readingListPageDao.transitionQueuedSaveToSaved(
        pageId = pageId,
        newSizeBytes = totalSizeBytes,
        currentTimeMs = currentTimeMs
    ) == 1

    suspend fun markSaveFailure(
        readingListPageDao: ReadingListPageDao,
        pageId: Long,
        currentTimeMs: Long
    ): Boolean = readingListPageDao.transitionQueuedSaveToError(
        pageId = pageId,
        currentTimeMs = currentTimeMs
    ) == 1
}

internal object SavedPageSaveCompletionPolicy {
    fun isComplete(
        htmlFetched: Boolean,
        textIndexed: Boolean,
        articlePersisted: Boolean,
        assetsPersisted: Boolean
    ): Boolean = htmlFetched && textIndexed && articlePersisted && assetsPersisted
}
