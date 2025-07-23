package com.omiyawaki.osrswiki.search.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single recent search query made by the user.
 *
 * @param query The text of the search query. This is used as the primary key.
 * @param timestamp The time when the search was performed, used for ordering.
 */
@Entity(tableName = "recent_searches")
data class RecentSearch(
    @PrimaryKey
    val query: String,
    val timestamp: Long
)
