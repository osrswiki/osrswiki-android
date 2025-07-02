package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.R

class PageLoadCoordinator(
    private val pageViewModel: PageViewModel,
    private val pageContentLoader: PageContentLoader,
    private val uiUpdater: PageUiUpdater,
    private val fragmentContextProvider: () -> PageFragment?
) {
    fun initiatePageLoad(forceNetwork: Boolean = false) {
        val fragment = fragmentContextProvider() ?: return
        val pageIdArg = fragment.getPageIdArg()
        val pageTitleArg = fragment.getPageTitleArg()

        val currentIdToLoadArg = pageIdArg
        val currentTitleToLoadArg = pageTitleArg
        var idToLoad: Int? = null
        if (!currentIdToLoadArg.isNullOrBlank()) {
            try {
                idToLoad = currentIdToLoadArg.toInt()
            } catch (e: NumberFormatException) {
                idToLoad = null
            }
        }
        val currentViewModelPageId: Int? = pageViewModel.uiState.pageId
        val currentViewModelPlainTextTitle = pageViewModel.uiState.plainTextTitle
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
        uiUpdater.updateUi()

        if (idToLoad != null) {
            if (!forceNetwork && currentViewModelPageId == idToLoad && contentAlreadyLoaded) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                uiUpdater.updateUi()
                return
            } else {
                pageContentLoader.loadPageById(idToLoad, currentTitleToLoadArg, forceNetwork)
            }
        } else if (!currentTitleToLoadArg.isNullOrBlank()) {
            if (!forceNetwork && currentViewModelPlainTextTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                uiUpdater.updateUi()
                return
            } else {
                pageContentLoader.loadPageByTitle(currentTitleToLoadArg, forceNetwork)
            }
        } else {
            val errorMsg = fragment.getString(R.string.error_no_article_identifier)
            val titleMsg = fragment.getString(R.string.title_page_not_specified)
            pageViewModel.uiState = PageUiState(isLoading = false, error = errorMsg, title = titleMsg, plainTextTitle = titleMsg, pageId = null, htmlContent = null)
            uiUpdater.updateUi()
        }
    }
}
