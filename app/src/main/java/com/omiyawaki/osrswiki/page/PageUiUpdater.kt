package com.omiyawaki.osrswiki.page

import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.R
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

        // Handle Loading State
        binding.progressContainer.isVisible = state.isLoading
        if (state.isLoading) {
            state.progress?.let { progress ->
                binding.progressBar.progress = progress
                val progressText = "Loading page - $progress%"
                binding.progressText.text = progressText
                Log.d(logTag, "  - Updating progress bar: $progressText")
            } ?: run {
                // Default text when loading starts but progress hasn't been reported
                binding.progressText.text = "Loading page..."
                Log.d(logTag, "  - Showing progress bar with default text.")
            }
        }

        // Handle Error State
        state.error?.let {
            binding.errorTextView.text = it
            binding.errorTextView.isVisible = true
        } ?: run {
            binding.errorTextView.isVisible = false
        }

        // Handle Content and WebView Visibility
        if (state.isLoading || state.error != null) {
            binding.pageWebView.visibility = View.INVISIBLE
        } else {
            // This is the success state
            if (state.htmlContent != null) {
                // Hide webview before loading to prevent flashes of unstyled content.
                // It will be made visible by the PageWebViewManager upon successful render.
                binding.pageWebView.visibility = View.INVISIBLE
                Log.d(logTag, "  - Calling pageWebViewManager.render()")
                pageWebViewManager.render(fullHtml = state.htmlContent)
            } else {
                // Content is unexpectedly null after loading without error.
                binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadData(
                    "<!DOCTYPE html><html><body>${fragment.getString(R.string.label_content_unavailable)}</body></html>",
                    "text/html",
                    "UTF-8"
                )
            }
        }
    }
}
