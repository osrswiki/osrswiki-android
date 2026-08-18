package com.omiyawaki.osrswiki.page

import android.util.Log
import com.omiyawaki.osrswiki.common.models.PageTitle
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.history.HistoryMetadataBackfill
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.network.WikiApiService
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.StringUtil
import com.omiyawaki.osrswiki.util.WikiUrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class PageHistoryManager(
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope,
    private val wikiApiService: WikiApiService = RetrofitClient.apiService,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val HISTORY_DEBUG_TAG = "PageHistoryManager"

    fun logPageVisit(snippet: String? = null, thumbnailUrl: String? = null) {
        coroutineScope.launch(Dispatchers.IO) {
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

            val normalizedUrl = WikiUrlUtil.normalize(state.wikiUrl, state.plainTextTitle)

            if (normalizedUrl != state.wikiUrl) {
                Log.d(HISTORY_DEBUG_TAG, "URL normalized from '${state.wikiUrl}' to '$normalizedUrl'")
            }

            val commonPageTitleForHistory = PageTitle(
                wikiUrl = normalizedUrl,
                displayText = StringUtil.extractMainTitle(state.title ?: state.plainTextTitle),
                pageId = state.pageId ?: -1,
                apiPath = state.plainTextTitle
            )

            val historyEntry = HistoryEntry(commonPageTitleForHistory, navigationSource).apply {
                timestamp = Date()
                this.snippet = snippet
                this.thumbnailUrl = thumbnailUrl
            }

            try {
                val dao = AppDatabase.instance.historyEntryDao()
                historyEntry.preserveExistingMetadata(dao.findEntryByUrl(normalizedUrl))
                if (HistoryMetadataBackfill.needsEnrichment(historyEntry)) {
                    val title = HistoryMetadataBackfill.previewTitle(historyEntry)
                    val preview = fetchPagePreview(
                        pageId = historyEntry.pageId,
                        title = title
                    )
                    HistoryMetadataBackfill.apply(historyEntry, preview.first, preview.second)
                }
                dao.upsertEntry(historyEntry)
                Log.d(HISTORY_DEBUG_TAG, "Global history upserted for: ${commonPageTitleForHistory.apiPath}")
            } catch (e: Exception) {
                L.e("$HISTORY_DEBUG_TAG: Error upserting history entry", e)
            }
        }
    }

    private suspend fun fetchPagePreview(pageId: Int?, title: String): Pair<String?, String?> {
        val trimmedTitle = title.replace('_', ' ').trim()
        val previewPage = runCatching {
            if (trimmedTitle.isNotEmpty()) {
                wikiApiService.getHistoryPreviewMetadata(trimmedTitle)
            } else if (pageId != null && pageId > 0) {
                wikiApiService.getPageExtracts(pageId.toString())
            } else {
                null
            }
        }.getOrNull()?.query?.pages?.firstOrNull()

        val intro = previewPage?.extract?.trim().orEmpty().ifBlank { null }
        val thumbnail = previewPage?.thumbnail?.source?.trim().orEmpty().ifBlank { null }
        if (intro != null) {
            return intro to thumbnail
        }

        if (pageId == null || pageId <= 0) return null to thumbnail
        val fallback = runCatching {
            wikiApiService.getPageExtract(pageId.toString())
        }.getOrNull()?.query?.pages?.firstOrNull()?.snippet?.trim()?.takeIf { it.isNotEmpty() }
        return fallback to thumbnail
    }
}
