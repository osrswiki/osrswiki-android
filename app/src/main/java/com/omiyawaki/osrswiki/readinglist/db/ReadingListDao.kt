package com.omiyawaki.osrswiki.readinglist.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.omiyawaki.osrswiki.readinglist.database.ReadingList
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage // Needed if populating pages within a transaction

@Dao
interface ReadingListDao {

    @Query("SELECT * FROM ReadingList ORDER BY mtime DESC")
    fun getAllLists(): List<ReadingList>

    @Query("SELECT * FROM ReadingList WHERE isDefault = 1 LIMIT 1")
    fun getDefaultList(): ReadingList?

    @Query("SELECT * FROM ReadingList WHERE id = :id LIMIT 1")
    fun getListById(id: Long): ReadingList?

    @Query("SELECT * FROM ReadingList WHERE title = :title LIMIT 1")
    fun getListByTitle(title: String): ReadingList?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertList(list: ReadingList): Long

    @Update
    fun updateList(list: ReadingList)

    @Delete
    fun deleteList(list: ReadingList)

    @Query("DELETE FROM ReadingList WHERE id = :listId AND isDefault = 0") // Prevent deleting default list this way
    fun deleteListById(listId: Long)

    @Transaction
    fun createList(title: String, description: String?): ReadingList {
        val newList = ReadingList(title = title, description = description)
        newList.id = insertList(newList)
        return newList
    }

    @Transaction
    fun createDefaultListIfNotExist(): ReadingList {
        var defaultList = getDefaultList()
        if (defaultList == null) {
            // TODO: Use a localized string for the default list title e.g. from R.string.default_reading_list_name
            defaultList = ReadingList(title = "Saved pages", description = "Default list for saved pages", isDefault = true)
            defaultList.id = insertList(defaultList)
            if (defaultList.id == -1L) { // Insert failed or conflict occurred, try to refetch
                defaultList = getListByTitle("Saved pages") ?: throw IllegalStateException("Failed to create or retrieve default list.")
            }
        }
        return defaultList
    }

    // Example of a method to get lists and their pages (if you choose to populate them this way)
    // This requires ReadingListPageDao to be accessible or methods to join data.
    // For now, populating ReadingList.pages is typically done in a repository or use case.
    /*
    @Transaction
    @Query("SELECT * FROM ReadingList WHERE id = :listId LIMIT 1")
    fun getListWithPages(listId: Long, pageDao: ReadingListPageDao): ReadingList? {
        val list = getListById(listId)
        list?.pages?.addAll(pageDao.getPagesByListId(listId))
        return list
    }
    */
}
