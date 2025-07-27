package com.omiyawaki.osrswiki.readinglist.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateReadingListPage(page: ReadingListPage)

    @Delete
    fun deleteReadingListPage(page: ReadingListPage)

    @Query("SELECT * FROM ReadingListPage")
    fun getAllPages(): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE id = :id")
    fun getPageById(id: Long): ReadingListPage?

    @Query("SELECT * FROM ReadingListPage WHERE listId = :listId AND status != :excludedStatus ORDER BY mtime DESC")
    fun getPagesByListId(listId: Long, excludedStatus: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE offline = 1 AND status = :statusSaved ORDER BY atime DESC")
    fun getFullySavedPagesObservable(statusSaved: Long = ReadingListPage.STATUS_SAVED): Flow<List<ReadingListPage>>

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
        var needsSync = false
        pages.forEach { page ->
            val statusRequiresUpdate = if (offline) {
                page.status != ReadingListPage.STATUS_QUEUE_FOR_SAVE && page.status != ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
            } else {
                page.status != ReadingListPage.STATUS_QUEUE_FOR_DELETE
            }

            if (page.offline == offline && !forcedSave && !statusRequiresUpdate) {
                return@forEach // No change needed
            }

            page.offline = offline
            if (offline) {
                page.status = if (forcedSave) ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE else ReadingListPage.STATUS_QUEUE_FOR_SAVE
                page.downloadProgress = 0
            } else {
                page.status = ReadingListPage.STATUS_QUEUE_FOR_DELETE
                page.downloadProgress = 0
            }
            updateReadingListPage(page)
            needsSync = true
        }
        if (needsSync) {
            val appContext = OSRSWikiApp.instance.applicationContext
            Log.e("RLPageDao_SAVE_TEST", "markPagesForOffline: About to enqueue SavedPageSyncWorker. needsSync=true. Context is " + (if(appContext == null) "NULL" else "NOT NULL"))
            SavedPageSyncWorker.enqueue(appContext)
            Log.e("RLPageDao_SAVE_TEST", "markPagesForOffline: Enqueued SavedPageSyncWorker.")
        } else {
            Log.e("RLPageDao_SAVE_TEST", "markPagesForOffline: NOT enqueuing SavedPageSyncWorker. needsSync=false")
        }
    }

    @Transaction
    fun markPagesForDeletion(listId: Long, pages: List<ReadingListPage>) {
        var needsSync = false
        pages.forEach { page ->
            if ((page.listId == listId || listId == -1L) && page.status != ReadingListPage.STATUS_QUEUE_FOR_DELETE) {
                page.status = ReadingListPage.STATUS_QUEUE_FOR_DELETE
                updateReadingListPage(page)
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

    @Query("UPDATE ReadingListPage SET status = :newStatus, offline = 0, sizeBytes = 0, mtime = :currentTimeMs WHERE id = :pageId")
    suspend fun updatePageAfterOfflineDeletion(pageId: Long, newStatus: Long, currentTimeMs: Long)

    // --- Existing utility methods ---
    @Query("SELECT * FROM ReadingListPage WHERE status = :status AND offline = :offline")
    fun getPagesByStatusAndOffline(status: Long, offline: Boolean): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE status = :status")
    fun getPagesByStatus(status: Long): List<ReadingListPage>

    @Query("UPDATE ReadingListPage SET status = :newStatus WHERE status = :oldStatus AND offline = :offline")
    suspend fun updateStatusForOfflinePages(oldStatus: Long, newStatus: Long, offline: Boolean)

    @Query("UPDATE ReadingListPage SET status = :newStatus, mtime = :currentTimeMs WHERE id = :pageId")
    suspend fun updatePageStatusToSavedAndMtime(pageId: Long, newStatus: Long = ReadingListPage.STATUS_SAVED, currentTimeMs: Long)

    // <<< NEW METHOD to update MediaWiki Page ID >>>
    @Query("UPDATE ReadingListPage SET mediaWikiPageId = :mwPageId WHERE id = :id")
    suspend fun updateMediaWikiPageId(id: Long, mwPageId: Int)
    
    // <<< Phase 3: NEW METHOD to update revision ID >>>
    @Query("UPDATE ReadingListPage SET revId = :revisionId WHERE id = :id")
    suspend fun updatePageRevisionId(id: Long, revisionId: Long)
    
    // NEW METHOD to update download progress
    @Query("UPDATE ReadingListPage SET downloadProgress = :progress WHERE id = :id")
    suspend fun updatePageDownloadProgress(id: Long, progress: Int)

    // Cache size management methods
    @Query("SELECT SUM(sizeBytes) FROM ReadingListPage WHERE offline = 1 AND status = :statusSaved")
    suspend fun getTotalCacheSizeBytes(statusSaved: Long = ReadingListPage.STATUS_SAVED): Long?

    @Query("SELECT * FROM ReadingListPage WHERE offline = 1 AND status = :statusSaved ORDER BY atime ASC")
    suspend fun getOldestSavedPages(statusSaved: Long = ReadingListPage.STATUS_SAVED): List<ReadingListPage>

    @Query("DELETE FROM ReadingListPage WHERE id IN (:pageIds)")
    suspend fun deletePagesByIds(pageIds: List<Long>)
}
