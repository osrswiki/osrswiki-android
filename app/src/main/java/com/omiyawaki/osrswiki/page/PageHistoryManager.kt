package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.common.models.PageTitle
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.tabs.PageBackStackItem
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class PageHistoryManager(
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val HISTORY_DEBUG_TAG = "PageHistoryManager"

    fun logPageVisit(snippet: String? = null, thumbnailUrl: String? = null) {
        coroutineScope.launch(Dispatchers.IO) {
            val fragment = fragmentContextProvider() ?: return@launch
            if (!fragment.isAdded || fragment.provideBinding() == null) {
                Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Fragment not in a valid state to log history.")
                return@launch
            }

            val currentOsrsApp = OSRSWikiApp.instance
            val currentTab = currentOsrsApp.currentTab
            val state = pageViewModel.uiState
            val navigationSource = fragment.getNavigationSource()

            if (state.isLoading || state.error != null || state.htmlContent == null || state.wikiUrl == null || state.plainTextTitle == null) {
                Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Page not fully loaded or essential data missing. Skipping history logging.")
                return@launch
            }

            // Reading scrollY must be done on the main thread.
            val scrollY = withContext(Dispatchers.Main) {
                fragment.provideBinding()!!.pageWebView.scrollY
            }

            if (currentTab != null && currentTab.backStack.isNotEmpty()) {
                val lastBackStackUrl = currentTab.backStack.last().pageTitle.wikiUrl
                if (lastBackStackUrl == state.wikiUrl) {
                    val lastBackStackItem = currentTab.backStack.last()
                    if (lastBackStackItem.scrollY != scrollY) {
                        // State mutation of the backstack should happen on the main thread.
                        withContext(Dispatchers.Main) {
                            lastBackStackItem.scrollY = scrollY
                            currentOsrsApp.commitTabState()
                        }
                    }
                    return@launch
                }
            }

            val commonPageTitleForHistory = PageTitle(
                wikiUrl = state.wikiUrl,
                displayText = state.title ?: state.plainTextTitle,
                pageId = state.pageId ?: -1,
                apiPath = state.plainTextTitle
            )

            val historyEntry = HistoryEntry(
                pageTitle = commonPageTitleForHistory,
                timestamp = Date(),
                source = navigationSource,
                snippet = snippet,
                thumbnailUrl = thumbnailUrl
            )

            try {
                AppDatabase.instance.historyEntryDao().upsertEntry(historyEntry)
                Log.d(HISTORY_DEBUG_TAG, "Global history upserted for: ${commonPageTitleForHistory.apiPath}")
            } catch (e: Exception) {
                L.e("$HISTORY_DEBUG_TAG: Error upserting history entry", e)
            }

            if (currentTab == null) {
                Log.e(HISTORY_DEBUG_TAG, "logPageVisit: Current tab is null. Cannot add to tab backstack.")
                return@launch
            }

            val pageBackStackItem = PageBackStackItem(
                pageTitle = commonPageTitleForHistory,
                historyEntry = historyEntry,
                scrollY = scrollY
            )

            // Modifying the backstack and committing state should happen on the main thread.
            withContext(Dispatchers.Main) {
                currentTab.backStack.add(pageBackStackItem)
                Log.d(
                    HISTORY_DEBUG_TAG,
                    "PageBackStackItem added for: ${commonPageTitleForHistory.apiPath}. Stack size: ${currentTab.backStack.size}"
                )
                currentOsrsApp.commitTabState()
            }
        }
    }
}
