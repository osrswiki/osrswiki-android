package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.util.log.L
import java.io.IOException

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private val pageViewModel = PageViewModel()
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null

    private var visualStateCallbackIdCounter: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Arguments are now processed here from the bundle
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
        }
        L.d("PageFragment onCreate - Args processed: ID: $pageIdArg, Title: $pageTitleArg")

        pageRepository = (requireActivity().applicationContext as OSRSWikiApp).pageRepository

        pageContentLoader = PageContentLoader(
            pageRepository,
            pageViewModel,
            lifecycleScope,
            onStateUpdated = {
                if (isAdded && _binding != null) {
                    updateUiFromViewModel()
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("PageFragment onCreateView")
        _binding = FragmentPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("PageFragment onViewCreated. Page ID from member: $pageIdArg, Page Title from member: $pageTitleArg")

        binding.pageWebView.webViewClient = object : WebViewClient() {
            @SuppressLint("RequiresApi")
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                L.d("WebView onPageFinished for URL: $url.")

                applyWebViewStylingAndRevealBody()

                if (view == null || _binding == null || !isAdded) {
                    L.w("onPageFinished: WebView, binding, or fragment not in a valid state to proceed with visibility.")
                    return
                }

                if (!pageViewModel.uiState.isLoading && pageViewModel.uiState.error == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val currentCallbackId = ++visualStateCallbackIdCounter
                        L.d("Requesting postVisualStateCallback (ID: $currentCallbackId) to make WebView widget visible.")
                        view.postVisualStateCallback(currentCallbackId, object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                if (requestId == currentCallbackId && isAdded && _binding != null) {
                                    if (!pageViewModel.uiState.isLoading && pageViewModel.uiState.error == null) {
                                        binding.pageWebView.visibility = View.VISIBLE
                                        L.d("WebView VisualStateCallback.onComplete (ID: $requestId): Made WebView WIDGET visible.")
                                    } else {
                                        L.w("WebView VisualStateCallback.onComplete (ID: $requestId): Conditions no longer met (isLoading=${pageViewModel.uiState.isLoading}, error=${pageViewModel.uiState.error != null}).")
                                    }
                                } else if (requestId != currentCallbackId) {
                                    L.w("WebView VisualStateCallback.onComplete: Mismatched requestId. Expected $currentCallbackId, got $requestId.")
                                } else {
                                     L.d("WebView VisualStateCallback.onComplete (ID: $requestId): Fragment not added or binding null.")
                                }
                            }
                        })
                    } else {
                        binding.pageWebView.visibility = View.VISIBLE
                        L.d("WebView onPageFinished (Legacy API < 23): Made WebView WIDGET visible directly.")
                    }
                } else {
                    L.d("WebView onPageFinished: Conditions not met to make WebView WIDGET visible (isLoading=${pageViewModel.uiState.isLoading}, error=${pageViewModel.uiState.error!=null}). WebView remains invisible.")
                }
            }
        }

        binding.pageWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    L.i("WebViewConsole [${it.sourceId()}:${it.lineNumber()}] ${it.message()}")
                }
                return true
            }
        }

        binding.pageWebView.settings.javaScriptEnabled = true
        setWebViewWidgetBackgroundColor()

        updateUiFromViewModel()
        initiatePageLoad(forceNetwork = false) // This will use the member pageIdArg/pageTitleArg

        binding.errorTextView.setOnClickListener {
            L.i("Retry button clicked. pageIdArg: $pageIdArg, pageTitleArg: $pageTitleArg. Forcing network.")
            initiatePageLoad(forceNetwork = true)
        }
    }

    private fun isDarkMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setWebViewWidgetBackgroundColor() {
        if (_binding == null || !isAdded) return
        val backgroundColorRes = R.color.osrs_parchment_bg
        val backgroundColorInt = ContextCompat.getColor(requireContext(), backgroundColorRes)
        binding.pageWebView.setBackgroundColor(backgroundColorInt)
        L.d("Set WebView WIDGET background color for mode (isDark: ${isDarkMode()}) to hex: ${String.format("#%06X", (0xFFFFFF and backgroundColorInt))}")
    }

    private fun applyWebViewStylingAndRevealBody() {
        if (_binding == null || !isAdded || context == null) {
            L.w("applyWebViewStylingAndRevealBody: Binding is null or fragment not added or context is null. Skipping.")
            return
        }
        L.d("applyWebViewStylingAndRevealBody called.")

        val cssString: String
        try {
            cssString = requireContext().assets.open("styles/wiki_content.css").bufferedReader().use { it.readText() }
            L.d("Successfully read wiki_content.css from assets.")
        } catch (e: IOException) {
            L.e("Error reading wiki_content.css from assets", e)
            return
        }

        val escapedCssString = cssString
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("'", "\\'")
            .replace("\n", "\\n")

        val injectCssJs = """
            (function() {
                var style = document.getElementById('osrsWikiInjectedStyle');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'osrsWikiInjectedStyle';
                    style.type = 'text/css';
                    var head = document.head || document.getElementsByTagName('head')[0];
                    if (head) {
                        head.appendChild(style);
                    } else {
                        console.error('OSRSWikiApp: Could not find head to inject CSS.');
                        return;
                    }
                }
                style.innerHTML = '${escapedCssString}';
                console.log('OSRSWikiApp: styles/wiki_content.css injected/updated.');
            })();
        """.trimIndent()

        binding.pageWebView.evaluateJavascript(injectCssJs) { result ->
            L.d("JavaScript for CSS injection from assets evaluated. Result: $result")

            val themeClass = if (isDarkMode()) "theme-dark" else "theme-light"
            val applyThemeAndRevealBodyJs = """
                (function() {
                    if (!document.body) { 
                        console.error('OSRSWikiApp: document.body not ready for class list or style manipulation.');
                        return; 
                    }
                    document.body.classList.remove('theme-light', 'theme-dark');
                    document.body.classList.add('$themeClass');
                    document.body.style.visibility = 'visible'; 
                    console.log('OSRSWikiApp: Applied theme class (' + '$themeClass' + ') and set body.style.visibility to visible.');
                })();
            """.trimIndent()

            binding.pageWebView.evaluateJavascript(applyThemeAndRevealBodyJs) { themeResult ->
                L.d("JavaScript for applying theme class and revealing body evaluated. Result: $themeResult")
            }
        }
    }

    private fun initiatePageLoad(forceNetwork: Boolean = false) {
        val currentIdToLoadArg = pageIdArg
        val currentTitleToLoadArg = pageTitleArg
        var idToLoad: Int? = null

        if (!currentIdToLoadArg.isNullOrBlank()) {
            try {
                idToLoad = currentIdToLoadArg.toInt()
            } catch (e: NumberFormatException) {
                L.w("currentIdToLoadArg '$currentIdToLoadArg' is not a valid integer. Will try title.")
                idToLoad = null
            }
        }

        val currentViewModelPageId = pageViewModel.uiState.pageId
        val currentViewModelTitle = pageViewModel.uiState.title
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
        updateUiFromViewModel()

        if (idToLoad != null) {
            if (!forceNetwork && currentViewModelPageId == idToLoad && contentAlreadyLoaded) {
                L.d("Page with ID '$idToLoad' data already present. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                L.i("Requesting to load page by ID: $idToLoad (Title arg was: '$currentTitleToLoadArg')")
                pageContentLoader.loadPageById(idToLoad, currentTitleToLoadArg, forceNetwork)
            }
        } else if (!currentTitleToLoadArg.isNullOrBlank()) {
            if (!forceNetwork && currentViewModelTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
                L.d("Page with title '$currentTitleToLoadArg' data already present. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                L.i("Requesting to load page by title: '$currentTitleToLoadArg' (ID arg was: '$currentIdToLoadArg')")
                pageContentLoader.loadPageByTitle(currentTitleToLoadArg, forceNetwork)
            }
        } else {
            L.e("Cannot load page: No valid pageId or pageTitle provided. pageIdArg: '$currentIdToLoadArg', pageTitleArg: '$currentTitleToLoadArg'")
            pageViewModel.uiState = PageUiState(
                isLoading = false,
                error = getString(R.string.error_no_article_identifier),
                title = getString(R.string.title_page_not_specified),
                pageId = null,
                htmlContent = null
            )
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        L.d("PageFragment updateUiFromViewModel. Current state: ${pageViewModel.uiState}")
        val state = pageViewModel.uiState

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.error?.let { detailedErrorString ->
            L.e("Page load error (technical details): $detailedErrorString")
            binding.errorTextView.text = detailedErrorString
            binding.errorTextView.visibility = View.VISIBLE
            binding.pageWebView.visibility = View.INVISIBLE
        } ?: run {
            binding.errorTextView.visibility = View.GONE
        }

        if (state.isLoading || state.error != null) {
            if (binding.pageWebView.visibility == View.VISIBLE || state.isLoading) {
                 binding.pageWebView.visibility = View.INVISIBLE
            }
            if (state.isLoading && state.htmlContent == null && binding.pageWebView.url == null) {
                L.d("Loading blank data into WebView as it's a fresh load.")
                val blankHtml = """
                    <!DOCTYPE html><html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>body{visibility:hidden;background-color:transparent;}</style>
                    </head><body></body></html>
                """.trimIndent()
                binding.pageWebView.loadData(blankHtml, "text/html", "UTF-8")
            }
        } else { 
            state.htmlContent?.let { htmlBodySnippet -> // Assume htmlContent is just the body snippet
                L.d("Loading actual HTML content into WebView.")
                binding.pageWebView.visibility = View.INVISIBLE
                
                val finalHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>${state.title ?: pageTitleArg ?: "OSRS Wiki"}</title>
                        </head>
                    <body>
                        ${htmlBodySnippet}
                    </body>
                    </html>
                """.trimIndent()

                val baseUrl = "https://oldschool.runescape.wiki/"
                binding.pageWebView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
            } ?: run {
                L.w("Attempting to display content, but htmlContent is null and not loading/error state.")
                 binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadDataWithBaseURL(null, getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
        L.i("PageFragment UI updated. ViewModel title: '${state.title}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading()
            (_binding?.root as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        _binding = null
        L.d("PageFragment onDestroyView")
    }

    companion object {
        private const val ARG_PAGE_ID = "pageId"
        private const val ARG_PAGE_TITLE = "pageTitle"

        @JvmStatic
        fun newInstance(pageId: String?, pageTitle: String?): PageFragment {
            return PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_ID, pageId)
                    putString(ARG_PAGE_TITLE, pageTitle)
                }
            }
        }
    }
}
