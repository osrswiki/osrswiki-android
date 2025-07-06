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
    private val pageActionTabLayout: PageActionTabLayout?,
    private val getPageTitle: () -> String?
) {
    private var pageStateObserverJob: Job? = null

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
        pageActionTabLayout ?: return
        val isSaved = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        pageActionTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isSaved))
    }

    fun cancelObserving() {
        pageStateObserverJob?.cancel()
    }
}
