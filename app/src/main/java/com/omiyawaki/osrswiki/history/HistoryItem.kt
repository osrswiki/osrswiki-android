package com.omiyawaki.osrswiki.history

import com.omiyawaki.osrswiki.history.db.HistoryEntry

sealed class HistoryItem {
    data class DateHeader(val dateString: String) : HistoryItem()
    data class EntryItem(val historyEntry: HistoryEntry) : HistoryItem()
}