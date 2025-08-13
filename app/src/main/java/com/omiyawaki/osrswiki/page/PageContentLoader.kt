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
    
    // REMOVED: loadPageDirectly() methods were unused and duplicated WebView loading functionality
    // Direct loading is now handled by PageLoadCoordinator -> PageUiUpdater -> PageWebViewManager

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
