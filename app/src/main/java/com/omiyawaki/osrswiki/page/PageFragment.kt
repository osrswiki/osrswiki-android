package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.util.log.L

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private val pageViewModel = PageViewModel()
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("PageFragment onCreate")

        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
            L.d("PageFragment created with ID: $pageIdArg, Title: $pageTitleArg")
        }

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

    @SuppressLint("SetJavaScriptEnabled") // For settings.javaScriptEnabled
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("PageFragment onViewCreated. Page ID: $pageIdArg, Page Title: $pageTitleArg")

        binding.pageWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyWebViewThemeCss() 
                if (_binding != null && !pageViewModel.uiState.isLoading && pageViewModel.uiState.error == null) {
                    binding.pageWebView.visibility = View.VISIBLE
                    L.d("WebView onPageFinished for URL: $url. Applied CSS and made WebView visible.")
                } else {
                    L.d("WebView onPageFinished for URL: $url. Conditions not met to make WebView visible (isLoading=${pageViewModel.uiState.isLoading}, error=${pageViewModel.uiState.error!=null}).")
                }
            }
        }
        binding.pageWebView.settings.javaScriptEnabled = true
        setWebViewWidgetBackgroundColor()


        updateUiFromViewModel() 
        initiatePageLoad(forceNetwork = false)

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
        // Corrected: Use the base color name; Android resolves it for night mode
        val backgroundColorRes = R.color.osrs_parchment_bg 
        val backgroundColorInt = ContextCompat.getColor(requireContext(), backgroundColorRes)
        binding.pageWebView.setBackgroundColor(backgroundColorInt)
        L.d("Set WebView widget background color for mode (isDark: ${isDarkMode()}) to hex: ${String.format("#%06X", (0xFFFFFF and backgroundColorInt))}")
    }


    private fun applyWebViewThemeCss() {
        if (_binding == null || !isAdded) return

        val useDarkMode = isDarkMode()
        // These resource IDs should correctly resolve to their -night variants if available
        val backgroundColorRes = R.color.osrs_parchment_bg 
        val textColorRes = R.color.osrs_text_on_parchment 

        val backgroundColorInt = ContextCompat.getColor(requireContext(), backgroundColorRes)
        val textColorInt = ContextCompat.getColor(requireContext(), textColorRes)

        val backgroundColorHex = String.format("#%06X", (0xFFFFFF and backgroundColorInt))
        val textColorHex = String.format("#%06X", (0xFFFFFF and textColorInt))

        L.d("Applying WebView CSS theme. DarkMode: $useDarkMode, Body BG: $backgroundColorHex, Body Text: $textColorHex")

        val css = """
            body {
                background-color: $backgroundColorHex !important;
                color: $textColorHex !important;
            }
            p, span, div, li, ul, ol,
            h1, h2, h3, h4, h5, h6,
            strong, b, em, i, u, s, strike,
            th, td, caption, label, legend, summary, details,
            dt, dd, figure, figcaption, address, blockquote, pre, code, samp, kbd, var, q, cite {
                color: $textColorHex !important;
                background-color: transparent !important;
            }
            a, a:link, a:visited {
                color: $textColorHex !important; 
            }
            table, tr {
                background-color: transparent !important;
            }
            hr {
                background-color: $textColorHex !important;
                border-color: $textColorHex !important;
            }
        """.trimIndent().replace("\n", " ") 

        val js = """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = '$css';
                var head = document.head || document.getElementsByTagName('head')[0];
                if (head) {
                    head.appendChild(style);
                    console.log('OSRSWikiApp: Custom CSS injected.');
                } else {
                    console.error('OSRSWikiApp: Could not find head to inject CSS.');
                }
            })();
        """.trimIndent()

        binding.pageWebView.evaluateJavascript(js) { result ->
            L.d("JavaScript for CSS injection evaluated. Result: $result")
        }
    }

    private fun initiatePageLoad(forceNetwork: Boolean = false) {
        var idToLoad: Int? = null
        if (!pageIdArg.isNullOrBlank()) {
            try {
                idToLoad = pageIdArg!!.toInt()
            } catch (e: NumberFormatException) {
                L.w("pageIdArg '$pageIdArg' is not a valid integer. Will try title.")
                idToLoad = null
            }
        }

        val currentViewModelPageId = pageViewModel.uiState.pageId
        val currentViewModelTitle = pageViewModel.uiState.title
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null
        //isLoading check was previously here, moved it below to ensure loading state is always set before new request
        val actualPageTitleFromArgs = pageTitleArg

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
        updateUiFromViewModel() 


        if (idToLoad != null) {
            // Check if we really need to load: same ID, content was loaded, and we are not currently trying to re-load it.
            if (!forceNetwork && (currentViewModelPageId == idToLoad && contentAlreadyLoaded && !pageViewModel.uiState.isLoading)) {
                 L.d("Page with ID '$idToLoad' data already present in ViewModel and not forcing network. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false) 
                updateUiFromViewModel() 
                return
            } else {
                 L.i("Requesting to load page by ID: $idToLoad (Title arg was: '$actualPageTitleFromArgs')")
                 pageContentLoader.loadPageById(idToLoad, actualPageTitleFromArgs, forceNetwork)
                 // return not needed here, loading state already set
            }
        } else if (!actualPageTitleFromArgs.isNullOrBlank()) {
            // Check if we really need to load: same title, content was loaded, and we are not currently trying to re-load it.
            if (!forceNetwork && (currentViewModelTitle == actualPageTitleFromArgs && contentAlreadyLoaded && !pageViewModel.uiState.isLoading)) {
                L.d("Page with title '$actualPageTitleFromArgs' data already present in ViewModel and not forcing network. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                L.i("Requesting to load page by title: '$actualPageTitleFromArgs' (ID arg was: '$pageIdArg')")
                pageContentLoader.loadPageByTitle(actualPageTitleFromArgs, forceNetwork)
                // return not needed here, loading state already set
            }
        } else {
            L.e("Cannot load page: No valid pageId or pageTitle provided. pageIdArg: '$pageIdArg', pageTitleArg: '$actualPageTitleFromArgs'")
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
            binding.pageWebView.visibility = View.INVISIBLE 
            if (state.isLoading) {
                binding.pageWebView.loadData("", "text/html", null) 
                setWebViewWidgetBackgroundColor() 
            }
        } else { 
            setWebViewWidgetBackgroundColor() 
            state.htmlContent?.let { html ->
                binding.pageWebView.visibility = View.INVISIBLE 
                val baseUrl = "https://oldschool.runescape.wiki/"
                binding.pageWebView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            } ?: run {
                binding.pageWebView.visibility = View.VISIBLE 
                binding.pageWebView.loadDataWithBaseURL(null, getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
        L.i("PageFragment UI updated. ViewModel title: '${state.title}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.pageWebView.stopLoading() 
        binding.pageWebView.settings.javaScriptEnabled = false
        (_binding?.root as? ViewGroup)?.removeView(binding.pageWebView)
        binding.pageWebView.destroy()
        _binding = null
        L.d("PageFragment onDestroyView")
    }

    companion object {
        private const val ARG_PAGE_ID = "pageId"
        private const val ARG_PAGE_TITLE = "pageTitle"

        @JvmStatic
        fun newInstance(pageId: String?, pageTitle: String?): PageFragment {
            L.d("PageFragment newInstance for ID: $pageId, Title: $pageTitle")
            return PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_ID, pageId)
                    putString(ARG_PAGE_TITLE, pageTitle)
                }
            }
        }
    }
}
