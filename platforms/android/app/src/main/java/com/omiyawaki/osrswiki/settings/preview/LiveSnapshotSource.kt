package com.omiyawaki.osrswiki.settings.preview

import com.omiyawaki.osrswiki.news.ui.FeedItem
import com.omiyawaki.osrswiki.news.ui.LiveSnapshotStore

/**
 * Preferred content source that reads currently shown items from live Home screen snapshot.
 * Provides authentic "what you see is what you get" theme previews.
 */
class LiveSnapshotSource : PreviewContentSource {
    
    override suspend fun getPreviewItems(limit: Int): List<FeedItem> {
        return LiveSnapshotStore.getCurrentItems(limit)
    }
}