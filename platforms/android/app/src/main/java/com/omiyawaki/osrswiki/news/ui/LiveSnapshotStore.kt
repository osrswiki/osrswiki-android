package com.omiyawaki.osrswiki.news.ui

import com.omiyawaki.osrswiki.news.ui.FeedItem

/**
 * Expert-recommended live snapshot store for theme preview content.
 * Updated by Home ViewModel after each successful render/diff to keep theme previews current.
 * This ensures previews always show the current actual state of the home page.
 */
object LiveSnapshotStore {
    
    /**
     * Current items displayed on the home screen.
     * Updated by Home VM after submitList() commit to maintain zero-maintenance dynamic content.
     */
    @Volatile 
    var items: List<FeedItem> = emptyList()
        private set
    
    /**
     * Updates the current snapshot with fresh feed items.
     * Called by Home ViewModel after successful list submission.
     */
    fun updateSnapshot(newItems: List<FeedItem>) {
        items = newItems
    }
    
    /**
     * Gets the current snapshot items, limited to specified count.
     * Used by theme preview system to show authentic current content.
     */
    fun getCurrentItems(limit: Int = 3): List<FeedItem> {
        return items.take(limit)
    }
}