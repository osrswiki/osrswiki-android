package com.omiyawaki.osrswiki.offline.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_objects",
    indices = [Index(value = ["url", "lang"], unique = true)]
)
data class OfflineObject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val url: String,
    val lang: String, // e.g., "en"
    val path: String, // Relative path to the file on disk (e.g., hashed_filename)
    var status: Int,  // e.g., STATUS_SAVED, STATUS_ERROR
    val usedByStr: String, // Comma-separated string of ReadingListPage IDs or empty
    val saveType: String // Type of save, e.g., READING_LIST, FULL_ARCHIVE
) {
    companion object {
        // Constants for saveType
        const val SAVE_TYPE_READING_LIST = "READING_LIST"
        const val SAVE_TYPE_FULL_ARCHIVE = "FULL_ARCHIVE"
        const val SAVE_TYPE_UNKNOWN = "UNKNOWN" // Default for migration if needed

        // Example status constants (define as needed)
        const val STATUS_QUEUE_FOR_SAVE = 0
        const val STATUS_SAVED = 1
        const val STATUS_ERROR = 2
        // Add other statuses as required
    }
}
