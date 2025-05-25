package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.data.network.NetworkConstants
// No explicit import for ArticleDisplayData needed if it's in the same package.

/**
 * A simple data holder for page-related information, similar to the POKO style
 * ViewModel found in the Wikipedia app. This class does not extend androidx.lifecycle.ViewModel.
 * Its properties are expected to be populated and managed externally.
 */
class PageViewModel {
    // Encapsulates core article details.
    var articleData: ArticleDisplayData? = null

    var isLoading: Boolean = false
    var errorMessage: String? = null
    var forceNetwork: Boolean = false

    val cacheControl: String
        get() = if (forceNetwork) {
            NetworkConstants.CACHE_CONTROL_FORCE_NETWORK_VALUE
        } else {
            NetworkConstants.CACHE_CONTROL_DEFAULT_VALUE
        }

    // Backing property for the saved state
    var isCurrentlyMarkedAsSaved: Boolean = false

    // Computed property
    val isSavedArticle: Boolean
        get() = isCurrentlyMarkedAsSaved
}
