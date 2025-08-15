package com.omiyawaki.osrswiki.settings.preview

import com.omiyawaki.osrswiki.news.ui.FeedItem

/**
 * Expert-recommended abstraction for dynamic theme preview content.
 * Enables zero-maintenance content that reflects current home page state.
 */
interface PreviewContentSource {
    
    /**
     * Gets feed items for theme preview display.
     * @param limit Maximum number of items to return (default 3 for compact previews)
     * @return List of current feed items for authentic preview content
     */
    suspend fun getPreviewItems(limit: Int = 3): List<FeedItem>
}