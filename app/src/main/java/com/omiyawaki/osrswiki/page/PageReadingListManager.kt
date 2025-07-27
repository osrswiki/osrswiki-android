package com.omiyawaki.osrswiki.page

import android.content.Context
import android.util.Log
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.action.PageActionItem
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker
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
    private val getPageTitle: () -> String?,
    private val context: Context
) {
    private var pageStateObserverJob: Job? = null
    
    init {
        // Set up the save button callback
        pageActionBarManager?.saveClickCallback = { 
            Log.d(TAG, "Save button clicked! Calling toggleSaveState()")
            toggleSaveState() 
        }
    }

    companion object {
        private const val TAG = "PageReadingListManager"
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
        val saveState = when {
            entry == null || !entry.offline -> PageActionBarManager.SaveState.NOT_SAVED
            entry.status == ReadingListPage.STATUS_QUEUE_FOR_SAVE -> PageActionBarManager.SaveState.DOWNLOADING
            entry.status == ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE -> PageActionBarManager.SaveState.DOWNLOADING  
            entry.status == ReadingListPage.STATUS_SAVED -> PageActionBarManager.SaveState.SAVED
            entry.status == ReadingListPage.STATUS_ERROR -> PageActionBarManager.SaveState.ERROR
            else -> PageActionBarManager.SaveState.ERROR // For any unexpected status
        }
        pageActionBarManager?.updateSaveIcon(saveState)
    }

    fun cancelObserving() {
        pageStateObserverJob?.cancel()
    }
    
    private fun toggleSaveState() {
        Log.d(TAG, "toggleSaveState() called")
        val state = pageViewModel.uiState
        val titleText = state.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: getPageTitle()?.takeIf { it.isNotBlank() }
            
        Log.d(TAG, "Page title: '$titleText', Wiki URL: '${state.wikiUrl}'")
        
        if (titleText.isNullOrBlank() || state.wikiUrl == null) {
            Log.w(TAG, "Cannot save: title is blank or URL is null")
            return
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Create a basic WikiSite and PageTitle for the current page
                val wikiSite = WikiSite.OSRS_WIKI
                val namespace = com.omiyawaki.osrswiki.page.Namespace.MAIN
                
                val existingEntry = readingListPageDao.findPageInAnyList(wikiSite, wikiSite.languageCode, namespace, titleText)
                
                when {
                    existingEntry != null && existingEntry.offline && existingEntry.status == ReadingListPage.STATUS_SAVED -> {
                        // Page is saved, so remove it
                        Log.i(TAG, "Removing saved page: $titleText")
                        readingListPageDao.deleteReadingListPage(existingEntry)
                    }
                    existingEntry != null && existingEntry.offline && existingEntry.status == ReadingListPage.STATUS_ERROR -> {
                        // Page had error, retry download
                        Log.i(TAG, "Retrying failed download for: $titleText")
                        readingListPageDao.updatePageStatusToSavedAndMtime(existingEntry.id, ReadingListPage.STATUS_QUEUE_FOR_SAVE, System.currentTimeMillis())
                        SavedPageSyncWorker.enqueue(context)
                    }
                    else -> {
                        // Page is not saved, so queue it for saving
                        Log.i(TAG, "Queueing page for saving: $titleText")
                        val pageTitle = com.omiyawaki.osrswiki.page.PageTitle(
                            namespace = namespace,
                            text = titleText,
                            wikiSite = wikiSite,
                            displayText = state.title ?: titleText
                        )
                        val newEntry = ReadingListPage(pageTitle).apply {
                            offline = true
                            status = ReadingListPage.STATUS_QUEUE_FOR_SAVE // Critical fix: Queue for download
                        }
                        val insertedId = readingListPageDao.insertReadingListPage(newEntry)
                        Log.i(TAG, "Page queued with ID: $insertedId, status: ${ReadingListPage.STATUS_QUEUE_FOR_SAVE}")
                        
                        // Enqueue the worker to download the content
                        Log.i(TAG, "Enqueuing SavedPageSyncWorker for background download")
                        SavedPageSyncWorker.enqueue(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in toggleSaveState(): ${e.message}", e)
            }
        }
    }
}
