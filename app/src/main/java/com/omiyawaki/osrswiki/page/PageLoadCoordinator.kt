package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L

class PageLoadCoordinator(
    private val pageViewModel: PageViewModel,
    private val pageContentLoader: PageContentLoader,
    private val uiUpdater: PageUiUpdater,
    private val fragmentContextProvider: () -> PageFragment?
) {
    fun initiatePageLoad(theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageLoadCoordinator.initiatePageLoad: Starting page load.")
        val fragment = fragmentContextProvider() ?: return
        val pageIdArg = fragment.getPageIdArg()
        val pageTitleArg = fragment.getPageTitleArg()

        var idToLoad: Int? = null
        if (!pageIdArg.isNullOrBlank()) {
            try {
                idToLoad = pageIdArg.toInt()
            } catch (e: NumberFormatException) {
                // Ignored
            }
        }

        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error.isNullOrEmpty()
        if (!forceNetwork && contentAlreadyLoaded) {
            if ((idToLoad != null && pageViewModel.uiState.pageId == idToLoad) ||
                (!pageTitleArg.isNullOrBlank() && pageViewModel.uiState.plainTextTitle == pageTitleArg)
            ) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                uiUpdater.updateUi()
                return
            }
        }

        // Keep the title visible immediately. Saved snapshots resolve from disk next;
        // do not flash a network "Downloading..." label before that is known.
        val titleDuringLoad = pageTitleArg ?: fragment.getString(R.string.label_loading)
        val savedOpen = fragment.getNavigationSource() == com.omiyawaki.osrswiki.history.db.HistoryEntry.SOURCE_SAVED_PAGE
        pageViewModel.uiState = PageUiState(
            isLoading = true,
            title = titleDuringLoad,
            progress = if (savedOpen) null else 5,
            progressText = if (savedOpen) null else "Opening page..."
        )
        L.d("PageLoadCoordinator.initiatePageLoad: Set initial UI state savedOpen=$savedOpen")
        uiUpdater.updateUi()

        if (idToLoad != null) {
            pageContentLoader.loadPageById(idToLoad, pageTitleArg, theme, forceNetwork)
        } else if (!pageTitleArg.isNullOrBlank()) {
            pageContentLoader.loadPageByTitle(pageTitleArg, theme, forceNetwork)
        } else {
            val errorMsg = fragment.getString(R.string.error_no_article_identifier)
            val titleMsg = fragment.getString(R.string.title_page_not_specified)
            pageViewModel.uiState = PageUiState(isLoading = false, error = errorMsg, title = titleMsg)
            uiUpdater.updateUi()
        }
    }
}
