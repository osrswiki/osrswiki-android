package com.omiyawaki.osrswiki.settings.preview

import com.omiyawaki.osrswiki.news.ui.FeedItem

/**
 * Fallback content source that reads recent items from DB/cache (no network).
 * Used when live snapshot is empty, typically during cold starts.
 * TODO: Implement repository access when available.
 */
class RepositoryFallbackSource : PreviewContentSource {
    
    override suspend fun getPreviewItems(limit: Int): List<FeedItem> {
        // TODO: Implement repo.latest(limit) that reads from cache only, no network
        // For now, return empty list until repository method is available
        return emptyList()
    }
}