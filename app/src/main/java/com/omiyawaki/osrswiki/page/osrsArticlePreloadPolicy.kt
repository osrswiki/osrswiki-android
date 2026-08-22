package com.omiyawaki.osrswiki.page

/**
 * Speculative live article preloads are a product non-requirement.
 * Shared process/HTML caches for an already-requested navigation may stay.
 * Guessed-destination WebViews must not be kept warm.
 */
internal object osrsArticlePreloadPolicy {
    const val speculativeLiveArticlePreloadsEnabled = false
}
