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
    @PrimaryKey
    @ColumnInfo(name = "page_wikiUrl")
    var wikiUrl: String,

    @ColumnInfo(name = "page_displayText")
    var displayText: String,

    @ColumnInfo(name = "page_pageId")
    var pageId: Int? = null,

    @ColumnInfo(name = "page_apiPath")
    var apiPath: String,

    @ColumnInfo(name = "timestamp")
    @Serializable(with = DateAsLongSerializer::class) // Apply the custom serializer
    var timestamp: Date = Date(),

    @ColumnInfo(name = "source")
    var source: Int,

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    var isArchived: Boolean = false,

    @ColumnInfo(name = "snippet")
    var snippet: String? = null,

    @ColumnInfo(name = "thumbnail_url")
    var thumbnailUrl: String? = null
) {
    constructor(pageTitle: PageTitle, source: Int) : this(
        wikiUrl = pageTitle.wikiUrl,
        displayText = pageTitle.displayText,
        pageId = pageTitle.pageId,
        apiPath = pageTitle.apiPath,
        source = source,
        timestamp = Date(),
        snippet = null,
        thumbnailUrl = null
    )

    // Convenience property to create a PageTitle from the embedded fields
    val pageTitle: PageTitle
        get() = PageTitle(
            wikiUrl = wikiUrl,
            displayText = displayText,
            pageId = pageId,
            apiPath = apiPath
        )

    companion object {
        const val SOURCE_SEARCH = 1
        const val SOURCE_INTERNAL_LINK = 2
        const val SOURCE_EXTERNAL_LINK = 3
        const val SOURCE_HISTORY = 4
        const val SOURCE_SAVED_PAGE = 5
        const val SOURCE_MAIN_PAGE = 6
        const val SOURCE_RANDOM = 7
        const val SOURCE_NEWS = 8 // The new, correct constant
    }
}
