package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
// Removed: import android.webkit.WebViewClient // We will use our AppWebViewClient
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.action.PageActionItem
// New imports for link handling
import com.omiyawaki.osrswiki.page.LinkHandler
import com.omiyawaki.osrswiki.page.PageLinkHandler
import com.omiyawaki.osrswiki.page.AppWebViewClient
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private val pageViewModel = PageViewModel()
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var readingListPageDao: ReadingListPageDao

    // For link handling
    private lateinit var linkHandler: LinkHandler

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null

    private val pageActionItemCallback = PageActionItemCallback()
    private var pageStateObserverJob: Job? = null

    private val WEBVIEW_DEBUG_TAG = "PFragment_WebViewDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
        }
        Log.d(WEBVIEW_DEBUG_TAG,"onCreate - Args processed: ID: $pageIdArg, Title: $pageTitleArg")
        pageRepository = (requireActivity().applicationContext as OSRSWikiApp).pageRepository
        readingListPageDao = AppDatabase.instance.readingListPageDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(WEBVIEW_DEBUG_TAG,"onCreateView")
        _binding = FragmentPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(WEBVIEW_DEBUG_TAG,"onViewCreated. Page ID: $pageIdArg, Page Title: $pageTitleArg")

        val appDb = AppDatabase.instance
        pageContentLoader = PageContentLoader(
            context = requireContext().applicationContext,
            pageRepository = pageRepository,
            pageViewModel = pageViewModel,
            readingListPageDao = appDb.readingListPageDao(),
            offlineObjectDao = appDb.offlineObjectDao(),
            coroutineScope = this.viewLifecycleOwner.lifecycleScope,
            onStateUpdated = {
                if (isAdded && _binding != null) {
                    updateUiFromViewModel()
                    observeAndRefreshSaveButtonState()
                }
            }
        )

        // Initialize LinkHandler and AppWebViewClient
        linkHandler = PageLinkHandler(requireContext())

        binding.pageWebView.webViewClient = object : AppWebViewClient(linkHandler) {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url) // Call super for base AppWebViewClient behavior (e.g. logging)
                Log.d(WEBVIEW_DEBUG_TAG, "WebView onPageCommitVisible for URL: $url. Making WebView visible and applying styles.")
                if (isAdded && _binding != null) {
                    binding.pageWebView.visibility = View.VISIBLE
                    applyWebViewStylingAndRevealBody()
                } else {
                    Log.w(WEBVIEW_DEBUG_TAG, "onPageCommitVisible: Fragment not added or binding null.")
                }
            }

            @SuppressLint("RequiresApi") // Kept from original, ensure it's still relevant
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url) // Call super for base AppWebViewClient behavior (e.g. logging)
                Log.d(WEBVIEW_DEBUG_TAG, "WebView onPageFinished for URL: $url. Current WebView visibility: ${binding.pageWebView?.visibility}")
                if (isAdded && _binding != null) {
                    if (binding.pageWebView.visibility != View.VISIBLE) {
                        Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished: WebView not yet visible. Making visible and applying styles.")
                        binding.pageWebView.visibility = View.VISIBLE
                        applyWebViewStylingAndRevealBody()
                    }
                    else if (url != null && !url.startsWith("data:") && !url.equals("about:blank", ignoreCase = true)) {
                        Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished: Online URL, WebView already visible. Re-applying styles.")
                        applyWebViewStylingAndRevealBody()
                    }
                } else {
                    Log.w(WEBVIEW_DEBUG_TAG, "onPageFinished: Fragment not added or binding null.")
                }
            }
            // Note: onReceivedError and onReceivedHttpError are already handled (logged) by the base AppWebViewClient.
            // If more specific error handling is needed in PageFragment, override them here too.
        }

        binding.pageWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.i("WebViewConsole", "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}")
                }
                return true
            }
        }
        binding.pageWebView.settings.javaScriptEnabled = true
        setWebViewWidgetBackgroundColor()

        binding.pageActionsTabLayout.callback = pageActionItemCallback
        binding.pageActionsTabLayout.update()

        updateUiFromViewModel()
        initiatePageLoad(forceNetwork = false)
        observeAndRefreshSaveButtonState()

        binding.errorTextView.setOnClickListener {
            Log.i(WEBVIEW_DEBUG_TAG, "Retry button clicked. pageIdArg: $pageIdArg, pageTitleArg: $pageTitleArg. Forcing network.")
            initiatePageLoad(forceNetwork = true)
        }
    }

    private fun isDarkMode(): Boolean {
        if (!isAdded) return false
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setWebViewWidgetBackgroundColor() {
        if (_binding == null || !isAdded) return
        binding.pageWebView.setBackgroundColor(Color.TRANSPARENT)
        Log.d(WEBVIEW_DEBUG_TAG, "Set WebView WIDGET background to TRANSPARENT.")
    }

    private fun applyWebViewStylingAndRevealBody() {
        if (_binding == null || !isAdded) {
            Log.w(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody: Binding is null or fragment not added. Skipping.")
            return
        }
        if (binding.pageWebView.visibility != View.VISIBLE) {
            Log.w(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody: WebView widget is INVISIBLE. Making it VISIBLE first.")
            binding.pageWebView.visibility = View.VISIBLE
        }

        Log.d(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody ENTERED.")
        val cssString: String
        try {
            cssString = requireContext().assets.open("styles/wiki_content.css").bufferedReader().use { it.readText() }
            Log.d(WEBVIEW_DEBUG_TAG, "Successfully read wiki_content.css from assets.")
        } catch (e: IOException) {
            Log.e(WEBVIEW_DEBUG_TAG, "Error reading wiki_content.css from assets", e)
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
                            console.log('OSRSWikiApp: Style element appended to head.');
                        } else {
                            console.error('OSRSWikiApp: Could not find head to inject CSS.');
                            return 'Error: No head element found.';
                        }
                    }
                    style.innerHTML = '${escapedCssString}';
                    console.log('OSRSWikiApp: styles/wiki_content.css injected/updated.');
                    return 'CSS Injected';
                })();
            """.trimIndent()

        Log.d(WEBVIEW_DEBUG_TAG, "Evaluating JS for CSS injection.")
        binding.pageWebView.evaluateJavascript(injectCssJs) { result ->
            Log.d(WEBVIEW_DEBUG_TAG, "JS for CSS injection evaluated. Result: $result")
            if (result != null && !result.contains("Error: No head element found.")) {
                val themeClass = if (isDarkMode()) "theme-dark" else "theme-light"
                val applyThemeAndRevealBodyJs = """
                        (function() {
                            if (!document.body) {
                                console.error('OSRSWikiApp: document.body not ready.');
                                return 'Error: No body element found.';
                            }
                            document.body.classList.remove('theme-light', 'theme-dark');
                            document.body.classList.add('$themeClass');
                            document.body.style.visibility = 'visible'; // Make HTML body visible
                            console.log('OSRSWikiApp: Applied theme class (' + '$themeClass' + ') and set body.style.visibility to visible.');
                            return 'Theme and visibility applied.';
                        })();
                    """.trimIndent()
                Log.d(WEBVIEW_DEBUG_TAG, "Evaluating JS for theme and body visibility.")
                binding.pageWebView.evaluateJavascript(applyThemeAndRevealBodyJs) { themeResult ->
                    Log.d(WEBVIEW_DEBUG_TAG, "JS for theme and body visibility evaluated. Result: $themeResult")
                }
            } else {
                val justRevealBodyJs = """
                       (function() {
                           if (document.body) {
                               document.body.style.visibility = 'visible';
                               console.log('OSRSWikiApp: (Fallback due to CSS injection issue) Set body.style.visibility to visible.');
                               return 'Body visibility set (fallback).';
                           } else {
                               console.error('OSRSWikiApp: (Fallback due to CSS injection issue) document.body still not ready.');
                               return 'Error: No body element found (fallback).';
                           }
                       })();
                   """.trimIndent()
                Log.w(WEBVIEW_DEBUG_TAG, "CSS injection may have failed. Attempting fallback body reveal only.")
                binding.pageWebView.evaluateJavascript(justRevealBodyJs) { fallbackResult ->
                    Log.d(WEBVIEW_DEBUG_TAG, "JS for fallback body visibility evaluated. Result: $fallbackResult")
                }
            }
        }
        Log.d(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody EXITED (JS evaluations are asynchronous).")
    }


    private fun initiatePageLoad(forceNetwork: Boolean = false) {
        val currentIdToLoadArg = pageIdArg
        val currentTitleToLoadArg = pageTitleArg
        var idToLoad: Int? = null

        if (!currentIdToLoadArg.isNullOrBlank()) {
            try {
                idToLoad = currentIdToLoadArg.toInt()
            } catch (e: NumberFormatException) {
                Log.w(WEBVIEW_DEBUG_TAG, "currentIdToLoadArg '$currentIdToLoadArg' is not a valid integer. Will try title.")
                idToLoad = null
            }
        }

        val currentViewModelPageId: Int? = pageViewModel.uiState.pageId
        val currentViewModelPlainTextTitle = pageViewModel.uiState.plainTextTitle
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
        updateUiFromViewModel()

        if (idToLoad != null) {
            if (!forceNetwork && currentViewModelPageId == idToLoad && contentAlreadyLoaded) {
                Log.d(WEBVIEW_DEBUG_TAG, "Page with ID '$idToLoad' data already present. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                Log.i(WEBVIEW_DEBUG_TAG, "Requesting to load page by ID: $idToLoad (Title arg was: '$currentTitleToLoadArg')")
                pageContentLoader.loadPageById(idToLoad, currentTitleToLoadArg, forceNetwork)
            }
        } else if (!currentTitleToLoadArg.isNullOrBlank()) {
            if (!forceNetwork && currentViewModelPlainTextTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
                Log.d(WEBVIEW_DEBUG_TAG, "Page with title '$currentTitleToLoadArg' data already present. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                Log.i(WEBVIEW_DEBUG_TAG, "Requesting to load page by title: '$currentTitleToLoadArg' (ID arg was: '$currentIdToLoadArg')")
                pageContentLoader.loadPageByTitle(currentTitleToLoadArg, forceNetwork)
            }
        } else {
            Log.e(WEBVIEW_DEBUG_TAG, "Cannot load page: No valid pageId or pageTitle provided. pageIdArg: '$currentIdToLoadArg', pageTitleArg: '$currentTitleToLoadArg'")
            pageViewModel.uiState = PageUiState(
                isLoading = false,
                error = getString(R.string.error_no_article_identifier),
                title = getString(R.string.title_page_not_specified),
                plainTextTitle = getString(R.string.title_page_not_specified),
                pageId = null,
                htmlContent = null
            )
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        if (!isAdded || !isVisible || _binding == null) { // Added !isVisible check
            Log.w(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Fragment not in a valid state (not added, not visible, or binding is null).")
            return
        }
        Log.d(WEBVIEW_DEBUG_TAG, "PageFragment updateUiFromViewModel. Current state: ${pageViewModel.uiState}")
        val state = pageViewModel.uiState

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.error?.let { detailedErrorString ->
            Log.e(WEBVIEW_DEBUG_TAG, "Page load error (technical details): $detailedErrorString")
            binding.errorTextView.text = detailedErrorString
            binding.errorTextView.visibility = View.VISIBLE
            binding.pageWebView.visibility = View.INVISIBLE
        } ?: run {
            binding.errorTextView.visibility = View.GONE
        }

        if (state.isLoading || state.error != null) {
            if (binding.pageWebView.visibility == View.VISIBLE || (state.isLoading && state.htmlContent == null) ) {
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Setting WebView WIDGET to INVISIBLE because isLoading or error.")
                binding.pageWebView.visibility = View.INVISIBLE
            }
            if (state.isLoading && state.htmlContent == null && (binding.pageWebView.url == null || binding.pageWebView.url == "about:blank")) {
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Loading blank data into WebView as it's a fresh load.")
                val blankHtml = """
                        <!DOCTYPE html><html><head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>html, body { background-color: transparent !important; visibility:hidden !important; }</style>
                        </head><body></body></html>
                    """.trimIndent()
                binding.pageWebView.loadData(blankHtml, "text/html", "UTF-8")
            }
        } else {
            state.htmlContent?.let { htmlBodySnippet ->
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Preparing to load actual HTML content. Current WebView visibility: ${binding.pageWebView.visibility}")
                binding.pageWebView.visibility = View.INVISIBLE
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Set WebView WIDGET to INVISIBLE before loadDataWithBaseURL.")

                val currentIsDarkMode = isDarkMode()
                val themeSpecificParchmentColorRes = if (currentIsDarkMode) {
                    R.color.osrs_parchment_bg_dark_theme_value
                } else {
                    R.color.osrs_parchment_bg
                }
                val backgroundColorInt = ContextCompat.getColor(requireContext(), themeSpecificParchmentColorRes)
                val backgroundColorHex = String.format("#%06X", (0xFFFFFF and backgroundColorInt))

                val finalHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>${state.title ?: pageTitleArg ?: "OSRS Wiki"}</title>
                            <style>
                                /* Immediately set background and hide body to prevent FUOC */
                                html { background-color: $backgroundColorHex !important; }
                                body {
                                    visibility: hidden; /* REMOVED !important */
                                    background-color: $backgroundColorHex !important;
                                }
                            </style>
                        </head>
                        <body> ${htmlBodySnippet}
                        </body>
                        </html>
                    """.trimIndent()
                val baseUrl = WikiSite.OSRS_WIKI.url()
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Using initial background hex: $backgroundColorHex for isDark: $currentIsDarkMode")
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Calling loadDataWithBaseURL. WebView visibility: ${binding.pageWebView.visibility}")
                binding.pageWebView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: loadDataWithBaseURL called. Waiting for WebViewClient callbacks to make visible and style.")

            } ?: run {
                Log.w(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: htmlContent is null, but not loading and no error. Displaying 'content unavailable'.")
                binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadDataWithBaseURL(null, getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
        Log.i(WEBVIEW_DEBUG_TAG, "PageFragment UI updated. ViewModel plainTextTitle: '${state.plainTextTitle}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}, htmlContent isNull: ${state.htmlContent == null}")
    }

    private fun observeAndRefreshSaveButtonState() {
        // ... (content of this method remains unchanged) ...
        pageStateObserverJob?.cancel()

        val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: pageTitleArg?.takeIf { it.isNotBlank() }

        if (plainTextForApi.isNullOrBlank()) {
            Log.e("PFragment_SAVE_TEST", "observeAndRefreshSaveButtonState - No plain text title. Cannot observe.")
            updateSaveIcon(null)
            return
        }

        val tempPageTitle = PageTitle(text = plainTextForApi, wikiSite = WikiSite.OSRS_WIKI)
        Log.e("PFragment_SAVE_TEST", "observeAndRefreshSaveButtonState - Starting to observe apiTitle '${tempPageTitle.prefixedText}'")


        pageStateObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            val defaultListId = withContext(Dispatchers.IO) {
                val readingListDao = AppDatabase.instance.readingListDao()
                (readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist()).id
            }

            readingListPageDao.observePageByListIdAndTitle(
                wiki = tempPageTitle.wikiSite,
                lang = tempPageTitle.wikiSite.languageCode,
                ns = tempPageTitle.namespace(),
                apiTitle = tempPageTitle.prefixedText,
                listId = defaultListId
            ).collectLatest { entry ->
                Log.e("PFragment_SAVE_TEST", "observeAndRefreshSaveButtonState - Observed ReadingListPage: $entry")
                updateSaveIcon(entry)
            }
        }
    }

    private fun updateSaveIcon(entry: ReadingListPage?) {
        // ... (content of this method remains unchanged) ...
        if (!isAdded || _binding == null) {
            L.v("updateSaveIcon: Fragment not in a state to update UI.")
            return
        }
        val isActuallySavedAndOffline = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isActuallySavedAndOffline))
        Log.e("PFragment_SAVE_TEST", "updateSaveIcon - Icon updated. IsActuallySavedAndOffline: $isActuallySavedAndOffline for apiTitle: ${entry?.apiTitle ?: pageViewModel.uiState.plainTextTitle ?: pageTitleArg}")
    }

    private fun refreshSaveButtonState() {
        // ... (content of this method remains unchanged) ...
        Log.d("PFragment_SAVE_TEST", "Legacy refreshSaveButtonState called (will soon be fully replaced by observer)")
        val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: pageTitleArg?.takeIf { it.isNotBlank() }

        if (plainTextForApi.isNullOrBlank()) {
            updateSaveIcon(null)
            return
        }
        val tempPageTitle = PageTitle(text = plainTextForApi, wikiSite = WikiSite.OSRS_WIKI)

        viewLifecycleOwner.lifecycleScope.launch {
            val defaultListId = withContext(Dispatchers.IO) {
                val readingListDao = AppDatabase.instance.readingListDao()
                (readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist()).id
            }
            val entry = withContext(Dispatchers.IO) {
                readingListPageDao.getPageByListIdAndTitle(
                    wiki = tempPageTitle.wikiSite,
                    lang = tempPageTitle.wikiSite.languageCode,
                    ns = tempPageTitle.namespace(),
                    apiTitle = tempPageTitle.prefixedText,
                    listId = defaultListId,
                    excludedStatus = -1L // Assuming -1L means don't exclude based on status for this check
                )
            }
            updateSaveIcon(entry)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageStateObserverJob?.cancel()
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            Log.d(WEBVIEW_DEBUG_TAG, "WebView destroyed.")
        }
        _binding = null
        Log.d(WEBVIEW_DEBUG_TAG, "PageFragment onDestroyView, _binding set to null.")
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

    private inner class PageActionItemCallback : PageActionItem.Callback {
        // ... (content of this inner class remains unchanged) ...
        private fun showThemedSnackbar(message: String, length: Int = Snackbar.LENGTH_LONG) {
            if (!isAdded || _binding == null) return
            val snackbar = Snackbar.make(binding.root, message, length)
                .setAnchorView(binding.pageActionsTabLayout)
            val snackbarBgColorResId: Int
            val snackbarTextColorResId: Int

            if (isDarkMode()) {
                snackbarBgColorResId = R.color.snackbar_background_light_appearance
                snackbarTextColorResId = R.color.snackbar_text_color_light_appearance
            } else {
                snackbarBgColorResId = R.color.snackbar_background_dark_appearance
                snackbarTextColorResId = R.color.snackbar_text_color_dark_appearance
            }

            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), snackbarBgColorResId))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), snackbarTextColorResId))
            snackbar.show()
        }

        override fun onSaveSelected() {
            if (!isAdded || _binding == null) return
            android.util.Log.e("PFragment_SAVE_TEST", "onSaveSelected --- ENTRY POINT REACHED ---");

            val pvmPlainTextTitle = pageViewModel.uiState.plainTextTitle
            val pvmHtmlContent = pageViewModel.uiState.htmlContent
            val pTitleArg = pageTitleArg
            android.util.Log.e("PFragment_SAVE_TEST", "Values for plainTextForApi: pvmPlainTextTitle='${pvmPlainTextTitle}', pvmHtmlContent is " + (if(pvmHtmlContent != null) "NOT NULL" else "NULL") + ", pageTitleArg='${pTitleArg}'");

            val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() && pageViewModel.uiState.htmlContent != null }
                ?: pageTitleArg?.takeIf { it.isNotBlank() }
            android.util.Log.e("PFragment_SAVE_TEST", "Derived plainTextForApi: '${plainTextForApi}'");

            val htmlTextForDisplay = pageViewModel.uiState.title?.takeIf { it.isNotBlank() && pageViewModel.uiState.htmlContent != null }
                ?: plainTextForApi

            if (plainTextForApi.isNullOrBlank()) {
                android.util.Log.e("PFragment_SAVE_TEST", "plainTextForApi IS NULL OR BLANK. Will show snackbar and return.");
                showThemedSnackbar(getString(R.string.cannot_save_page_no_title), Snackbar.LENGTH_SHORT)
                return
            }
            android.util.Log.e("PFragment_SAVE_TEST", "plainTextForApi IS VALID. Proceeding with save logic.");

            val currentThumb = pageViewModel.uiState.imageUrl
            val currentPageTitle = PageTitle(
                namespace = null, // Assuming default namespace for articles
                text = plainTextForApi,
                wikiSite = WikiSite.OSRS_WIKI, // Ensure this is the correct WikiSite instance
                thumbUrl = currentThumb,
                description = null, // You might get this from PageViewModel if available
                displayText = htmlTextForDisplay ?: plainTextForApi
            )

            val titleForSnackbar = currentPageTitle.prefixedText

            viewLifecycleOwner.lifecycleScope.launch {
                var message: String = getString(R.string.error_generic_save_unsave)
                var existingEntry: ReadingListPage? = null
                try {
                    val readingListDao = AppDatabase.instance.readingListDao()
                    val localReadingListPageDao = AppDatabase.instance.readingListPageDao() // This is the same as readingListPageDao above
                    val defaultList = withContext(Dispatchers.IO) {
                        readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist()
                    }

                    existingEntry = withContext(Dispatchers.IO) {
                        localReadingListPageDao.getPageByListIdAndTitle(
                            wiki = currentPageTitle.wikiSite,
                            lang = currentPageTitle.wikiSite.languageCode,
                            ns = currentPageTitle.namespace(),
                            apiTitle = currentPageTitle.prefixedText,
                            listId = defaultList.id,
                            excludedStatus = -1L // Assuming -1L means don't exclude based on status for this check
                        )
                    }
                    android.util.Log.e("PFragment_SAVE_TEST", "DAO Query for apiTitle '${currentPageTitle.prefixedText}', Entry found: ${existingEntry != null}, Offline: ${existingEntry?.offline}, Status: ${existingEntry?.status}")


                    if (existingEntry != null) {
                        android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry IS NOT NULL. Offline: ${existingEntry.offline}, Status: ${existingEntry.status}")
                        if (existingEntry.offline) { // Assuming 'offline' means it's saved and available offline
                            android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry.offline IS TRUE. Marking for DELETION.")
                            withContext(Dispatchers.IO) {
                                localReadingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry))
                            }
                            message = "'$titleForSnackbar' offline version will be removed."
                        } else { // It's in a list, but not marked as offline, or download failed/pending. Mark for save.
                            android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry.offline IS FALSE. Marking for SAVE/DOWNLOAD.")
                            withContext(Dispatchers.IO) {
                                // Assuming this method correctly updates status for download/offline availability
                                localReadingListPageDao.markPagesForOffline(listOf(existingEntry), offline = true, forcedSave = false)
                            }
                            if (Prefs.isDownloadingReadingListArticlesEnabled) { // Check user preference
                                message = "'$titleForSnackbar' queued for download."
                            } else {
                                message = "'$titleForSnackbar' marked for offline availability."
                            }
                        }
                    } else { // existingEntry IS NULL
                        android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry IS NULL. Adding new page to list.")
                        val downloadEnabled = Prefs.isDownloadingReadingListArticlesEnabled
                        android.util.Log.e("PFragment_SAVE_TEST", "For new page, Prefs.isDownloadingReadingListArticlesEnabled = $downloadEnabled")
                        val titlesAdded = withContext(Dispatchers.IO) {
                            localReadingListPageDao.addPagesToList(
                                defaultList,
                                listOf(currentPageTitle),
                                downloadEnabled // This flag should control if it's also queued for download
                            )
                        }
                        if (titlesAdded.isNotEmpty()) {
                            L.i("Page '$titleForSnackbar' added to list '${defaultList.title}'.")
                            if (downloadEnabled) {
                                message = "'$titleForSnackbar' saved and queued for download."
                            } else {
                                message = "'$titleForSnackbar' saved to reading list."
                            }
                        } else {
                            L.w("Page '$titleForSnackbar' was not added. It might already exist or an error occurred.")
                            message = "Page '$titleForSnackbar' could not be saved (may already exist or error)."
                        }
                    }
                    if(isAdded) refreshSaveButtonState() // Refresh icon immediately based on intended state
                } catch (e: Exception) {
                    android.util.Log.e("PFragment_SAVE_TEST", "Error during save/unsave for '$titleForSnackbar'", e)
                }
                if(isAdded && _binding != null) showThemedSnackbar(message)
            }
        }

        override fun onFindInArticleSelected() {
            L.d("Find in article selected - Not yet implemented")
            if (isAdded && _binding != null) showThemedSnackbar("Find in page: Not yet implemented.", Snackbar.LENGTH_SHORT)
        }

        override fun onThemeSelected() {
            L.d("Theme selected - Not yet implemented")
            if (isAdded && _binding != null) showThemedSnackbar("Appearance: Not yet implemented.", Snackbar.LENGTH_SHORT)
        }

        override fun onContentsSelected() {
            L.d("Contents selected - Not yet implemented")
            if (isAdded && _binding != null) showThemedSnackbar("Contents: Not yet implemented.", Snackbar.LENGTH_SHORT)
        }
    }
}
