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
import com.google.android.material.snackbar.Snackbar
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.page.action.PageActionItem
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val pageActionItemCallback = PageActionItemCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        L.d("PageFragment onViewCreated. Page ID: $pageIdArg, Page Title: $pageTitleArg")

        binding.pageWebView.webViewClient = object : WebViewClient() {
            @SuppressLint("RequiresApi")
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                L.d("WebView onPageFinished for URL: $url.")
                applyWebViewStylingAndRevealBody()
                if (view == null || _binding == null || !isAdded) {
                    L.w("onPageFinished: WebView, binding, or fragment not in a valid state.")
                    return
                }
                if (!pageViewModel.uiState.isLoading && pageViewModel.uiState.error == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val currentCallbackId = ++visualStateCallbackIdCounter
                        L.d("Requesting postVisualStateCallback (ID: $currentCallbackId)")
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

        binding.pageActionsTabLayout.callback = pageActionItemCallback
        binding.pageActionsTabLayout.update()

        updateUiFromViewModel()
        initiatePageLoad(forceNetwork = false)

        binding.errorTextView.setOnClickListener {
            L.i("Retry button clicked. pageIdArg: $pageIdArg, pageTitleArg: $pageTitleArg. Forcing network.")
            initiatePageLoad(forceNetwork = true)
        }
    }

    private fun isDarkMode(): Boolean {
        if (!isAdded) return false
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setWebViewWidgetBackgroundColor() {
        if (_binding == null || !isAdded) return
        val backgroundColorInt = ContextCompat.getColor(requireContext(), R.color.osrs_parchment_bg)
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
                     if (head) { head.appendChild(style); } else { console.error('OSRSWikiApp: Could not find head to inject CSS.'); return; }
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
                     if (!document.body) { console.error('OSRSWikiApp: document.body not ready.'); return; }
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

        val currentViewModelPageId: Int? = pageViewModel.uiState.pageId 
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
            if (binding.pageWebView.visibility == View.VISIBLE || (state.isLoading && state.htmlContent == null) ) {
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
            state.htmlContent?.let { htmlBodySnippet ->
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
        refreshSaveButtonState()
        L.i("PageFragment UI updated. ViewModel title: '${state.title}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    private fun refreshSaveButtonState() {
        if (_binding == null || !isAdded || context == null) {
            L.v("refreshSaveButtonState: Fragment not in a state to update UI.")
            return
        }

        val currentTitleForSaving = pageTitleArg
        if (currentTitleForSaving.isNullOrBlank()) {
            L.w("refreshSaveButtonState: No page title available. Cannot check save state.")
            binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(false))
            return
        }

        val currentPageTitle = PageTitle(text = currentTitleForSaving, wikiSite = WikiSite.OSRS_WIKI)
        L.d("refreshSaveButtonState: Checking for title '${currentPageTitle.displayText}'")

        viewLifecycleOwner.lifecycleScope.launch {
            val isSaved = withContext(Dispatchers.IO) {
                try {
                    val readingListDao = AppDatabase.instance.readingListDao()
                    val readingListPageDao = AppDatabase.instance.readingListPageDao()
                    val defaultList = readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist()
                    
                    readingListPageDao.getPageByListIdAndTitle(
                        wiki = currentPageTitle.wikiSite,
                        lang = currentPageTitle.wikiSite.languageCode,
                        ns = currentPageTitle.namespace(),
                        apiTitle = currentPageTitle.prefixedText,
                        listId = defaultList.id
                    ) != null
                } catch (e: Exception) {
                    L.e("Error checking save state for title '${currentPageTitle.displayText}'", e)
                    false 
                }
            }
            if (isAdded && _binding != null) {
                 binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isSaved))
                 L.d("Save button state updated. IsSaved: $isSaved for title: ${currentPageTitle.displayText}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
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

    private inner class PageActionItemCallback : PageActionItem.Callback {
        private fun showThemedSnackbar(message: String, length: Int = Snackbar.LENGTH_LONG) {
            if (!isAdded || _binding == null || context == null) return

            val snackbar = Snackbar.make(binding.root, message, length)
                .setAnchorView(binding.pageActionsTabLayout)

            val snackbarBgColorResId: Int
            val snackbarTextColorResId: Int

            if (isDarkMode()) { // Current app theme is Dark, Snackbar uses Light Theme appearance
                snackbarBgColorResId = R.color.snackbar_background_light_appearance // e.g., your light parchment #E2DBC8
                snackbarTextColorResId = R.color.snackbar_text_color_light_appearance  // e.g., your dark text for light bg #3A2E1C
            } else { // Current app theme is Light, Snackbar uses Dark Theme appearance
                snackbarBgColorResId = R.color.snackbar_background_dark_appearance  // e.g., your dark bg #28221d
                snackbarTextColorResId = R.color.snackbar_text_color_dark_appearance   // e.g., your light text for dark bg #f4eaea
            }

            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), snackbarBgColorResId))
            snackbar.setTextColor(ContextCompat.getColor(requireContext(), snackbarTextColorResId))
            snackbar.show()
        }

        override fun onSaveSelected() {
            if (!isAdded || _binding == null || context == null) return

            val currentTitleForSaving = pageTitleArg
            if (currentTitleForSaving.isNullOrBlank()) {
                showThemedSnackbar(getString(R.string.cannot_save_page_no_title), Snackbar.LENGTH_SHORT)
                L.w("Save action: No page title available (pageTitleArg is blank).")
                return
            }

            val currentPageTitle = PageTitle(text = currentTitleForSaving, wikiSite = WikiSite.OSRS_WIKI)
            L.d("Save action triggered for page: ${currentPageTitle.displayText} (prefixed: ${currentPageTitle.prefixedText})")

            viewLifecycleOwner.lifecycleScope.launch {
                var message: String = getString(R.string.error_generic_save_unsave)
                try {
                    val readingListDao = AppDatabase.instance.readingListDao()
                    val readingListPageDao = AppDatabase.instance.readingListPageDao()
                    val defaultList = withContext(Dispatchers.IO) {
                         readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist()
                    }

                    val existingEntry = withContext(Dispatchers.IO) {
                        readingListPageDao.getPageByListIdAndTitle(
                            wiki = currentPageTitle.wikiSite,
                            lang = currentPageTitle.wikiSite.languageCode,
                            ns = currentPageTitle.namespace(),
                            apiTitle = currentPageTitle.prefixedText,
                            listId = defaultList.id
                        )
                    }

                    if (existingEntry != null) {
                        withContext(Dispatchers.IO) {
                            readingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry))
                            L.i("Page '${currentPageTitle.displayText}' marked for deletion from list '${defaultList.title}'.")
                        }
                        message = "'${currentPageTitle.displayText}' removed from saved pages."
                    } else { 
                        val titlesAdded = withContext(Dispatchers.IO) {
                            readingListPageDao.addPagesToList(defaultList, listOf(currentPageTitle), Prefs.isDownloadingReadingListArticlesEnabled)
                        }
                        if (titlesAdded.isNotEmpty()) {
                            L.i("Page '${currentPageTitle.displayText}' added to list '${defaultList.title}'.")
                            message = "'${currentPageTitle.displayText}' saved."
                        } else {
                            L.w("Page '${currentPageTitle.displayText}' was not added. It might already exist in DB or an error occurred.")
                            message = "Page could not be saved (may already exist or error)."
                        }
                    }
                    if (isAdded) refreshSaveButtonState()
                } catch (e: Exception) {
                    L.e("Error during save/unsave operation for title '${currentPageTitle.displayText}'", e)
                    // message is already set to generic error string by default
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
