package com.omiyawaki.osrswiki.page

import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.bridge.JavaScriptActionHandler
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding

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
                val tableScript = JavaScriptActionHandler.getToggleTablesScript()

                if (tableScript.isNotEmpty()) {
                    Log.d(TAG, "Injecting table collapse script into HTML.")
                    val scriptTag = "<script>$tableScript</script>"
                    finalHtml = finalHtml.replace("</head>", "$scriptTag</head>", ignoreCase = true)
                } else {
                    Log.d(TAG, "Table collapse script is empty, not injecting.")
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
