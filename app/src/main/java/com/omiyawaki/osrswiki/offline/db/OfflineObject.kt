package com.omiyawaki.osrswiki.offline.db // Corrected package

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class OfflineObject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val lang: String,
    val path: String,
    var status: Int = 0,
    var usedByStr: String = ""
) {
    val usedBy: List<Long> get() {
        return usedByStr.split('|').filter { it.isNotEmpty() }.mapNotNull {
            try {
                it.toLong()
            } catch (e: NumberFormatException) {
                null
            }
        }
    }

    fun addUsedBy(pageId: Long) {
        val set = usedBy.toMutableSet()
        set.add(pageId)
        updateUsedBy(set)
    }

    fun removeUsedBy(pageId: Long) {
        val set = usedBy.toMutableSet()
        set.remove(pageId)
        updateUsedBy(set)
    }

    private fun updateUsedBy(set: Set<Long>) {
        usedByStr = if (set.isEmpty()) "" else "|${set.joinToString("|")}|"
    }
}
