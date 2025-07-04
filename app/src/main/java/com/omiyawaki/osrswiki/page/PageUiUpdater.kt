package com.omiyawaki.osrswiki.page

import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.settings.Prefs

class PageUiUpdater(
    private val binding: FragmentPageBinding,
    private val pageViewModel: PageViewModel,
    private val pageWebViewManager: PageWebViewManager,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val TAG = "PageUiUpdater"

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
                val currentTheme = (fragment.requireActivity().application as OSRSWikiApp).getCurrentTheme()

                var finalHtml = state.htmlContent

                // Check the preference for enabling collapsible tables.
                if (Prefs.isCollapseTablesEnabled) {
                    Log.d(TAG, "Collapsible tables enabled. Injecting CSS and JS.")
                    // Get the theme-aware CSS and the feature JavaScript.
                    val css = JavaScriptActionHandler.getCollapsibleTablesCss(fragment.requireContext())
                    val js = JavaScriptActionHandler.getCollapsibleTablesJs(fragment.requireContext())
                    val scriptTag = "<script>${js}</script>"

                    // Inject both the CSS <style> block and the JS <script> block into the head.
                    finalHtml = finalHtml.replace("</head>", "$css$scriptTag</head>", ignoreCase = true)
                } else {
                    Log.d(TAG, "Collapsible tables disabled, not injecting.")
                }

                pageWebViewManager.render(
                    fullHtml = finalHtml,
                    baseUrl = state.wikiUrl,
                    theme = currentTheme
                )
            } else {
                if (binding.pageWebView.visibility != View.VISIBLE) binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadDataWithBaseURL(null, fragment.getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
    }
}
