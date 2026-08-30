package com.omiyawaki.osrswiki.search

import android.content.Intent
import com.omiyawaki.osrswiki.R

/**
 * Reusable Search destination filter. Home "View more" is one call site:
 * Update: namespace (112), reverse-chronological when the query is empty
 * (`osrsUpdatesBrowseOrder`: generator index, then timestamp, then pageid).
 */
data class osrsSearchScope(
    val namespace: Int? = null,
    val emptyQueryBrowsesNewest: Boolean = false, // reverse-chronological via osrsUpdatesBrowseOrder
    val hintResId: Int = R.string.search_hint_wiki
) {
    val restrictsNamespace: Boolean get() = namespace != null

    fun putExtras(intent: Intent) {
        namespace?.let { intent.putExtra(EXTRA_NAMESPACE, it) }
        intent.putExtra(EXTRA_BROWSE_NEWEST, emptyQueryBrowsesNewest)
        intent.putExtra(EXTRA_HINT, hintResId)
    }

    companion object {
        const val EXTRA_NAMESPACE = "osrs_search_namespace"
        const val EXTRA_BROWSE_NEWEST = "osrs_search_browse_newest"
        const val EXTRA_HINT = "osrs_search_hint"

        val ALL = osrsSearchScope()
        val UPDATES = osrsSearchScope(
            namespace = osrsMediaWikiNamespace.UPDATES,
            emptyQueryBrowsesNewest = true,
            hintResId = R.string.search_updates_hint
        )

        fun fromIntent(intent: Intent?): osrsSearchScope {
            if (intent == null) return ALL
            val ns = if (intent.hasExtra(EXTRA_NAMESPACE)) {
                intent.getIntExtra(EXTRA_NAMESPACE, Int.MIN_VALUE)
            } else {
                Int.MIN_VALUE
            }
            return osrsSearchScope(
                namespace = ns.takeIf { it != Int.MIN_VALUE },
                emptyQueryBrowsesNewest = intent.getBooleanExtra(EXTRA_BROWSE_NEWEST, false),
                hintResId = intent.getIntExtra(EXTRA_HINT, R.string.search_hint_wiki)
            )
        }
    }
}

object osrsMediaWikiNamespace {
    const val MAIN = 0
    const val UPDATES = 112
    const val CALCULATOR = 116
    /** Default Home search: main articles plus user-facing Calculator: pages. */
    const val DEFAULT_SEARCH = "$MAIN|$CALCULATOR"
}
