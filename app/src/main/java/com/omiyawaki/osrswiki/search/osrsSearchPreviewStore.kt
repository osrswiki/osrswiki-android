package com.omiyawaki.osrswiki.search

import com.omiyawaki.osrswiki.network.SearchResult
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

/**
 * In-place snippet overlay so the updates list can paint titles immediately
 * and bind settled previews without waiting for extract/parse on first load.
 */
class osrsSearchPreviewStore {
    private val snippets = ConcurrentHashMap<Int, String>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun snippetFor(pageId: Int): String? = snippets[pageId]

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun merge(results: List<SearchResult>) {
        var changed = false
        for (result in results) {
            val preview = osrsSearchPreviewText.fromCandidates(result.snippet, result.extract)
            if (!preview.isNullOrBlank() && snippets[result.pageid] != preview) {
                snippets[result.pageid] = preview
                changed = true
            }
        }
        if (changed) {
            listeners.forEach { it.invoke() }
        }
    }
}
