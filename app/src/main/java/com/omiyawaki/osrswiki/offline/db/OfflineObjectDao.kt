package com.omiyawaki.osrswiki.offline.db

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.offline.db.OfflineObject
import java.io.File

@Dao
interface OfflineObjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOfflineObject(obj: OfflineObject): Long // Changed to return Long

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateOfflineObject(obj: OfflineObject)

    @Query("SELECT * FROM offline_objects WHERE url = :url AND lang = :lang LIMIT 1")
    fun getOfflineObject(url: String, lang: String): OfflineObject?

    @Query("SELECT * FROM offline_objects WHERE url = :url LIMIT 1")
    fun getOfflineObjectByUrl(url: String): OfflineObject?

    @Query("SELECT * FROM offline_objects WHERE usedByStr LIKE '%|' || :readingListPageId || '|%'")
    fun getObjectsUsedByPageId(readingListPageId: Long): List<OfflineObject>

    @Transaction
    suspend fun addObject(url: String, lang: String, path: String, originalPageTitle: PageTitle, readingListPageDao: ReadingListPageDao) {
        var currentObj: OfflineObject? = getOfflineObject(url, lang)
        val isNewObject = currentObj == null
        var wasModified = false

        if (isNewObject) {
            currentObj = OfflineObject(
                url = url,
                lang = lang,
                path = path,
                status = 0, // e.g., OfflineObject.STATUS_QUEUE_FOR_SAVE
                usedByStr = "", 
                saveType = OfflineObject.SAVE_TYPE_READING_LIST 
            )
        }

        var objectToPersist: OfflineObject = currentObj!! 

        val associatedPage = readingListPageDao.findPageInAnyList(
            originalPageTitle.wikiSite,
            originalPageTitle.wikiSite.languageCode,
            originalPageTitle.namespace(),
            originalPageTitle.prefixedText
        )

        associatedPage?.let { page ->
            val pageIdStrSegment = "|${page.id}|" 
            if (!objectToPersist.usedByStr.contains(pageIdStrSegment)) {
                val newUsedByStr = if (objectToPersist.usedByStr.isEmpty()) {
                    pageIdStrSegment
                } else if (objectToPersist.usedByStr.endsWith("|")) {
                    objectToPersist.usedByStr + "${page.id}|"
                } else {
                    objectToPersist.usedByStr + pageIdStrSegment
                }
                objectToPersist = objectToPersist.copy(usedByStr = newUsedByStr)
                wasModified = true
            }
        }

        if (isNewObject) {
            insertOfflineObject(objectToPersist)
        } else if (wasModified) {
            updateOfflineObject(objectToPersist)
        }
    }

    @Transaction
    fun deleteObjectsForPageIds(readingListPageIds: List<Long>, context: Context) { 
        readingListPageIds.forEach { pageId ->
            val objectsUsedByThisPage = getObjectsUsedByPageId(pageId)
            objectsUsedByThisPage.forEach { currentObj -> 
                val pageIdStrSegment = "|${pageId}|"
                var updatedUsedByStr = currentObj.usedByStr.replace(pageIdStrSegment, "|")
                
                updatedUsedByStr = updatedUsedByStr.replace("||", "|")
                if (updatedUsedByStr == "|") {
                    updatedUsedByStr = ""
                }
                if (updatedUsedByStr.startsWith("|")) {
                     updatedUsedByStr = updatedUsedByStr.removePrefix("|")
                }
                if (updatedUsedByStr.endsWith("|")) {
                    updatedUsedByStr = updatedUsedByStr.removeSuffix("|")
                }

                if (updatedUsedByStr.isEmpty()) {
                    deleteFilesForObject(currentObj, context) 
                    deleteOfflineObjectQuery(currentObj.id) 
                } else {
                    updateOfflineObject(currentObj.copy(usedByStr = updatedUsedByStr))
                }
            }
        }
    }

    @Query("DELETE FROM offline_objects WHERE id = :id") 
    fun deleteOfflineObjectQuery(id: Long)

    fun deleteFilesForObject(obj: OfflineObject, context: Context) { 
        try {
            val baseDir: File? = when (obj.saveType) {
                OfflineObject.SAVE_TYPE_READING_LIST -> File(context.filesDir, "offline_pages_rl")
                OfflineObject.SAVE_TYPE_FULL_ARCHIVE -> {
                    val externalCacheSubDir = context.getExternalFilesDir("wiki_archive")
                    externalCacheSubDir?.let { File(it, "content") }
                }
                else -> {
                    Log.e("OfflineObjectDao", "Unknown saveType for file deletion: ${obj.saveType}")
                    null
                }
            }

            if (baseDir != null) {
                if (!baseDir.exists()) baseDir.mkdirs() 
                val metadataFile = File(baseDir, obj.path + ".0")
                val contentsFile = File(baseDir, obj.path + ".1")
                if (metadataFile.exists()) metadataFile.delete()
                if (contentsFile.exists()) contentsFile.delete()
                Log.d("OfflineObjectDao", "Deleted files for ${obj.path} in ${baseDir.absolutePath}")
            } else {
                Log.e("OfflineObjectDao", "Base directory not found for saveType ${obj.saveType}, cannot delete files for ${obj.path}")
            }

        } catch (e: Exception) {
            Log.e("OfflineObjectDao", "Error deleting offline files for ${obj.path}", e)
        }
    }

    fun getTotalBytesForPageId(readingListPageId: Long, context: Context): Long { 
        var totalBytes: Long = 0
        try {
            val objects = getObjectsUsedByPageId(readingListPageId)
            totalBytes = objects.sumOf { obj ->
                val baseDir: File? = when (obj.saveType) {
                    OfflineObject.SAVE_TYPE_READING_LIST -> File(context.filesDir, "offline_pages_rl")
                    OfflineObject.SAVE_TYPE_FULL_ARCHIVE -> {
                         val externalCacheSubDir = context.getExternalFilesDir("wiki_archive")
                         externalCacheSubDir?.let { File(it, "content") }
                    }
                    else -> null
                }
                if (baseDir != null && baseDir.exists()) { 
                    File(baseDir, obj.path + ".1").takeIf { it.exists() }?.length() ?: 0L
                } else {
                    0L
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineObjectDao", "Error getting total bytes for page ID $readingListPageId", e)
        }
        return totalBytes
    }

    @Query("SELECT * FROM offline_objects WHERE url = :url AND lang = :lang AND saveType = :saveType LIMIT 1")
    fun findByUrlAndLangAndSaveType(url: String, lang: String, saveType: String): OfflineObject?
}
