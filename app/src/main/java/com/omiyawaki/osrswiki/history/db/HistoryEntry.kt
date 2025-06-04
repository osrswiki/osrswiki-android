package com.omiyawaki.osrswiki.history.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.omiyawaki.osrswiki.common.models.PageTitle // Ensure this import is correct
import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Represents an entry in the user's page visit history.
 * This is a Room entity.
 *
 * @property id The unique local database ID for this history entry.
 * @property pageTitle The [PageTitle] associated with this history event. Its fields will be
 * embedded into this table by Room. The PageTitle's wikiUrl can be used
 * as a defacto unique key for a page, though history entries are unique by timestamp.
 * @property timestamp The date and time when this page was visited.
 * @property source An integer code indicating how the page was accessed (e.g., search, internal link).
 * @property isArchived Indicates if the page is part of an archived session (future use for Wikipedia parity).
 */
@Serializable // Potentially useful, though primarily a Room entity
@Entity(tableName = "history_entries") // Updated table name
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0,

    @Embedded(prefix = "page_") // Prefixes ensure field names from PageTitle don't clash
    var pageTitle: PageTitle,   // Updated type name

    @ColumnInfo(name = "timestamp")
    var timestamp: Date = Date(), // Defaults to current time on creation

    @ColumnInfo(name = "source")
    var source: Int,

    @ColumnInfo(name = "is_archived", defaultValue = "0") // For future parity with Wikipedia
    var isArchived: Boolean = false
) {
    constructor(pageTitle: PageTitle, source: Int) : this( // Updated type name
        pageTitle = pageTitle,
        source = source,
        timestamp = Date() // Ensure timestamp is set
        // id, isArchived will use defaults
    )

    // Define source constants, similar to Wikipedia's HistoryEntry.SOURCE_*
    companion object {
        const val SOURCE_SEARCH = 1
        const val SOURCE_INTERNAL_LINK = 2
        const val SOURCE_EXTERNAL_LINK = 3 // e.g. from a deep link
        const val SOURCE_HISTORY = 4       // Navigated from the history list itself
        const val SOURCE_SAVED_PAGE = 5    // Navigated from saved pages list
        const val SOURCE_MAIN_PAGE = 6     // Navigated from a pre-defined main page link
        const val SOURCE_RANDOM = 7        // If a "random page" feature is added
        // Add other sources as needed
    }
}
