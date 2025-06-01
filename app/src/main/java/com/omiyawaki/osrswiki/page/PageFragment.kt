package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log // Ensure android.util.Log is imported
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
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao // Added for direct use
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
    private lateinit var readingListPageDao: ReadingListPageDao // For observing

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null

    private var visualStateCallbackIdCounter: Long = 0
    private val pageActionItemCallback = PageActionItemCallback()
    private var pageStateObserverJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
        }
        L.d("PageFragment onCreate - Args processed: ID: $pageIdArg, Title: $pageTitleArg")
        pageRepository = (requireActivity().applicationContext as OSRSWikiApp).pageRepository
        readingListPageDao = AppDatabase.instance.readingListPageDao() // Initialize DAO
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

        val appDb = AppDatabase.instance // Already have readingListPageDao from onCreate
        pageContentLoader = PageContentLoader(
            context = requireContext().applicationContext,
            pageRepository = pageRepository,
            pageViewModel = pageViewModel,
            readingListPageDao = appDb.readingListPageDao(), // Or use this.readingListPageDao
            offlineObjectDao = appDb.offlineObjectDao(),
            coroutineScope = this.viewLifecycleOwner.lifecycleScope,
            onStateUpdated = {
                if (isAdded && _binding != null) {
                    updateUiFromViewModel()
                    // After main UI state is updated, also re-evaluate and observe save state
                    // This might be called frequently, ensure observeAndRefreshSaveButtonState is idempotent
                    observeAndRefreshSaveButtonState()
                }
            }
        )

        // ... WebView setup as before ...
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

        updateUiFromViewModel() // Initial UI update
        initiatePageLoad(forceNetwork = false)
        observeAndRefreshSaveButtonState() // Start observing save state

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
        // ... (content as before)
        if (_binding == null || !isAdded) {
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
        // ... (content as before)
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
        val currentViewModelPlainTextTitle = pageViewModel.uiState.plainTextTitle
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null)
        updateUiFromViewModel() // This will call refreshSaveButtonState via onStateUpdated after loader acts

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
            if (!forceNetwork && currentViewModelPlainTextTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
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
                plainTextTitle = getString(R.string.title_page_not_specified),
                pageId = null,
                htmlContent = null
            )
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        if (!isAdded || _binding == null) {
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
        // refreshSaveButtonState() // Removed from here; will be driven by observer or specific calls
        L.i("PageFragment UI updated. ViewModel plainTextTitle: '${state.plainTextTitle}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, error: ${state.error != null}")
    }

    private fun observeAndRefreshSaveButtonState() {
        pageStateObserverJob?.cancel() // Cancel previous observer if any

        val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: pageTitleArg?.takeIf { it.isNotBlank() }

        if (plainTextForApi.isNullOrBlank()) {
            Log.e("OSRSWIKI_DEBUG", "observeAndRefreshSaveButtonState - No plain text title. Cannot observe.")
            updateSaveIcon(null) // Update icon to default (unsaved)
            return
        }

        // Construct a PageTitle just for its properties, not for adding to list here
        val tempPageTitle = PageTitle(text = plainTextForApi, wikiSite = WikiSite.OSRS_WIKI)
        Log.e("OSRSWIKI_DEBUG", "observeAndRefreshSaveButtonState - Starting to observe apiTitle '${tempPageTitle.prefixedText}'")


        pageStateObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            // Get the default reading list ID once
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
            ).collectLatest { entry -> // collectLatest will cancel previous collection if a new one starts fast
                Log.e("OSRSWIKI_DEBUG", "observeAndRefreshSaveButtonState - Observed ReadingListPage: $entry")
                updateSaveIcon(entry)
            }
        }
    }

    private fun updateSaveIcon(entry: ReadingListPage?) {
        if (!isAdded || _binding == null) {
            L.v("updateSaveIcon: Fragment not in a state to update UI.")
            return
        }
        val isActuallySavedAndOffline = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isActuallySavedAndOffline))
        Log.e("OSRSWIKI_DEBUG", "updateSaveIcon - Icon updated. IsActuallySavedAndOffline: $isActuallySavedAndOffline for apiTitle: ${entry?.apiTitle ?: pageViewModel.uiState.plainTextTitle ?: pageTitleArg}")
    }


    // This function is now effectively replaced by observeAndRefreshSaveButtonState + updateSaveIcon
    private fun refreshSaveButtonState() {
        // This immediate refresh might still be useful after a direct user action,
        // but the observer will handle the definitive state from DB.
        // For now, let's ensure it's called to get an immediate UI feedback,
        // and the observer will correct it if the background task changes state.
        Log.d("OSRSWIKI_DEBUG", "Legacy refreshSaveButtonState called (will soon be fully replaced by observer)")

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
                    excludedStatus = -1L // Get current state regardless of status for immediate feedback
                )
            }
            updateSaveIcon(entry)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        pageStateObserverJob?.cancel() // Cancel the observer job
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            L.d("WebView destroyed.")
        }
        _binding = null
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
            if (!isAdded || _binding == null) return
            val snackbar = Snackbar.make(binding.root, message, length)
                .setAnchorView(binding.pageActionsTabLayout)
            // ... (snackbar styling) ...
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

            // Log values used to derive plainTextForApi
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
                // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - No plain text page title available for API operations.") // Original OSRSWIKI_DEBUG log
                return
            }
            android.util.Log.e("PFragment_SAVE_TEST", "plainTextForApi IS VALID. Proceeding with save logic.");

            val currentThumb = pageViewModel.uiState.imageUrl
            val currentPageTitle = PageTitle(
                namespace = null,
                text = plainTextForApi,
                wikiSite = WikiSite.OSRS_WIKI,
                thumbUrl = currentThumb,
                description = null,
                displayText = htmlTextForDisplay ?: plainTextForApi
            )
            // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - Action triggered for apiTitle: ${currentPageTitle.prefixedText} (displayTitle: ${currentPageTitle.displayText})") // Original OSRSWIKI_DEBUG log

            val titleForSnackbar = currentPageTitle.prefixedText

            viewLifecycleOwner.lifecycleScope.launch {
                var message: String = getString(R.string.error_generic_save_unsave)
                var existingEntry: ReadingListPage? = null
                try {
                    val readingListDao = AppDatabase.instance.readingListDao()
                    val localReadingListPageDao = AppDatabase.instance.readingListPageDao() // Use local val
                    val defaultList = withContext(Dispatchers.IO) {
                        readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist()
                    }

                    existingEntry = withContext(Dispatchers.IO) {
                        localReadingListPageDao.getPageByListIdAndTitle( // use local val
                            wiki = currentPageTitle.wikiSite,
                            lang = currentPageTitle.wikiSite.languageCode,
                            ns = currentPageTitle.namespace(),
                            apiTitle = currentPageTitle.prefixedText,
                            listId = defaultList.id,
                            excludedStatus = -1L
                        )
                    }
                    // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - DAO Query for apiTitle '${currentPageTitle.prefixedText}', Entry found: ${existingEntry != null}, Offline: ${existingEntry?.offline}, Status: ${existingEntry?.status}") // Original OSRSWIKI_DEBUG log
                    android.util.Log.e("PFragment_SAVE_TEST", "DAO Query for apiTitle '${currentPageTitle.prefixedText}', Entry found: ${existingEntry != null}, Offline: ${existingEntry?.offline}, Status: ${existingEntry?.status}")


                    if (existingEntry != null) {
                        // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - existingEntry is NOT NULL. Offline: ${existingEntry.offline}") // Original OSRSWIKI_DEBUG log
                        android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry IS NOT NULL. Offline: ${existingEntry.offline}, Status: ${existingEntry.status}")
                        if (existingEntry.offline) {
                            // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - Entry found AND IS OFFLINE (or queued). Marking for DELETION of offline files.") // Original OSRSWIKI_DEBUG log
                            android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry.offline IS TRUE. Marking for DELETION.")
                            withContext(Dispatchers.IO) {
                                localReadingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry))
                            }
                            message = "'$titleForSnackbar' offline version will be removed."
                        } else {
                            // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - Entry found but IS NOT OFFLINE. Marking for SAVE/DOWNLOAD.") // Original OSRSWIKI_DEBUG log
                            android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry.offline IS FALSE. Marking for SAVE/DOWNLOAD.")
                            withContext(Dispatchers.IO) {
                                localReadingListPageDao.markPagesForOffline(listOf(existingEntry), offline = true, forcedSave = false)
                            }
                            if (Prefs.isDownloadingReadingListArticlesEnabled) {
                                message = "'$titleForSnackbar' queued for download."
                            } else {
                                message = "'$titleForSnackbar' marked for offline availability."
                            }
                        }
                    } else {
                        // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - Entry NOT found. Adding to list and marking for download.") // Original OSRSWIKI_DEBUG log
                        android.util.Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry IS NULL. Adding new page to list.")
                        val downloadEnabled = Prefs.isDownloadingReadingListArticlesEnabled
                        android.util.Log.e("PFragment_SAVE_TEST", "For new page, Prefs.isDownloadingReadingListArticlesEnabled = $downloadEnabled")
                        val titlesAdded = withContext(Dispatchers.IO) {
                            localReadingListPageDao.addPagesToList(
                                defaultList,
                                listOf(currentPageTitle),
                                downloadEnabled
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
                    // The observer will handle the refreshSaveButtonState implicitly by reacting to DB changes.
                    // Explicit call to refreshSaveButtonState() here might show an intermediate state.
                    // However, for immediate feedback after click, it might be desired. Let's keep it for now.
                    if (isAdded) refreshSaveButtonState() 
                } catch (e: Exception) {
                    // Log.e("OSRSWIKI_DEBUG", "onSaveSelected - Error during save/unsave operation for title '$titleForSnackbar'", e) // Original OSRSWIKI_DEBUG log
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
