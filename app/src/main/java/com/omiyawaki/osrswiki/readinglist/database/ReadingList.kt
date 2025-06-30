package com.omiyawaki.osrswiki.readinglist.database // Corrected package

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
data class ReadingList(
    var title: String,
    var description: String? = null,
    var mtime: Long = System.currentTimeMillis(),
    var atime: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    var isDefault: Boolean = false
) : Serializable {

    @Ignore
    val pages = mutableListOf<com.omiyawaki.osrswiki.readinglist.database.ReadingListPage>() // Corrected import if needed, or just ReadingListPage if in same package

    @Ignore
    val FAKE_ID_FOR_DEFAULT_LIST: Long = -1
    @Ignore
    val FAKE_ID_FOR_ALL_SAVED_PAGES: Long = -2

    val isFakeList: Boolean
        @Ignore get() = id < 0
}
