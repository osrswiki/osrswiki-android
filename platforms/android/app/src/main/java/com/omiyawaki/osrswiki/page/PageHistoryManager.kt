package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.common.models.PageTitle
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.WikiUrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class PageHistoryManager(
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val HISTORY_DEBUG_TAG = "PageHistoryManager"

    fun logPageVisit(snippet: String? = null, thumbnailUrl: String? = null) {
        coroutineScope.launch(Dispatchers.IO) {
            // Capture diagnostic information immediately
            val stackTrace = Thread.currentThread().stackTrace.joinToString("\n") { "  at $it" }
            val threadName = Thread.currentThread().name
            
            val fragment = fragmentContextProvider() ?: return@launch
            if (!fragment.isAdded || fragment.provideBinding() == null) {
                Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Fragment not in a valid state to log history.")
                return@launch
            }

            val state = pageViewModel.uiState
            val navigationSource = fragment.getNavigationSource()

            if (state.isLoading || state.error != null || state.htmlContent == null || state.wikiUrl == null || state.plainTextTitle == null) {
                Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Page not fully loaded or essential data missing. Skipping history logging.")
                return@launch
            }
            
            // Log detailed diagnostic information
            Log.d(HISTORY_DEBUG_TAG, """
                ========== HISTORY ENTRY DIAGNOSTIC ==========
                Original wikiUrl: '${state.wikiUrl}'
                wikiUrl length: ${state.wikiUrl.length}
                revisionId: ${state.revisionId}
                plainTextTitle: '${state.plainTextTitle}'
                displayText: '${state.title}'
                thread: $threadName
                navigationSource: $navigationSource
                timestamp: ${System.currentTimeMillis()}
                Stack trace:
                $stackTrace
                ==============================================
            """.trimIndent())

            // Normalize URL to ensure consistency across navigation sources
            val normalizedUrl = WikiUrlUtil.normalize(state.wikiUrl, state.plainTextTitle)
            
            if (normalizedUrl != state.wikiUrl) {
                Log.d(HISTORY_DEBUG_TAG, "URL normalized from '${state.wikiUrl}' to '$normalizedUrl'")
            }
            
            val commonPageTitleForHistory = PageTitle(
                wikiUrl = normalizedUrl,
                displayText = state.title ?: state.plainTextTitle,
                pageId = state.pageId ?: -1,
                apiPath = state.plainTextTitle
            )

            val historyEntry = HistoryEntry(commonPageTitleForHistory, navigationSource).apply {
                timestamp = Date()
                this.snippet = snippet
                this.thumbnailUrl = thumbnailUrl
            }
            
            // Log the actual HistoryEntry that will be inserted
            Log.d(HISTORY_DEBUG_TAG, """
                ========== INSERTING HISTORY ENTRY ==========
                HistoryEntry.wikiUrl: '${historyEntry.wikiUrl}'
                HistoryEntry.wikiUrl length: ${historyEntry.wikiUrl.length}
                HistoryEntry.apiPath: '${historyEntry.apiPath}'
                HistoryEntry.displayText: '${historyEntry.displayText}'
                HistoryEntry.pageId: ${historyEntry.pageId}
                HistoryEntry.timestamp: ${historyEntry.timestamp}
                =============================================
            """.trimIndent())

            try {
                AppDatabase.instance.historyEntryDao().upsertEntry(historyEntry)
                Log.d(HISTORY_DEBUG_TAG, "Global history upserted for: ${commonPageTitleForHistory.apiPath}")
            } catch (e: Exception) {
                L.e("$HISTORY_DEBUG_TAG: Error upserting history entry", e)
            }
        }
    }
}