package com.omiyawaki.osrswiki.history

import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.StringUtil

/**
 * Fills missing search-history snippets and thumbnails from wiki preview metadata.
 * Visit-time logging should already store this, but older rows and deep-link visits
 * can omit it; the history list then backfills any remaining gaps.
 */
object HistoryMetadataBackfill {
    fun needsEnrichment(entry: HistoryEntry): Boolean {
        return entry.snippet.isNullOrBlank() || entry.thumbnailUrl.isNullOrBlank()
    }

    fun previewTitle(entry: HistoryEntry): String {
        val fromPath = entry.apiPath.replace('_', ' ').trim()
        if (fromPath.isNotEmpty() && !fromPath.contains('<') && !fromPath.contains("mw-page-title-main")) {
            return fromPath
        }
        return StringUtil.extractMainTitle(entry.displayText).trim()
    }

    fun matchKey(title: String): String {
        val cleaned = if (title.contains('<') || title.contains("mw-page-title-main")) {
            StringUtil.extractMainTitle(title)
        } else {
            title
        }
        return cleaned.replace('_', ' ').trim().lowercase()
    }

    fun apply(entry: HistoryEntry, extract: String?, thumbnailUrl: String?): Boolean {
        val updated = filledCopy(entry, extract, thumbnailUrl) ?: return false
        entry.snippet = updated.snippet
        entry.thumbnailUrl = updated.thumbnailUrl
        return true
    }

    fun filledCopy(entry: HistoryEntry, extract: String?, thumbnailUrl: String?): HistoryEntry? {
        var snippet = entry.snippet
        var thumb = entry.thumbnailUrl
        val cleanExtract = extract?.trim().orEmpty()
        val cleanThumb = thumbnailUrl?.trim().orEmpty()
        if (snippet.isNullOrBlank() && cleanExtract.isNotEmpty()) snippet = cleanExtract
        if (thumb.isNullOrBlank() && cleanThumb.isNotEmpty()) thumb = cleanThumb
        if (snippet == entry.snippet && thumb == entry.thumbnailUrl) return null
        return entry.copy(snippet = snippet, thumbnailUrl = thumb)
    }
}
