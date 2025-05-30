package com.omiyawaki.osrswiki.offline.db // Corrected package

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.omiyawaki.osrswiki.page.PageTitle // Corrected import
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao // Corrected import for parameter type
import java.io.File

@Dao
interface OfflineObjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOfflineObject(obj: OfflineObject)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateOfflineObject(obj: OfflineObject)

    @Query("SELECT * FROM OfflineObject WHERE url = :url AND lang = :lang LIMIT 1")
    fun getOfflineObject(url: String, lang: String): OfflineObject?

    @Query("SELECT * FROM OfflineObject WHERE url = :url LIMIT 1")
    fun getOfflineObjectByUrl(url: String): OfflineObject?

    @Query("SELECT * FROM OfflineObject WHERE usedByStr LIKE '%|' || :readingListPageId || '|%'")
    fun getObjectsUsedByPageId(readingListPageId: Long): List<OfflineObject>

    @Transaction
    suspend fun addObject(url: String, lang: String, path: String, originalPageTitle: PageTitle, readingListPageDao: ReadingListPageDao) {
        var obj = getOfflineObject(url, lang)
        var isNewObject = false
        var wasModified = false

        if (obj == null) {
            obj = OfflineObject(url = url, lang = lang, path = path, status = 0)
            isNewObject = true
        }

        // Find the ReadingListPage entry to link this offline object to.
        // This assumes originalPageTitle uniquely identifies a saved ReadingListPage entry or we handle the first one.
        val associatedPage = readingListPageDao.findPageInAnyList(
            originalPageTitle.wikiSite,
            originalPageTitle.wikiSite.languageCode,
            originalPageTitle.namespace(),
            originalPageTitle.prefixedText
        )

        associatedPage?.let { page ->
            if (!obj.usedBy.contains(page.id)) {
                obj.addUsedBy(page.id)
                wasModified = true
            }
        }

        if (isNewObject) {
            insertOfflineObject(obj)
        } else if (wasModified) {
            updateOfflineObject(obj)
        }
    }

    @Transaction
    fun deleteObjectsForPageIds(readingListPageIds: List<Long>) {
        readingListPageIds.forEach { pageId ->
            val objectsUsedByThisPage = getObjectsUsedByPageId(pageId)
            objectsUsedByThisPage.forEach { obj ->
                obj.removeUsedBy(pageId)
                if (obj.usedBy.isEmpty()) {
                    deleteFilesForObject(obj)
                    deleteOfflineObjectQuery(obj.id)
                } else {
                    updateOfflineObject(obj)
                }
            }
        }
    }

    @Query("DELETE FROM OfflineObject WHERE id = :id")
    fun deleteOfflineObjectQuery(id: Int)

    fun deleteFilesForObject(obj: OfflineObject) {
        try {
            val metadataFile = File(obj.path + ".0")
            val contentsFile = File(obj.path + ".1")
            if (metadataFile.exists()) metadataFile.delete()
            if (contentsFile.exists()) contentsFile.delete()
        } catch (e: Exception) {
            // Consider logging this exception, e.g., L.e("Error deleting offline files for ${obj.path}", e)
        }
    }

    fun getTotalBytesForPageId(readingListPageId: Long): Long {
        var totalBytes: Long = 0
        try {
            val objects = getObjectsUsedByPageId(readingListPageId)
            totalBytes = objects.sumOf { File(it.path + ".1").length() }
        } catch (e: Exception) {
            // Consider logging
        }
        return totalBytes
    }
}
