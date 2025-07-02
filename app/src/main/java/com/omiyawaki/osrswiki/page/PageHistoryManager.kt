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

class PageHistoryManager(
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val HISTORY_DEBUG_TAG = "PageHistoryManager"

    fun logPageVisit() {
        val fragment = fragmentContextProvider() ?: return
        if (!fragment.isAdded || fragment.provideBinding() == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Fragment not in a valid state to log history.")
            return
        }

        val binding = fragment.provideBinding()!!
        val currentOsrsApp = OSRSWikiApp.instance
        val currentTab = currentOsrsApp.currentTab
        val state = pageViewModel.uiState
        val navigationSource = fragment.getNavigationSource()

        if (state.isLoading || state.error != null || state.htmlContent == null || state.wikiUrl == null || state.plainTextTitle == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Page not fully loaded or essential data missing. Skipping history logging.")
            return
        }

        if (currentTab != null && currentTab.backStack.isNotEmpty()) {
            val lastBackStackUrl = currentTab.backStack.last().pageTitle.wikiUrl
            if (lastBackStackUrl == state.wikiUrl) {
                val lastBackStackItem = currentTab.backStack.last()
                val newScrollY = binding.pageWebView.scrollY
                if (lastBackStackItem.scrollY != newScrollY) {
                    lastBackStackItem.scrollY = newScrollY
                    currentOsrsApp.commitTabState()
                }
                return
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
            source = navigationSource
        )

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    AppDatabase.instance.historyEntryDao().upsertEntry(historyEntry)
                    Log.d(HISTORY_DEBUG_TAG, "Global history upserted for: ${commonPageTitleForHistory.apiPath}")
                } catch (e: Exception) {
                    L.e("$HISTORY_DEBUG_TAG: Error upserting history entry", e)
                }
            }
        }

        if (currentTab == null) {
            Log.e(HISTORY_DEBUG_TAG, "logPageVisit: Current tab is null. Cannot add to tab backstack.")
            return
        }

        val pageBackStackItem = PageBackStackItem(
            pageTitle = commonPageTitleForHistory,
            historyEntry = historyEntry,
            scrollY = binding.pageWebView.scrollY
        )
        currentTab.backStack.add(pageBackStackItem)
        Log.d(HISTORY_DEBUG_TAG, "PageBackStackItem added for: ${commonPageTitleForHistory.apiPath}. Stack size: ${currentTab.backStack.size}")
        currentOsrsApp.commitTabState()
    }
}
