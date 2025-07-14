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
    private val htmlLogTag = "PageUiUpdater-HTML"

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
        } else {
            if (state.htmlContent != null) {
                binding.pageWebView.visibility = View.INVISIBLE

                // Use the standard Android Log class to specify a custom tag
                Log.d(htmlLogTag, state.htmlContent)

                // Call the updated render function with only the HTML content.
                pageWebViewManager.render(
                    fullHtml = state.htmlContent
                )
            } else {
                if (binding.pageWebView.visibility != View.VISIBLE) binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadData("<!DOCTYPE html><html><body>${fragment.getString(R.string.label_content_unavailable)}</body></html>", "text/html", "UTF-8")
            }
        }
    }
}
