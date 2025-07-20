package com.omiyawaki.osrswiki.page

import android.view.View
import androidx.core.text.HtmlCompat
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.util.log.L

class PageUiUpdater(
    private val binding: FragmentPageBinding?,
    private val pageViewModel: PageViewModel,
    private val webViewManager: PageWebViewManager,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private var isRenderInitiated = false

    fun updateUi() {
        val state = pageViewModel.uiState
        val fragment = fragmentContextProvider()
        val context = fragment?.context

        val logMessage = "PageUiUpdater.updateUi() called. isLoading: ${state.isLoading}, " +
                "progress: ${state.progress}, error: ${state.error != null}, " +
                "htmlContent: ${state.htmlContent != null}"
        L.d(logMessage)

        binding?.apply {
            // Reset the render flag when a new page load begins.
            if (state.isLoading && (state.progress ?: 0) <= 5) {
                isRenderInitiated = false
            }

            if (state.isLoading) {
                progressContainer.visibility = View.VISIBLE
                progressBar.progress = state.progress ?: 0
                progressText.text = state.progressText ?: ""
                L.d(" - Updating progress bar: ${state.progressText} (${state.progress}%)")
            } else {
                // When isLoading is false, the page has already been revealed by the
                // fragment's callback. This block's only job is to hide the progress UI.
                progressContainer.visibility = View.GONE
            }

            if (state.error != null) {
                errorTextView.visibility = View.VISIBLE
                errorTextView.text = state.error
                pageWebView.visibility = View.GONE
            } else {
                errorTextView.visibility = View.GONE
                pageWebView.visibility = if (state.htmlContent != null) View.VISIBLE else View.GONE
                state.htmlContent?.let {
                    if (!isRenderInitiated) {
                        L.d("HTML is ready and render has not been initiated. Calling render().")
                        isRenderInitiated = true
                        webViewManager.render(it)
                    }
                }
            }

            fragment?.activity?.title =
                HtmlCompat.fromHtml(state.title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
    }
}
