package com.omiyawaki.osrswiki.page

import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding

class PageUiUpdater(
    private val binding: FragmentPageBinding,
    private val pageViewModel: PageViewModel,
    private val pageWebViewManager: PageWebViewManager,
    private val pageHtmlFactory: PageHtmlFactory,
    private val fragmentContextProvider: () -> PageFragment?
) {
    fun updateUi() {
        val fragment = fragmentContextProvider() ?: return
        val state = pageViewModel.uiState

        if (!fragment.isAdded || !fragment.isVisible) {
            return
        }

        binding.progressBar.isVisible = state.isLoading
        state.error?.let {
            binding.errorTextView.text = it
            binding.errorTextView.isVisible = true
            binding.pageWebView.visibility = View.INVISIBLE
        } ?: run { binding.errorTextView.isVisible = false }

        if (state.isLoading || state.error != null) {
            if (binding.pageWebView.visibility == View.VISIBLE || (state.isLoading && state.htmlContent == null)) {
                binding.pageWebView.visibility = View.INVISIBLE
            }
            if (state.isLoading && state.htmlContent == null && (binding.pageWebView.url == null || binding.pageWebView.url == "about:blank")) {
                binding.pageWebView.loadData("<html><body></body></html>", "text/html", "UTF-8")
            }
        } else {
            if (state.htmlContent != null) {
                val finalHtml = pageHtmlFactory.createPageHtml(state, fragment.getPageTitleArg())
                Log.d("PageUiUpdater", "Final HTML to render: $finalHtml")
                binding.pageWebView.visibility = View.INVISIBLE
                val currentTheme = (fragment.requireActivity().application as OSRSWikiApp).getCurrentTheme()
                pageWebViewManager.render(
                    htmlSnippet = finalHtml,
                    baseUrl = state.wikiUrl,
                    pageTitle = state.title ?: fragment.getPageTitleArg(),
                    theme = currentTheme
                )
            } else {
                if (binding.pageWebView.visibility != View.VISIBLE) binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadDataWithBaseURL(null, fragment.getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
    }
}
