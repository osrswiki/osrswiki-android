package com.omiyawaki.osrswiki.readinglist.repository

import android.util.Log
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.database.OfflinePageFtsDao
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.savedpages.ReadingListSnapshotDeletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
// import javax.inject.Inject // Removed
// import javax.inject.Singleton // Removed

/**
 * Repository for accessing saved page data.
 * Abstracts the data source (ReadingListPageDao) from the ViewModel.
 */
// @Singleton // Removed
class SavedPagesRepository constructor( // @Inject removed from constructor
    private val readingListPageDao: ReadingListPageDao,
    private val offlinePageFtsDaoProvider: () -> OfflinePageFtsDao = { AppDatabase.instance.offlinePageFtsDao() },
    private val databaseProvider: () -> AppDatabase = { AppDatabase.instance },
    private val invalidatePreparedArticle: (ArticlePrewarmRequest) -> Unit =
        OSRSWikiApp::invalidatePreparedArticleIfAvailable,
    private val deleteReadingListRowsOverride:
        (suspend (List<Long>, android.content.Context) -> List<ReadingListPage>)? = null
) {

    /**
     * Retrieves a reactive list of all pages that are fully saved for offline viewing.
     * The list is ordered by the last access time, most recent first.
     */
    fun getFullySavedPages(): Flow<List<ReadingListPage>> {
        return readingListPageDao.getFullySavedPagesObservable()
    }

    /**
     * Searches through saved pages by title.
     * Returns pages whose display titles contain the search query (case-insensitive).
     */
    suspend fun searchSavedPagesByTitle(query: String): List<ReadingListPage> {
        Log.d(TAG, "searchSavedPagesByTitle: Starting search for query='$query'")
        
        val allPages = getReadableOfflinePagesSnapshot()
        Log.d(TAG, "searchSavedPagesByTitle: Found ${allPages.size} total saved pages")
        
        // Log details of saved pages for debugging
        allPages.forEachIndexed { index, page ->
            Log.d(TAG, "searchSavedPagesByTitle: Page $index - title='${page.displayTitle}', status=${page.status}, offline=${page.offline}")
        }
        
        val matches = allPages.filter { 
            val titleMatch = it.displayTitle.contains(query, ignoreCase = true)
            val descriptionMatch = it.description?.contains(query, ignoreCase = true) == true
            Log.d(TAG, "searchSavedPagesByTitle: Checking '${it.displayTitle}' - titleMatch=$titleMatch, descriptionMatch=$descriptionMatch")
            titleMatch || descriptionMatch
        }
        
        Log.d(TAG, "searchSavedPagesByTitle: Found ${matches.size} title matches for query='$query'")
        return matches
    }

    /**
     * Searches through saved pages using full-text search.
     * Returns FTS results that match the query in page content.
     */
    suspend fun searchSavedPagesByContent(query: String): List<OfflinePageFts> {
        Log.d(TAG, "searchSavedPagesByContent: Starting FTS search for query='$query'")
        
        val ftsDao = offlinePageFtsDaoProvider()
        
        // First check what's in the FTS table
        val allFtsEntries = ftsDao.getAll()
        Log.d(TAG, "searchSavedPagesByContent: FTS table has ${allFtsEntries.size} total entries")
        allFtsEntries.forEachIndexed { index, entry ->
            Log.d(TAG, "searchSavedPagesByContent: FTS Entry $index - url='${entry.url}', title='${entry.title}'")
        }
        
        val results = ftsDao.searchAll(query)
        Log.d(TAG, "searchSavedPagesByContent: Found ${results.size} FTS matches for query='$query'")
        
        results.forEachIndexed { index, result ->
            Log.d(TAG, "searchSavedPagesByContent: FTS Result $index - url='${result.url}', title='${result.title}'")
        }
        
        return results
    }

    /**
     * Combined search that searches both titles and content.
     * Returns a combined list of results from both title and FTS searches.
     */
    suspend fun searchSavedPages(query: String): List<ReadingListPage> {
        Log.d(TAG, "searchSavedPages: Starting combined search for query='$query'")
        
        // First get title matches
        val titleMatches = searchSavedPagesByTitle(query)
        Log.d(TAG, "searchSavedPages: Got ${titleMatches.size} title matches")
        
        // Then get FTS matches and convert them to ReadingListPage objects
        val ftsMatches = searchSavedPagesByContent(query)
        Log.d(TAG, "searchSavedPages: Got ${ftsMatches.size} FTS matches")
        
        // Fix: Get all saved pages independently for FTS matching
        val allSavedPages = getReadableOfflinePagesSnapshot()
        Log.d(TAG, "searchSavedPages: Got ${allSavedPages.size} total saved pages for FTS matching")
        
        val ftsPageMatches = ftsMatches.mapNotNull { ftsResult ->
            // Try to match FTS results back to saved pages by URL/title
            val matchedPage = allSavedPages.firstOrNull { page ->
                val titleMatch = ftsResult.title.contains(page.displayTitle, ignoreCase = true) ||
                        page.displayTitle.contains(ftsResult.title, ignoreCase = true)
                Log.d(TAG, "searchSavedPages: Matching FTS '${ftsResult.title}' with page '${page.displayTitle}' = $titleMatch")
                titleMatch
            }
            if (matchedPage != null) {
                Log.d(TAG, "searchSavedPages: Matched FTS result '${ftsResult.title}' to page '${matchedPage.displayTitle}'")
            }
            matchedPage
        }
        
        Log.d(TAG, "searchSavedPages: Got ${ftsPageMatches.size} FTS page matches")
        
        // Combine and deduplicate results, prioritizing title matches
        val combinedResults = (titleMatches + ftsPageMatches).distinctBy { it.id }
        Log.d(TAG, "searchSavedPages: Final combined results: ${combinedResults.size} pages")
        
        combinedResults.forEachIndexed { index, page ->
            Log.d(TAG, "searchSavedPages: Final result $index - '${page.displayTitle}'")
        }
        
        return combinedResults.sortedByDescending { it.atime } // Sort by most recently accessed
    }

    suspend fun getReadableOfflinePagesSnapshot(): List<ReadingListPage> =
        readingListPageDao.getFullySavedPagesObservable().first()

    /**
     * Deletes a single saved page including its offline objects and FTS entries.
     */
    suspend fun deleteSavedPage(page: ReadingListPage, context: android.content.Context) {
        Log.d(TAG, "deleteSavedPage: Deleting page '${page.displayTitle}'")
        deleteSavedPages(listOf(page), context)
    }

    /**
     * Deletes multiple saved pages through the same cache cleanup path as single-page deletion.
     */
    suspend fun deleteSavedPages(pages: List<ReadingListPage>, context: android.content.Context) {
        if (pages.isEmpty()) return

        Log.d(TAG, "deleteSavedPages: Deleting ${pages.size} saved pages")

        try {
            val pageIds = pages.map(ReadingListPage::id).distinct()
            val deletedPages = deleteReadingListRowsOverride?.invoke(pageIds, context)
                ?: ReadingListSnapshotDeletion(
                    context = context,
                    database = databaseProvider(),
                    invalidatePreparedArticle = invalidatePreparedArticle
                ).deleteReadingListRows(pageIds)
            Log.d(TAG, "deleteSavedPages: Deleted ${deletedPages.size} reading list page entries")
        } catch (e: Exception) {
            Log.e(TAG, "deleteSavedPages: Error deleting saved pages", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "SavedPagesRepository"
    }
}
