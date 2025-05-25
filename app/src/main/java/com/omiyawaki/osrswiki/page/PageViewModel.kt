package com.omiyawaki.osrswiki.page

/**
 * A simple data holder for page-related information, similar to the POKO style
 * ViewModel found in the Wikipedia app. This class does not extend androidx.lifecycle.ViewModel.
 * Its properties are expected to be populated and managed externally.
 */
class PageViewModel {
    var pageId: Int? = null
    var articleTitle: String? = null
    var htmlContent: String? = null
    var imageUrl: String? = null
    var isLoading: Boolean = false
    var errorMessage: String? = null
    var forceNetwork: Boolean = false

    // TODO: Add any computed properties if necessary, e.g., for cache control strings
    // based on `forceNetwork`, or other derived states.

    // TODO: Consider if specific data classes are needed for complex properties,
    // or if simple types are sufficient for now.
}
