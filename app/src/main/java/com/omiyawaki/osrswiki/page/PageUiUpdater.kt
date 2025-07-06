package com.omiyawaki.osrswiki.page

import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.settings.Prefs
// NOTE: We are not using the custom L wrapper for the HTML log
// import com.omiyawaki.osrswiki.util.log.L

class PageUiUpdater(
    private val binding: FragmentPageBinding,
    private val pageViewModel: PageViewModel,
    private val pageWebViewManager: PageWebViewManager,
    private val fragmentContextProvider: () -> PageFragment?
) {
    private val TAG = "PageUiUpdater"
    private val HTML_LOG_TAG = "PageUiUpdater-HTML"

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
                var injectedContent = ""

                // The dynamic style for top padding is no longer needed.
                // val dynamicStyle = "<style>body { padding-top: ${toolbarHeightPx}px !important; }</style>"
                // injectedContent += dynamicStyle

                if (Prefs.isCollapseTablesEnabled) {
                    val css = JavaScriptActionHandler.getCollapsibleTablesCss(fragment.requireContext())
                    val js = JavaScriptActionHandler.getCollapsibleTablesJs(fragment.requireContext())
                    val scriptTag = "<script>${js}</script>"
                    injectedContent += "$css$scriptTag"
                }

                if (injectedContent.isNotEmpty()) {
                    finalHtml = finalHtml.replace("</head>", "$injectedContent</head>", ignoreCase = true)
                }

                // Use the standard Android Log class to specify a custom tag
                Log.d(HTML_LOG_TAG, finalHtml)

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
