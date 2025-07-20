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
    fun updateUi() {
        val state = pageViewModel.uiState
        val fragment = fragmentContextProvider()
        val context = fragment?.context

        val logMessage = "PageUiUpdater.updateUi() called. isLoading: ${state.isLoading}, " +
                "progress: ${state.progress}, error: ${state.error != null}, " +
                "htmlContent: ${state.htmlContent != null}"
        L.d(logMessage)

        binding?.apply {
            if (state.isLoading) {
                progressContainer.visibility = View.VISIBLE
                progressBar.progress = state.progress ?: 0
                progressText.text = state.progressText ?: ""
                L.d(" - Updating progress bar: ${state.progressText} (${state.progress}%)")
            } else {
                progressContainer.visibility = View.GONE
            }

            if (state.error != null) {
                errorTextView.visibility = View.VISIBLE
                errorTextView.text = state.error
                pageWebView.visibility = View.GONE
            } else {
                errorTextView.visibility = View.GONE
                pageWebView.visibility = View.VISIBLE
                state.htmlContent?.let {
                    if (!webViewManager.isPageLoaded()) {
                        L.d("HTML is ready and WebView has not been loaded. Initiating render.")
                        webViewManager.render(it)
                    }
                }
            }

            fragment?.activity?.title =
                HtmlCompat.fromHtml(state.title ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
    }
}
