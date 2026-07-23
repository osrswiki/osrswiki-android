package com.omiyawaki.osrswiki.readinglist.ui

import android.content.Context
import com.omiyawaki.osrswiki.R

object SavedPagesSearchEmptyStateText {
    fun messageFor(
        context: Context,
        query: String,
        resultCount: Int
    ): String? {
        val trimmedQuery = query.trim()
        return when {
            trimmedQuery.isEmpty() -> context.getString(R.string.search_hint_saved_pages)
            resultCount == 0 -> context.getString(R.string.saved_pages_search_no_results_for_query, trimmedQuery)
            else -> null
        }
    }
}
