package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.data.network.NetworkConstants

/**
 * A simple data holder for page-related information, similar to the POKO style
 * ViewModel found in the Wikipedia app. This class does not extend androidx.lifecycle.ViewModel.
 * Its properties are expected to be populated and managed externally.
 */
class PageViewModel {
    var pageId: Int? = null // As seen in your last successful output
    var articleTitle: String? = null
    var htmlContent: String? = null
    var imageUrl: String? = null
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
