package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.page.preemptive.PreloadedPageCache
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L

class PageLoadCoordinator(
    private val pageViewModel: PageViewModel,
    private val pageContentLoader: PageContentLoader,
    private val uiUpdater: PageUiUpdater,
    private val pageWebViewManager: PageWebViewManager,
    private val fragmentContextProvider: () -> PageFragment?
) {
    
    fun initiatePageLoad(theme: Theme, forceNetwork: Boolean = false) {
        L.d("PageLoadCoordinator.initiatePageLoad: Starting page load.")
        val fragment = fragmentContextProvider() ?: return
        val pageIdArg = fragment.getPageIdArg()
        val pageTitleArg = fragment.getPageTitleArg()

        // Immediately set the UI to a loading state with initial progress.
        val titleDuringLoad = pageTitleArg ?: fragment.getString(R.string.label_loading)
        pageViewModel.uiState = PageUiState(isLoading = true, title = titleDuringLoad, progress = 5, progressText = "Downloading...")
        L.d("PageLoadCoordinator.initiatePageLoad: Set initial UI state to 5% 'Downloading...'.")
        uiUpdater.updateUi()

        var idToLoad: Int? = null
        if (!pageIdArg.isNullOrBlank()) {
            try {
                idToLoad = pageIdArg.toInt()
            } catch (e: NumberFormatException) {
                // Ignored
            }
        }

        // Skip preloaded content for now - use direct loading for everything
        // This ensures consistent behavior and eliminates the old HTML rendering path
        // (Disabled preloaded content check)

        // Skip HTML content caching check for consistent direct loading behavior
        // All pages will load fresh from the wiki
        val contentAlreadyLoaded = false // Disable old caching logic

        // Use direct loading for ALL pages - simpler and more maintainable
        L.d("PageLoadCoordinator: Using direct loading for all pages")
        
        if (idToLoad != null) {
            // Generate wiki URL by page ID and load directly
            val directWikiUrl = "https://oldschool.runescape.wiki/?curid=$idToLoad"
            L.d("PageLoadCoordinator: Loading page by ID $idToLoad via direct URL: $directWikiUrl")
            
            // Update UI state for direct loading
            pageViewModel.uiState = pageViewModel.uiState.copy(
                isLoading = true,
                error = null,
                progress = 10,
                progressText = "Loading from wiki...",
                wikiUrl = directWikiUrl,
                pageId = idToLoad,
                title = pageTitleArg,
                isDirectLoading = true
            )
            uiUpdater.updateUi()
            
            // PageUiUpdater will handle calling loadUrlDirectly() when it sees isDirectLoading = true
            
        } else if (!pageTitleArg.isNullOrBlank()) {
            // Generate wiki URL by title and load directly
            val directWikiUrl = "https://oldschool.runescape.wiki/w/" + pageTitleArg.replace(" ", "_")
            L.d("PageLoadCoordinator: Loading page '$pageTitleArg' via direct URL: $directWikiUrl")
            
            // Update UI state for direct loading
            pageViewModel.uiState = pageViewModel.uiState.copy(
                isLoading = true,
                error = null,
                progress = 10,
                progressText = "Loading from wiki...",
                wikiUrl = directWikiUrl,
                title = pageTitleArg,
                isDirectLoading = true
            )
            uiUpdater.updateUi()
            
            // PageUiUpdater will handle calling loadUrlDirectly() when it sees isDirectLoading = true
            
        } else {
            val errorMsg = fragment.getString(R.string.error_no_article_identifier)
            val titleMsg = fragment.getString(R.string.title_page_not_specified)
            pageViewModel.uiState = PageUiState(isLoading = false, error = errorMsg, title = titleMsg)
            uiUpdater.updateUi()
        }
    }
}
