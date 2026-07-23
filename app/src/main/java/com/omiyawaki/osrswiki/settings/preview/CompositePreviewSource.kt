package com.omiyawaki.osrswiki.settings.preview

import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.news.ui.FeedItem
/**
 * Expert-recommended composite preview source with smart fallback strategy.
 * Provides robust content sourcing: live snapshot → repository cache → minimal placeholders.
 */
class CompositePreviewSource(
    private val live: LiveSnapshotSource = LiveSnapshotSource(),
    private val repo: RepositoryFallbackSource = RepositoryFallbackSource()
) : PreviewContentSource {
    
    override suspend fun getPreviewItems(limit: Int): List<FeedItem> {
        // Priority 1: Live snapshot from currently displayed home screen
        val liveItems = live.getPreviewItems(limit)
        if (liveItems.isNotEmpty()) {
            return liveItems
        }
        
        // Priority 2: Repository cache (no network calls)
        val cachedItems = repo.getPreviewItems(limit)
        if (cachedItems.isNotEmpty()) {
            return cachedItems
        }
        
        // Priority 3: Last resort minimal placeholders for layout testing
        return createPlaceholderItems(limit)
    }
    
    /**
     * Creates minimal placeholder items as absolute last resort.
     * These ensure theme previews can still demonstrate layout and theming.
     */
    private fun createPlaceholderItems(limit: Int): List<FeedItem> {
        val placeholderUpdates = listOf(
            UpdateItem(
                title = "Recent Update",
                snippet = "Latest improvements and fixes to enhance your experience...",
                imageUrl = "", // Will use placeholder drawable
                articleUrl = ""
            ),
            UpdateItem(
                title = "Game Update",
                snippet = "New content and balance changes now available...",
                imageUrl = "", // Will use placeholder drawable  
                articleUrl = ""
            )
        )
        
        val placeholderAnnouncement = AnnouncementItem(
            date = "Today",
            content = "Wiki announcement with important information for users..."
        )
        
        val placeholderOnThisDay = OnThisDayItem(
            title = "On this day...",
            events = listOf(
                "• Historic game update released",
                "• Community milestone reached",  
                "• Major content expansion launched"
            )
        )
        
        val allItems = listOf(
            FeedItem.Updates(placeholderUpdates),
            FeedItem.Announcement(placeholderAnnouncement),
            FeedItem.OnThisDay(placeholderOnThisDay)
        )
        
        return allItems.take(limit)
    }
}