package com.omiyawaki.osrswiki.common.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents the identifying information for a Wiki page.
 *
 * @property wikiUrl The canonical URL of the wiki page. Used as a primary identifier.
 * @property displayText The title of the page suitable for display (e.g., in UI elements).
 * @property pageId The unique integer ID of the page from the wiki API, if available.
 * @property apiPath The path segment used for API calls (e.g., "Rune_armour_set_(lg)").
 * This is often the same as the last path segment of wikiUrl, but URL encoded.
 */
@Serializable
@Parcelize
data class PageTitle(
    val wikiUrl: String,
    val displayText: String,
    val pageId: Int? = null, // Optional: From wiki API if available
    val apiPath: String      // Path used for API lookups, typically URL-encoded title
) : Parcelable {
    // Future consideration: Could add methods to extract apiPath from wikiUrl or vice-versa if needed.
    // For now, assume apiPath is provided or derived correctly when a PageTitle is created.

    /**
     * Checks if the core identifying information (URL and API path) is empty.
     */
    fun isEmpty(): Boolean {
        return wikiUrl.isEmpty() && apiPath.isEmpty()
    }
}
