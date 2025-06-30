package com.omiyawaki.osrswiki.history.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.omiyawaki.osrswiki.common.models.PageTitle
import com.omiyawaki.osrswiki.common.serialization.DateAsLongSerializer // Import the custom serializer
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable 
@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @Embedded(prefix = "page_")
    var pageTitle: PageTitle, 

    @ColumnInfo(name = "timestamp")
    @Serializable(with = DateAsLongSerializer::class) // Apply the custom serializer
    var timestamp: Date = Date(),

    @ColumnInfo(name = "source")
    var source: Int,

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    var isArchived: Boolean = false
) {
    constructor(pageTitle: PageTitle, source: Int) : this(
        pageTitle = pageTitle,
        source = source,
        timestamp = Date()
    )

    companion object {
        const val SOURCE_SEARCH = 1
        const val SOURCE_INTERNAL_LINK = 2
        const val SOURCE_EXTERNAL_LINK = 3
        const val SOURCE_HISTORY = 4
        const val SOURCE_SAVED_PAGE = 5
        const val SOURCE_MAIN_PAGE = 6
        const val SOURCE_RANDOM = 7
    }
}
