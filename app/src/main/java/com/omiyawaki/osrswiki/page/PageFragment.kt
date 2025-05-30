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
import androidx.lifecycle.lifecycleScope // Still potentially useful for other fragment-level coroutines
// import androidx.lifecycle.viewLifecycleOwner // Needed for viewLifecycleOwner.lifecycleScope
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

    // pageViewModel can be initialized here if its constructor is simple
    private val pageViewModel = PageViewModel()
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader // Declare here

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

        // Initialize pageRepository - this is fine here as it doesn't depend on the view
        pageRepository = (requireActivity().applicationContext as OSRSWikiApp).pageRepository

        // DO NOT initialize pageContentLoader here if it uses viewLifecycleOwner.lifecycleScope
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

        // Initialize PageContentLoader HERE, now that viewLifecycleOwner is available
        val appDb = AppDatabase.instance // Get AppDatabase instance
        pageContentLoader = PageContentLoader(
            context = requireContext().applicationContext,
            pageRepository = pageRepository, // pageRepository initialized in onCreate
            pageViewModel = pageViewModel,   // pageViewModel initialized as a property
            readingListPageDao = appDb.readingListPageDao(),
            offlineObjectDao = appDb.offlineObjectDao(),
            coroutineScope = this.getViewLifecycleOwner().lifecycleScope, // NOW THIS IS SAFE
            onStateUpdated = {
                if (isAdded && _binding != null) { // Check isAdded and _binding
                    updateUiFromViewModel()
                }
            }
        )

        // Setup WebView
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

        updateUiFromViewModel() // Initial UI update based on potentially empty/loading ViewModel
        initiatePageLoad(forceNetwork = false) // Now initiate load AFTER pageContentLoader is ready

        binding.errorTextView.setOnClickListener {
            L.i("Retry button clicked. pageIdArg: $pageIdArg, pageTitleArg: $pageTitleArg. Forcing network.")
            initiatePageLoad(forceNetwork = true)
        }
    }

    private fun isDarkMode(): Boolean {
        if (!isAdded) return false // Ensure fragment is added before accessing resources
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setWebViewWidgetBackgroundColor() {
        if (_binding == null || !isAdded) return
        val backgroundColorInt = ContextCompat.getColor(requireContext(), R.color.osrs_parchment_bg)
        binding.pageWebView.setBackgroundColor(backgroundColorInt)
        L.d("Set WebView WIDGET background color for mode (isDark: ${isDarkMode()}) to hex: ${String.format("#%06X", (0xFFFFFF and backgroundColorInt))}")
    }

    private fun applyWebViewStylingAndRevealBody() {
        if (_binding == null || !isAdded || context == null) { // context check is redundant due to requireContext() below
            L.w("applyWebViewStylingAndRevealBody: Binding is null or fragment not added. Skipping.")
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
        // pageContentLoader is now guaranteed to be initialized here
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

        // Ensure UI reflects loading state immediately
        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
        updateUiFromViewModel() // Call this to update progress bar etc.

        if (idToLoad != null) {
            if (!forceNetwork && currentViewModelPageId == idToLoad && contentAlreadyLoaded) {
                L.d("Page with ID '$idToLoad' data already present. Reverting loading state.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false) // Content is already there
                updateUiFromViewModel() // Reflect that loading is done
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
        if (!isAdded || _binding == null) { // Added check for isAdded and binding
            L.w("updateUiFromViewModel: Fragment not in a valid state or binding is null.")
            return
        }
        L.d("PageFragment updateUiFromViewModel. Current state: ${pageViewModel.uiState}")
        val state = pageViewModel.uiState

        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.error?.let { detailedErrorString ->
            L.e("Page load error (technical details): $detailedErrorString")
            binding.errorTextView.text = detailedErrorString
            binding.errorTextView.visibility = View.VISIBLE
            binding.pageWebView.visibility = View.INVISIBLE // Hide WebView on error
        } ?: run {
            binding.errorTextView.visibility = View.GONE
        }

        if (state.isLoading || state.error != null) {
            // If loading or error, ensure webview is invisible if it was previously visible
            // or if it's a fresh load without content yet.
            if (binding.pageWebView.visibility == View.VISIBLE || (state.isLoading && state.htmlContent == null) ) {
                binding.pageWebView.visibility = View.INVISIBLE
            }
            // Load blank page only if loading new content and webview hasn't loaded anything yet
            if (state.isLoading && state.htmlContent == null && binding.pageWebView.url == null) {
                L.d("Loading blank data into WebView as it's a fresh load.")
                val blankHtml = """
                    <!DOCTYPE html><html><head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>body{visibility:hidden;background-color:transparent;}</style>
                    </head><body></body></html>
                """.trimIndent()
                // Load blank data to prepare WebView, but keep it invisible until onPageFinished
                binding.pageWebView.loadData(blankHtml, "text/html", "UTF-8")
            }
        } else { // Successfully loaded, no error, not loading
            state.htmlContent?.let { htmlBodySnippet ->
                L.d("Loading actual HTML content into WebView.")
                // Keep WebView invisible until onPageFinished and styling is applied
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
                val baseUrl = "https://oldschool.runescape.wiki/" // Define your base URL
                binding.pageWebView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
            } ?: run {
                // This case (htmlContent is null but no error and not loading) should ideally not happen if state is consistent.
                L.w("Attempting to display content, but htmlContent is null and not loading/error state.")
                binding.pageWebView.visibility = View.VISIBLE // Or INVISIBLE if showing an error/placeholder
                binding.pageWebView.loadDataWithBaseURL(null, getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
        refreshSaveButtonState() // Refresh save button based on new title/pageId from ViewModel
        L.i("PageFragment UI updated. ViewModel title: '${state.title}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    private fun refreshSaveButtonState() {
        if (_binding == null || !isAdded) { // Simpler check
            L.v("refreshSaveButtonState: Fragment not in a state to update UI.")
            return
        }

        // Use the title from the ViewModel if available (canonical title from API),
        // otherwise fallback to pageTitleArg.
        val titleForCheck = pageViewModel.uiState.title?.takeIf { it.isNotBlank() } ?: pageTitleArg

        if (titleForCheck.isNullOrBlank()) {
            L.w("refreshSaveButtonState: No page title available (ViewModel or Arg). Cannot check save state.")
            binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(false))
            return
        }

        // Assuming titleForCheck is the "prefixed text" / "apiTitle"
        val currentPageTitle = PageTitle(text = titleForCheck, wikiSite = WikiSite.OSRS_WIKI)
        L.d("refreshSaveButtonState: Checking for title '${currentPageTitle.displayText}' (apiTitle: '${currentPageTitle.prefixedText}')")

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
                        apiTitle = currentPageTitle.prefixedText, // Use prefixedText which is the apiTitle
                        listId = defaultList.id
                    ) != null
                } catch (e: Exception) {
                    L.e("Error checking save state for title '${currentPageTitle.displayText}'", e)
                    false
                }
            }
            if (isAdded && _binding != null) { // Check isAdded and _binding again, as this is an async callback
                binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isSaved))
                L.d("Save button state updated. IsSaved: $isSaved for title: ${currentPageTitle.displayText}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Important: Clear WebView resources
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading()
            // The webview must be removed from the view hierarchy before calling destroy to prevent memory leak.
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            L.d("WebView destroyed.")
        }
        _binding = null // Crucial for preventing memory leaks with ViewBinding
        L.d("PageFragment onDestroyView, _binding set to null.")
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
            if (!isAdded || _binding == null) return // Simpler check

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

            // Use the title from the ViewModel if available and loaded, otherwise pageTitleArg
            val currentViewModelTitle = pageViewModel.uiState.title?.takeIf { it.isNotBlank() && pageViewModel.uiState.htmlContent != null }
            val titleForSaving = currentViewModelTitle ?: pageTitleArg

            if (titleForSaving.isNullOrBlank()) {
                showThemedSnackbar(getString(R.string.cannot_save_page_no_title), Snackbar.LENGTH_SHORT)
                L.w("Save action: No page title available (ViewModel or Arg is blank).")
                return
            }

            // Assuming titleForSaving is the "prefixed text" / "apiTitle"
            val currentPageTitle = PageTitle(text = titleForSaving, wikiSite = WikiSite.OSRS_WIKI)
            L.d("Save action triggered for page: ${currentPageTitle.displayText} (apiTitle: ${currentPageTitle.prefixedText})")

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
                            apiTitle = currentPageTitle.prefixedText, // Use prefixedText
                            listId = defaultList.id
                        )
                    }

                    if (existingEntry != null) {
                        // Page exists, so mark for deletion (of offline files, and then potentially from list if logic implies)
                        withContext(Dispatchers.IO) {
                            // This marks for offline file deletion; actual list removal is separate
                            readingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry))
                        }
                        L.i("Page '${currentPageTitle.displayText}' marked for offline file deletion from list '${defaultList.title}'.")
                        message = "'${currentPageTitle.displayText}' removed from saved pages." // This message might be misleading if page stays in list
                        // Consider: "Offline version of '${currentPageTitle.displayText}' will be removed."
                    } else {
                        // Page doesn't exist in list, so add it
                        val downloadEnabled = Prefs.isDownloadingReadingListArticlesEnabled
                        val titlesAdded = withContext(Dispatchers.IO) {
                            readingListPageDao.addPagesToList(
                                defaultList,
                                listOf(currentPageTitle),
                                downloadEnabled
                            )
                        }
                        if (titlesAdded.isNotEmpty()) {
                            L.i("Page '${currentPageTitle.displayText}' added to list '${defaultList.title}'.")
                            if (downloadEnabled) {
                                message = "'${currentPageTitle.displayText}' saved and queued for download."
                            } else {
                                message = "'${currentPageTitle.displayText}' saved to reading list."
                            }
                        } else {
                            L.w("Page '${currentPageTitle.displayText}' was not added. It might already exist or an error occurred.")
                            message = "Page could not be saved (may already exist or error)."
                        }
                    }
                    if (isAdded) refreshSaveButtonState() // Refresh button after DB operation
                } catch (e: Exception) {
                    L.e("Error during save/unsave operation for title '${currentPageTitle.displayText}'", e)
                    // message already set to generic error
                }
                if(isAdded && _binding != null) showThemedSnackbar(message) // Show feedback
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