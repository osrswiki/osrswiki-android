package com.omiyawaki.osrswiki.readinglist.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.omiyawaki.osrswiki.OSRSWikiApp // Import your Application class
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.Namespace
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker // Import the worker
// TODO: Uncomment and implement/adapt these dependencies later
// import com.omiyawaki.osrswiki.concurrency.FlowEventBus
// import com.omiyawaki.osrswiki.events.ArticleSavedOrDeletedEvent
// import com.omiyawaki.osrswiki.readinglist.sync.ReadingListSyncAdapter

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
                }
                insertReadingListPage(newPage)
                addedDisplayTitles.add(title.displayText)
            }
        }
        if (addedDisplayTitles.isNotEmpty() && downloadEnabled) {
            SavedPageSyncWorker.enqueue(OSRSWikiApp.instance.applicationContext)
        }
        // TODO: FlowEventBus.post(ArticleSavedOrDeletedEvent(true, ...))
        return addedDisplayTitles
    }

    @Transaction
    fun markPagesForOffline(pages: List<ReadingListPage>, offline: Boolean, forcedSave: Boolean) {
        var needsSync = false
        pages.forEach { page ->
            // Condition to check if an update is actually needed to avoid redundant operations/syncs
            val statusRequiresUpdate = if (offline) {
                page.status != ReadingListPage.STATUS_QUEUE_FOR_SAVE && page.status != ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE
            } else {
                page.status != ReadingListPage.STATUS_QUEUE_FOR_DELETE
            }

            if (page.offline == offline && !forcedSave && !statusRequiresUpdate) {
                return@forEach // No change needed for this page under these conditions
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
            SavedPageSyncWorker.enqueue(OSRSWikiApp.instance.applicationContext)
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
            SavedPageSyncWorker.enqueue(OSRSWikiApp.instance.applicationContext)
            // TODO: FlowEventBus.post(ArticleSavedOrDeletedEvent(false, ...))
        }
    }

    @Query("DELETE FROM ReadingListPage WHERE status = :status")
    suspend fun purgePagesByStatus(status: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE)


    @Query(
        "SELECT * FROM ReadingListPage WHERE wiki = :wiki AND lang = :lang AND namespace = :ns AND apiTitle = :apiTitle AND listId = :listId LIMIT 1"
    )
    fun observePageByListIdAndTitle(wiki: WikiSite, lang: String, ns: Namespace, apiTitle: String, listId: Long): kotlinx.coroutines.flow.Flow<ReadingListPage?>

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
}
