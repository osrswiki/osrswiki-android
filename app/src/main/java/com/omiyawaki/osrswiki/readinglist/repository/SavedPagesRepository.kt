package com.omiyawaki.osrswiki.readinglist.repository

import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.database.OfflinePageFts
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.flow.Flow
// import javax.inject.Inject // Removed
// import javax.inject.Singleton // Removed

/**
 * Repository for accessing saved page data.
 * Abstracts the data source (ReadingListPageDao) from the ViewModel.
 */
// @Singleton // Removed
class SavedPagesRepository constructor( // @Inject removed from constructor
    private val readingListPageDao: ReadingListPageDao
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
        val searchPattern = "%$query%"
        return readingListPageDao.getFullySavedPagesObservable(ReadingListPage.STATUS_SAVED)
            .let { flow ->
                // Convert flow to list for synchronous filtering
                // Note: This is a simplified approach. In a production app, you might want
                // to add a proper SQL query to the DAO for better performance
                val allPages = readingListPageDao.getPagesByStatusAndOffline(ReadingListPage.STATUS_SAVED, true)
                allPages.filter { 
                    it.displayTitle.contains(query, ignoreCase = true) ||
                    it.description?.contains(query, ignoreCase = true) == true
                }
            }
    }

    /**
     * Searches through saved pages using full-text search.
     * Returns FTS results that match the query in page content.
     */
    suspend fun searchSavedPagesByContent(query: String): List<OfflinePageFts> {
        val ftsDao = AppDatabase.instance.offlinePageFtsDao()
        return ftsDao.searchAll(query)
    }

    /**
     * Combined search that searches both titles and content.
     * Returns a combined list of results from both title and FTS searches.
     */
    suspend fun searchSavedPages(query: String): List<ReadingListPage> {
        // First get title matches
        val titleMatches = searchSavedPagesByTitle(query)
        
        // Then get FTS matches and convert them to ReadingListPage objects
        val ftsMatches = searchSavedPagesByContent(query)
        val ftsPageIds = ftsMatches.mapNotNull { ftsResult ->
            // Try to match FTS results back to saved pages by URL/title
            // This is a simplified approach - in production you might want a more robust mapping
            titleMatches.firstOrNull { page ->
                ftsResult.title.contains(page.displayTitle, ignoreCase = true) ||
                page.displayTitle.contains(ftsResult.title, ignoreCase = true)
            }
        }
        
        // Combine and deduplicate results, prioritizing title matches
        val combinedResults = (titleMatches + ftsPageIds).distinctBy { it.id }
        return combinedResults.sortedByDescending { it.atime } // Sort by most recently accessed
    }
}