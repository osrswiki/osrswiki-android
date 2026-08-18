package com.omiyawaki.osrswiki.readinglist.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingListPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReadingListPage(page: ReadingListPage): Long

    @Query("SELECT * FROM ReadingListPage")
    fun getAllPages(): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE id = :id")
    fun getPageById(id: Long): ReadingListPage?

    @Query("SELECT * FROM ReadingListPage WHERE listId = :listId AND status != :excludedStatus ORDER BY mtime DESC")
    fun getPagesByListId(listId: Long, excludedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE): List<ReadingListPage>

    /**
     * Authoritative readable-snapshot stream shared by Saved list, search, open, and delete-all.
     * A forced refresh/error may expose prior non-empty bytes; a brand-new partial save may not.
     */
    @Query(
        """
        SELECT * FROM ReadingListPage
        WHERE offline = 1
          AND (
            status = :statusSaved
            OR (sizeBytes > 0 AND status IN (:statusForcedSave, :statusError))
          )
        ORDER BY atime DESC
        """
    )
    fun getFullySavedPagesObservable(
        statusSaved: Long = ReadingListPage.STATUS_SAVED,
        statusForcedSave: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
        statusError: Long = ReadingListPage.STATUS_ERROR
    ): Flow<List<ReadingListPage>>

    @Query("SELECT * FROM ReadingListPage WHERE wiki = :wiki AND lang = :lang AND namespace = :ns AND apiTitle = :apiTitle AND listId = :listId AND status != :excludedStatus LIMIT 1")
    fun getPageByListIdAndTitle(wiki: WikiSite, lang: String, ns: Namespace, apiTitle: String, listId: Long, excludedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE): ReadingListPage?

    @Query("SELECT * FROM ReadingListPage WHERE wiki = :wiki AND lang = :lang AND namespace = :ns AND apiTitle = :apiTitle AND status != :excludedStatus")
    suspend fun findPageInAnyList(wiki: WikiSite, lang: String, ns: Namespace, apiTitle: String, excludedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE): ReadingListPage?

    @Transaction
    fun addPagesToList(list: ReadingList, titles: List<PageTitle>, downloadEnabled: Boolean): List<String> {
        val addedDisplayTitles = mutableListOf<String>()
        titles.forEach { title ->
            if (getPageByListIdAndTitle(title.wikiSite, title.wikiSite.languageCode, title.namespace(), title.prefixedText, list.id) == null) {
                val newPage = ReadingListPage(title).apply {
                    this.listId = list.id
                    this.offline = downloadEnabled
                    if (this.offline) {
                        this.status = ReadingListPage.STATUS_QUEUE_FOR_SAVE
                    } else {
                        this.status = ReadingListPage.STATUS_SAVED
                        this.sizeBytes = 0
                    }
                    // mediaWikiPageId is not set here, will be set by SavedPageSyncWorker
                }
                insertReadingListPage(newPage)
                addedDisplayTitles.add(title.displayText)
            }
        }
        if (addedDisplayTitles.isNotEmpty() && downloadEnabled) {
            val appContext = OSRSWikiApp.instance.applicationContext
            Log.e("RLPageDao_SAVE_TEST", "addPagesToList: About to enqueue SavedPageSyncWorker. downloadEnabled=true. Context is " + (if(appContext == null) "NULL" else "NOT NULL"))
            SavedPageSyncWorker.enqueue(appContext)
            Log.e("RLPageDao_SAVE_TEST", "addPagesToList: Enqueued SavedPageSyncWorker.")
        } else {
            Log.e("RLPageDao_SAVE_TEST", "addPagesToList: NOT enqueuing SavedPageSyncWorker. addedDisplayTitlesEmpty=${addedDisplayTitles.isEmpty()}, downloadEnabled=${downloadEnabled}")
        }
        return addedDisplayTitles
    }

    @Transaction
    fun markPagesForOffline(pages: List<ReadingListPage>, offline: Boolean, forcedSave: Boolean) {
        val needsSync = transitionPageIdsForOffline(
            pageIds = pages.map(ReadingListPage::id),
            offline = offline,
            forcedSave = forcedSave
        ) > 0
        if (needsSync) {
            val appContext = OSRSWikiApp.instance.applicationContext
            Log.e("RLPageDao_SAVE_TEST", "markPagesForOffline: About to enqueue SavedPageSyncWorker. needsSync=true. Context is " + (if(appContext == null) "NULL" else "NOT NULL"))
            SavedPageSyncWorker.enqueue(appContext)
            Log.e("RLPageDao_SAVE_TEST", "markPagesForOffline: Enqueued SavedPageSyncWorker.")
        } else {
            Log.e("RLPageDao_SAVE_TEST", "markPagesForOffline: NOT enqueuing SavedPageSyncWorker. needsSync=false")
        }
    }

    /**
     * Changes only queue ownership fields. Callers may hold UI models from before a snapshot
     * publication; copying those entities back would overwrite its revision, size, identity, and
     * durable-settlement marker.
     */
    @Transaction
    fun transitionPageIdsForOffline(
        pageIds: List<Long>,
        offline: Boolean,
        forcedSave: Boolean
    ): Int {
        if (pageIds.isEmpty()) return 0
        return pageIds.sumOf { pageId ->
            when {
                !offline -> transitionPageToOfflineDelete(pageId)
                forcedSave -> transitionPageToForcedOfflineSave(pageId)
                else -> transitionPageToRegularOfflineSave(pageId)
            }
        }
    }

    @Query(
        """
        UPDATE ReadingListPage
        SET offline = 1,
            status = :queuedStatus,
            downloadProgress = 0
        WHERE id = :pageId
          AND NOT (offline = 1 AND status IN (:queuedStatus, :forcedQueuedStatus))
        """
    )
    fun transitionPageToRegularOfflineSave(
        pageId: Long,
        queuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
        forcedQueuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
    ): Int

    @Query(
        """
        UPDATE ReadingListPage
        SET offline = 1,
            status = :forcedQueuedStatus,
            downloadProgress = 0
        WHERE id = :pageId
          AND NOT (offline = 1 AND status = :forcedQueuedStatus)
        """
    )
    fun transitionPageToForcedOfflineSave(
        pageId: Long,
        forcedQueuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
    ): Int

    @Query(
        """
        UPDATE ReadingListPage
        SET offline = 0,
            status = :deleteStatus,
            downloadProgress = 0
        WHERE id = :pageId
          AND NOT (offline = 0 AND status = :deleteStatus)
        """
    )
    fun transitionPageToOfflineDelete(
        pageId: Long,
        deleteStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): Int

    @Transaction
    fun markPagesForDeletion(listId: Long, pages: List<ReadingListPage>) {
        var needsSync = false
        pages.forEach { page ->
            if (transitionPageToDelete(page.id, listId) == 1) {
                needsSync = true
            }
        }
        if (needsSync) {
            val appContext = OSRSWikiApp.instance.applicationContext
            Log.e("RLPageDao_SAVE_TEST", "markPagesForDeletion: About to enqueue SavedPageSyncWorker. needsSync=true. Context is " + (if(appContext == null) "NULL" else "NOT NULL"))
            SavedPageSyncWorker.enqueue(appContext)
            Log.e("RLPageDao_SAVE_TEST", "markPagesForDeletion: Enqueued SavedPageSyncWorker.")
        } else {
            Log.e("RLPageDao_SAVE_TEST", "markPagesForDeletion: NOT enqueuing SavedPageSyncWorker. needsSync=false")
        }
    }

    /**
     * Status-only deletion claim. A UI row may predate a just-published snapshot; never @Update
     * that stale object over the new revision, size, page identity, or settlement marker.
     */
    @Query(
        """
        UPDATE ReadingListPage
        SET status = :deleteStatus,
            downloadProgress = 0
        WHERE id = :pageId
          AND (:listId = -1 OR listId = :listId)
          AND status != :deleteStatus
        """
    )
    fun transitionPageToDelete(
        pageId: Long,
        listId: Long,
        deleteStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): Int

    /**
     * Claims deletion before any file/metadata cleanup, then returns the latest persisted rows.
     * Room serializes this transaction against snapshot publication, so either publication wins
     * first and these records contain the new generation, or deletion wins and publication loses.
     */
    @Transaction
    suspend fun claimPagesForDeletion(pageIds: List<Long>): List<ReadingListPage> {
        if (pageIds.isEmpty()) return emptyList()
        transitionPagesToDelete(pageIds)
        return getPagesByIdsAndStatus(pageIds)
    }

    @Query(
        """
        UPDATE ReadingListPage
        SET status = :deleteStatus,
            downloadProgress = 0
        WHERE id IN (:pageIds)
          AND status != :deleteStatus
        """
    )
    suspend fun transitionPagesToDelete(
        pageIds: List<Long>,
        deleteStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): Int

    @Query("SELECT * FROM ReadingListPage WHERE id IN (:pageIds) AND status = :status")
    suspend fun getPagesByIdsAndStatus(
        pageIds: List<Long>,
        status: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): List<ReadingListPage>

    @Query("DELETE FROM ReadingListPage WHERE status = :status")
    suspend fun purgePagesByStatus(status: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE)

    @Query("SELECT * FROM ReadingListPage WHERE wiki = :wiki AND lang = :lang AND namespace = :ns AND apiTitle = :apiTitle AND listId = :listId LIMIT 1")
    fun observePageByListIdAndTitle(wiki: WikiSite, lang: String, ns: Namespace, apiTitle: String, listId: Long): Flow<ReadingListPage?>

    // --- Methods for SavedPageSyncWorker ---
    @Query("SELECT * FROM ReadingListPage WHERE offline = 1 AND (status = :statusQueueForSave OR status = :statusQueueForForcedSave)")
    suspend fun getPagesToProcessForSaving(
        statusQueueForSave: Long = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
        statusQueueForForcedSave: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
    ): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE status = :statusQueueForDelete")
    suspend fun getPagesToProcessForDeleting(
        statusQueueForDelete: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): List<ReadingListPage>

    @Query("UPDATE ReadingListPage SET sizeBytes = :newSizeBytes WHERE id = :pageId")
    suspend fun updatePageSizeBytes(pageId: Long, newSizeBytes: Long)

    @Query(
        """
        UPDATE ReadingListPage
        SET status = :newStatus,
            offline = 0,
            sizeBytes = 0,
            durableSettlementVersion = :noSettlementVersion,
            mtime = :currentTimeMs
        WHERE id = :pageId
        """
    )
    suspend fun updatePageAfterOfflineDeletion(
        pageId: Long,
        newStatus: Long,
        currentTimeMs: Long,
        noSettlementVersion: Int = ReadingListPage.DURABLE_SETTLEMENT_VERSION_NONE
    )

    /** Queues an existing online-only row without replacing unrelated persisted columns. */
    @Query(
        """
        UPDATE ReadingListPage
        SET offline = 1,
            status = :queuedStatus,
            downloadProgress = 0,
            mtime = :currentTimeMs
        WHERE id = :pageId
        """
    )
    suspend fun queueExistingPageForSave(
        pageId: Long,
        currentTimeMs: Long,
        queuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_SAVE
    ): Int

    // --- Existing utility methods ---
    @Query("SELECT * FROM ReadingListPage WHERE status = :status AND offline = :offline")
    fun getPagesByStatusAndOffline(status: Long, offline: Boolean): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE status = :status")
    fun getPagesByStatus(status: Long): List<ReadingListPage>

    @Query("UPDATE ReadingListPage SET status = :newStatus WHERE status = :oldStatus AND offline = :offline")
    suspend fun updateStatusForOfflinePages(oldStatus: Long, newStatus: Long, offline: Boolean)

    @Query("UPDATE ReadingListPage SET status = :newStatus, mtime = :currentTimeMs WHERE id = :pageId")
    suspend fun updatePageStatusToSavedAndMtime(pageId: Long, newStatus: Long = ReadingListPage.STATUS_SAVED, currentTimeMs: Long)

    /**
     * Commits a completed explicit save only while this exact row is still queued. Including the
     * terminal progress and size in the same compare-and-set prevents notification cancellation
     * from racing a physical worker teardown into a mixed SAVED/ERROR state.
     */
    @Query(
        """
        UPDATE ReadingListPage
        SET status = :savedStatus,
            mtime = :currentTimeMs,
            downloadProgress = 100,
            sizeBytes = :newSizeBytes,
            durableSettlementVersion = :settlementVersion
        WHERE id = :pageId
          AND status IN (:queuedStatus, :forcedQueuedStatus)
        """
    )
    suspend fun transitionQueuedSaveToSaved(
        pageId: Long,
        newSizeBytes: Long,
        currentTimeMs: Long,
        savedStatus: Long = ReadingListPage.STATUS_SAVED,
        queuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
        forcedQueuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
        settlementVersion: Int = ReadingListPage.CURRENT_DURABLE_SETTLEMENT_VERSION
    ): Int

    /** Commits a retryable terminal error only if save/cancel has not already won the row. */
    @Query(
        """
        UPDATE ReadingListPage
        SET status = :errorStatus,
            mtime = :currentTimeMs,
            downloadProgress = 0
        WHERE id = :pageId
          AND status IN (:queuedStatus, :forcedQueuedStatus)
        """
    )
    suspend fun transitionQueuedSaveToError(
        pageId: Long,
        currentTimeMs: Long,
        errorStatus: Long = ReadingListPage.STATUS_ERROR,
        queuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
        forcedQueuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
    ): Int

    @Query("UPDATE ReadingListPage SET status = :newStatus, mtime = :currentTimeMs WHERE id = :pageId")
    fun updatePageStatusToSavedAndMtimeBlocking(pageId: Long, newStatus: Long = ReadingListPage.STATUS_SAVED, currentTimeMs: Long)

    // <<< NEW METHOD to update MediaWiki Page ID >>>
    @Query("UPDATE ReadingListPage SET mediaWikiPageId = :mwPageId WHERE id = :id")
    suspend fun updateMediaWikiPageId(id: Long, mwPageId: Int)
    
    // <<< Phase 3: NEW METHOD to update revision ID >>>
    @Query("UPDATE ReadingListPage SET revId = :revisionId WHERE id = :id")
    suspend fun updatePageRevisionId(id: Long, revisionId: Long)
    
    /**
     * Advances visible save progress only while the row is still owned by a queued save. Returning
     * zero lets a stale physical worker stop immediately after cancellation wins STATUS_ERROR.
     */
    @Query(
        """
        UPDATE ReadingListPage
        SET downloadProgress = :progress
        WHERE id = :id
          AND status IN (:queuedStatus, :forcedQueuedStatus)
        """
    )
    suspend fun updateQueuedSaveDownloadProgress(
        id: Long,
        progress: Int,
        queuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_SAVE,
        forcedQueuedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
    ): Int

    // Cache size management methods
    @Query(
        """
        SELECT SUM(sizeBytes) FROM ReadingListPage
        WHERE offline = 1
          AND (
            status = :statusSaved
            OR (sizeBytes > 0 AND status IN (:statusForcedSave, :statusError))
          )
        """
    )
    suspend fun getTotalCacheSizeBytes(
        statusSaved: Long = ReadingListPage.STATUS_SAVED,
        statusForcedSave: Long = ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE,
        statusError: Long = ReadingListPage.STATUS_ERROR
    ): Long?

    @Query(
        """
        SELECT * FROM ReadingListPage
        WHERE offline = 1
          AND (
            status = :statusSaved
            OR (sizeBytes > 0 AND status = :statusError)
          )
        ORDER BY atime ASC
        """
    )
    suspend fun getOldestSavedPages(
        statusSaved: Long = ReadingListPage.STATUS_SAVED,
        statusError: Long = ReadingListPage.STATUS_ERROR
    ): List<ReadingListPage>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM ReadingListPage
            WHERE offline = 1
              AND status != :deleteStatus
              AND mediaWikiPageId = :mediaWikiPageId
        )
        """
    )
    suspend fun hasOfflineReferenceForMediaWikiPageId(
        mediaWikiPageId: Int,
        deleteStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM ReadingListPage
            WHERE offline = 1
              AND status != :deleteStatus
              AND wiki = :wiki
              AND lang = :lang
              AND namespace = :namespace
              AND apiTitle = :apiTitle
        )
        """
    )
    suspend fun hasOfflineReferenceForPageIdentity(
        wiki: WikiSite,
        lang: String,
        namespace: Namespace,
        apiTitle: String,
        deleteStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): Boolean

    @Query(
        """
        DELETE FROM ReadingListPage
        WHERE id IN (:pageIds)
          AND status = :deleteStatus
        """
    )
    suspend fun purgeClaimedPagesByIds(
        pageIds: List<Long>,
        deleteStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE
    ): Int
}
