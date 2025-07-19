package com.omiyawaki.osrswiki.page

import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding

class PageUiUpdater(
    private val binding: FragmentPageBinding,
    private val pageViewModel: PageViewModel,
    private val pageWebViewManager: PageWebViewManager,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val logTag = "PageLoadTrace"

    fun updateUi() {
        val fragment = fragmentContextProvider() ?: return
        val state = pageViewModel.uiState

        if (!fragment.isAdded || !fragment.isVisible) {
            return
        }

        Log.d(logTag, "PageUiUpdater.updateUi() called. isLoading: ${state.isLoading}, progress: ${state.progress}, error: ${state.error != null}, htmlContent: ${state.htmlContent != null}")

        // Initiate Rendering if content is ready and not already rendering.
        // We check webView.url == null as a proxy to know if loadDataWithBaseURL has been called yet.
        // This is the key fix: it decouples the render call from the isLoading flag.
        if (state.htmlContent != null && binding.pageWebView.url == null) {
            Log.d(logTag, "HTML is ready and WebView has not been loaded. Initiating render.")
            pageWebViewManager.render(fullHtml = state.htmlContent)
        }

        // Handle Loading State and Progress Bar
        binding.progressContainer.isVisible = state.isLoading
        if (state.isLoading) {
            binding.progressBar.progress = state.progress ?: 0
            binding.progressText.text = state.progressText ?: "Loading..."
            Log.d(logTag, " - Updating progress bar: ${binding.progressText.text} (${state.progress ?: 0}%)")
        }

        // Handle Error State
        binding.errorTextView.isVisible = state.error != null
        state.error?.let {
            binding.errorTextView.text = it
        }

        // The WebView should only become visible after loading is fully complete (isLoading=false) and there are no errors.
        // The final reveal is handled inside PageWebViewManager to prevent content flashing.
        binding.pageWebView.isVisible = !state.isLoading && state.error == null
    }
}
