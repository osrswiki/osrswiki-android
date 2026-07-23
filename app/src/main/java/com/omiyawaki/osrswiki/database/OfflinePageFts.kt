package com.omiyawaki.osrswiki.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "offline_page_fts")
@Fts4 // Using FTS4
data class OfflinePageFts(
    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String
)