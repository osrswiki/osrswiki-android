package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.action.PageActionItem
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageReadingListManager(
    private val pageViewModel: PageViewModel,
    private val readingListPageDao: ReadingListPageDao,
    private val coroutineScope: CoroutineScope,
    private val pageActionBarManager: PageActionBarManager?,
    private val getPageTitle: () -> String?
) {
    private var pageStateObserverJob: Job? = null
    
    init {
        // Set up the save button callback
        pageActionBarManager?.saveClickCallback = { toggleSaveState() }
    }

    fun observeAndRefreshSaveButtonState() {
        pageStateObserverJob?.cancel()
        val titleForDaoLookup = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: getPageTitle()?.takeIf { it.isNotBlank() }

        if (titleForDaoLookup.isNullOrBlank()) {
            updateSaveIcon(null)
            return
        }

        val pagePackageTitle = PageTitle(
            namespace = Namespace.MAIN,
            text = titleForDaoLookup,
            wikiSite = WikiSite.OSRS_WIKI
        )

        pageStateObserverJob = coroutineScope.launch {
            val defaultListId = withContext(Dispatchers.IO) { AppDatabase.instance.readingListDao().let { it.getDefaultList() ?: it.createDefaultListIfNotExist() }.id }
            readingListPageDao.observePageByListIdAndTitle(
                pagePackageTitle.wikiSite,
                pagePackageTitle.wikiSite.languageCode,
                pagePackageTitle.namespace(),
                pagePackageTitle.prefixedText,
                defaultListId
            ).collectLatest { entry -> updateSaveIcon(entry) }
        }
    }

    private fun updateSaveIcon(entry: ReadingListPage?) {
        val isSaved = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        pageActionBarManager?.updateSaveIcon(isSaved)
    }

    fun cancelObserving() {
        pageStateObserverJob?.cancel()
    }
    
    private fun toggleSaveState() {
        val state = pageViewModel.uiState
        val titleText = state.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: getPageTitle()?.takeIf { it.isNotBlank() }
            
        if (titleText.isNullOrBlank() || state.wikiUrl == null) {
            return
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Create a basic WikiSite and PageTitle for the current page
                val wikiSite = WikiSite.OSRS_WIKI
                val namespace = com.omiyawaki.osrswiki.page.Namespace.MAIN
                
                val existingEntry = readingListPageDao.findPageInAnyList(wikiSite, wikiSite.languageCode, namespace, titleText)
                
                if (existingEntry != null && existingEntry.offline && existingEntry.status == ReadingListPage.STATUS_SAVED) {
                    // Page is saved, so remove it
                    readingListPageDao.deleteReadingListPage(existingEntry)
                } else {
                    // Page is not saved, so save it
                    val pageTitle = com.omiyawaki.osrswiki.page.PageTitle(
                        namespace = namespace,
                        text = titleText,
                        wikiSite = wikiSite,
                        displayText = state.title ?: titleText
                    )
                    val newEntry = ReadingListPage(pageTitle).apply {
                        offline = true
                        status = ReadingListPage.STATUS_SAVED
                    }
                    readingListPageDao.insertReadingListPage(newEntry)
                }
            } catch (e: Exception) {
                // Handle error silently for now
            }
        }
    }
}
