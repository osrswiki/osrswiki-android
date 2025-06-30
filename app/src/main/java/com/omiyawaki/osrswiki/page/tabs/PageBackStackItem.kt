package com.omiyawaki.osrswiki.page.tabs

import com.omiyawaki.osrswiki.common.models.PageTitle
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import kotlinx.serialization.Serializable

/**
 * Represents an item in a tab's navigation backstack.
 * It holds the page title, the specific history entry created when this page was first
 * visited in this stack sequence, and the scroll position.
 *
 * @property pageTitle The [PageTitle] of the page.
 * @property historyEntry The [HistoryEntry] associated with this specific visit.
 * This connects the tab's backstack item to the global history log.
 * @property scrollY The vertical scroll position of the page when the user navigated away.
 */
@Serializable
data class PageBackStackItem(
    var pageTitle: PageTitle,
    var historyEntry: HistoryEntry, // The HistoryEntry created when this page was loaded
    var scrollY: Int = 0
)
