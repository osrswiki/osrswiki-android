package com.omiyawaki.osrswiki.readinglist.repository

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
}