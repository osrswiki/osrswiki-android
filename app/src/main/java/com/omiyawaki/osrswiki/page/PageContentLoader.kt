package com.omiyawaki.osrswiki.page

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageContentLoader(
    private val context: Context,
    private val pageRepository: PageRepository,
    private val pageViewModel: PageViewModel,
    private val coroutineScope: CoroutineScope,
    private val onStateUpdated: () -> Unit
) {
    
    // New method for direct wiki page loading
    fun loadPageDirectly(articleQueryTitle: String, theme: Theme, webView: WebView) {
        L.d("PageContentLoader: Loading page directly from wiki: '$articleQueryTitle', theme: $theme")
        
        // Generate the wiki URL for direct loading
        val directWikiUrl = WikiSite.OSRS_WIKI.url() + "/w/" + Uri.encode(articleQueryTitle)
        L.d("PageContentLoader: Generated direct wiki URL: $directWikiUrl")
        
        // Update UI state to show loading with direct loading flag
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                pageViewModel.uiState = pageViewModel.uiState.copy(
                    isLoading = true,
                    error = null,
                    progress = 10,
                    progressText = "Loading from wiki...",
                    wikiUrl = directWikiUrl,
                    isDirectLoading = true
                )
                onStateUpdated()
                
                // Load the wiki page directly in WebView
                webView.loadUrl(directWikiUrl)
            }
        }
    }
    
    fun loadPageDirectlyById(pageId: Int, initialDisplayTitle: String?, theme: Theme, webView: WebView) {
        L.d("PageContentLoader: Loading page directly by ID: $pageId, displayTitle: '$initialDisplayTitle', theme: $theme")
        
        // Generate the wiki URL for direct loading by page ID
        val directWikiUrl = "https://oldschool.runescape.wiki/?curid=$pageId"
        L.d("PageContentLoader: Generated direct wiki URL by ID: $directWikiUrl")
        
        // Update UI state to show loading with direct loading flag
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                pageViewModel.uiState = pageViewModel.uiState.copy(
                    isLoading = true,
                    error = null,
                    progress = 10,
                    progressText = "Loading from wiki...",
                    wikiUrl = directWikiUrl,
                    pageId = pageId,
                    title = initialDisplayTitle,
                    isDirectLoading = true
                )
                onStateUpdated()
                
                // Load the wiki page directly in WebView
                webView.loadUrl(directWikiUrl)
            }
        }
    }

    fun updateRenderProgress(progress: Int) {
        if (pageViewModel.uiState.progress in 50..99) {
            val newProgress = 50 + (progress * 0.5).toInt()
            L.d("updateRenderProgress: WebView progress: $progress%. Setting new progress to $newProgress%.")
            pageViewModel.uiState = pageViewModel.uiState.copy(
                progress = newProgress,
                progressText = "Rendering page..."
            )
            onStateUpdated()
        }
    }

    fun onPageRendered() {
        L.d("onPageRendered: Page finished rendering. Setting progress to 100%.")
        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false, progress = 100, progressText = "Finished"
        )
        onStateUpdated()
    }

    fun onRenderFailed(errorMessage: String) {
        L.e("onRenderFailed: $errorMessage")
        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false, error = errorMessage, progress = null, progressText = null
        )
        onStateUpdated()
    }

}
