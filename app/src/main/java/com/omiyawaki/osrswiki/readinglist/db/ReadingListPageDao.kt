package com.omiyawaki.osrswiki.readinglist.db 

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.omiyawaki.osrswiki.dataclient.WikiSite 
import com.omiyawaki.osrswiki.page.Namespace   
import com.omiyawaki.osrswiki.page.PageTitle   
import com.omiyawaki.osrswiki.readinglist.database.ReadingList 
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage 
// TODO: Uncomment and implement/adapt these dependencies later
// import com.omiyawaki.osrswiki.concurrency.FlowEventBus
// import com.omiyawaki.osrswiki.events.ArticleSavedOrDeletedEvent
// import com.omiyawaki.osrswiki.readinglist.sync.ReadingListSyncAdapter
// import com.omiyawaki.osrswiki.savedpages.SavedPageSyncService

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
                    }
                }
                insertReadingListPage(newPage)
                addedDisplayTitles.add(title.displayText)
            }
        }
        if (addedDisplayTitles.isNotEmpty()) {
            // TODO: SavedPageSyncService.enqueue()
            // TODO: FlowEventBus.post(ArticleSavedOrDeletedEvent(true, ...))
        }
        return addedDisplayTitles
    }

    @Transaction
    fun markPagesForOffline(pages: List<ReadingListPage>, offline: Boolean, forcedSave: Boolean) {
        pages.forEach { page ->
            if (page.offline == offline && !forcedSave) {
                return@forEach
            }
            page.offline = offline
            if (offline) {
                page.status = if (forcedSave) ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE else ReadingListPage.STATUS_QUEUE_FOR_SAVE
                page.downloadProgress = 0
            } else {
                page.status = ReadingListPage.STATUS_SAVED 
                page.sizeBytes = 0
                page.downloadProgress = 0
            }
            updateReadingListPage(page)
        }
        // TODO: SavedPageSyncService.enqueue()
    }

    @Transaction
    fun markPagesForDeletion(listId: Long, pages: List<ReadingListPage>) {
        pages.forEach { page ->
            if (page.listId == listId || listId == -1L) { 
                page.status = ReadingListPage.STATUS_QUEUE_FOR_DELETE
                updateReadingListPage(page)
            }
        }
        // TODO: SavedPageSyncService.enqueue()
        // TODO: FlowEventBus.post(ArticleSavedOrDeletedEvent(false, ...))
    }

    @Query("DELETE FROM ReadingListPage WHERE status = :status")
    fun purgePagesByStatus(status: Long = ReadingListPage.STATUS_QUEUE_FOR_DELETE)

    @Query("SELECT * FROM ReadingListPage WHERE status = :status AND offline = :offline")
    fun getPagesByStatusAndOffline(status: Long, offline: Boolean): List<ReadingListPage>

    @Query("SELECT * FROM ReadingListPage WHERE status = :status")
    fun getPagesByStatus(status: Long): List<ReadingListPage>

    @Query("UPDATE ReadingListPage SET status = :newStatus WHERE status = :oldStatus AND offline = :offline")
    fun updateStatusForOfflinePages(oldStatus: Long, newStatus: Long, offline: Boolean)

    // New method to update status and mtime for a specific page ID
    @Query("UPDATE ReadingListPage SET status = :newStatus, mtime = :currentTimeMs WHERE id = :pageId")
    fun updatePageStatusToSavedAndMtime(pageId: Long, newStatus: Long = ReadingListPage.STATUS_SAVED, currentTimeMs: Long)
}
