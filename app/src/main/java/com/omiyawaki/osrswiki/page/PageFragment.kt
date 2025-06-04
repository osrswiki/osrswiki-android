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
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.common.models.PageTitle as CommonPageTitle // Alias to avoid confusion
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.action.PageActionItem
import com.omiyawaki.osrswiki.page.tabs.PageBackStackItem
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
import java.util.Date

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var pageViewModel: PageViewModel
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var readingListPageDao: ReadingListPageDao
    private lateinit var pageLinkHandler: PageLinkHandler

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null

    private val pageActionItemCallback = PageActionItemCallback()
    private var pageStateObserverJob: Job? = null

    private val WEBVIEW_DEBUG_TAG = "PFragment_WebViewDebug"
    private val HISTORY_DEBUG_TAG = "PFragment_HistoryDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
        }
        Log.d(WEBVIEW_DEBUG_TAG,"onCreate - Args processed: ID: $pageIdArg, Title: $pageTitleArg")
        pageViewModel = PageViewModel() // Consider using ViewModelProvider
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

        pageLinkHandler = PageLinkHandler(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            pageRepository,
            pageViewModel
        )

        binding.pageWebView.webViewClient = object : AppWebViewClient(pageLinkHandler) {
            @RequiresApi(Build.VERSION_CODES.M)
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                Log.d(WEBVIEW_DEBUG_TAG, "WebView onPageCommitVisible for URL: $url. Current page isOffline: ${pageViewModel.uiState.isCurrentlyOffline}")
                if (isAdded && _binding != null && pageViewModel.uiState.isCurrentlyOffline) {
                    Log.d(WEBVIEW_DEBUG_TAG, "onPageCommitVisible: OFFLINE page detected. Applying styles and revealing.")
                    val wasAlreadyVisible = binding.pageWebView.visibility == View.VISIBLE
                    if (!wasAlreadyVisible) {
                        applyWebViewStylingAndRevealBody {
                            if (isAdded && _binding != null) {
                                Log.d(WEBVIEW_DEBUG_TAG, "onPageCommitVisible (offline): Styling complete. Making WebView widget VISIBLE.")
                                binding.pageWebView.visibility = View.VISIBLE
                                // Log history after WebView is made visible for the first time for this content
                                // TODO: The source (HistoryEntry.SOURCE_INTERNAL_LINK) should be made dynamic.
                                // Ensure HistoryEntry.SOURCE_INTERNAL_LINK and other source constants are defined.
                                logPageVisit(HistoryEntry.SOURCE_INTERNAL_LINK)
                            } else {
                                Log.w(WEBVIEW_DEBUG_TAG, "onPageCommitVisible (offline) callback: Fragment not added or binding null when trying to make WebView visible.")
                            }
                        }
                    } else {
                        Log.d(WEBVIEW_DEBUG_TAG, "onPageCommitVisible (offline): WebView already visible. Re-applying styles if necessary.")
                        applyWebViewStylingAndRevealBody {
                            Log.d(WEBVIEW_DEBUG_TAG, "onPageCommitVisible (offline): Styles re-applied to already visible WebView.")
                        }
                    }
                } else if (isAdded) {
                    Log.d(WEBVIEW_DEBUG_TAG, "onPageCommitVisible: ONLINE page or state not yet offline. Deferring primary styling/reveal to onPageFinished. isOffline: ${pageViewModel.uiState.isCurrentlyOffline}")
                }
            }

            @SuppressLint("RequiresApi")
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(WEBVIEW_DEBUG_TAG, "WebView onPageFinished for URL: $url. Current page isOffline: ${pageViewModel.uiState.isCurrentlyOffline}. WebView visibility: ${binding.pageWebView?.visibility}")
                if (isAdded && _binding != null && !pageViewModel.uiState.isCurrentlyOffline) {
                    Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished: ONLINE page detected. Applying styles and revealing for URL: $url.")
                    val wasAlreadyVisible = binding.pageWebView.visibility == View.VISIBLE
                    if (!wasAlreadyVisible) {
                        applyWebViewStylingAndRevealBody {
                            if (isAdded && _binding != null) {
                                Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished (online): Styling complete. Making WebView widget VISIBLE.")
                                binding.pageWebView.visibility = View.VISIBLE
                                if (url != null && !url.startsWith("data:") && !url.equals("about:blank", ignoreCase = true)) {
                                    // Log history after WebView is made visible for the first time for this content
                                    // TODO: The source (HistoryEntry.SOURCE_INTERNAL_LINK) should be made dynamic.
                                    // Ensure HistoryEntry.SOURCE_INTERNAL_LINK and other source constants are defined.
                                    logPageVisit(HistoryEntry.SOURCE_INTERNAL_LINK)
                                }
                            } else {
                                Log.w(WEBVIEW_DEBUG_TAG, "onPageFinished (online) callback: Fragment not added or binding null when trying to make WebView visible.")
                            }
                        }
                    } else if (url != null && !url.startsWith("data:") && !url.equals("about:blank", ignoreCase = true)) {
                        Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished (online): Online URL, WebView already visible. Re-applying styles for URL: $url.")
                        applyWebViewStylingAndRevealBody {
                            Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished (online): Styles re-applied to already visible WebView for URL: $url.")
                        }
                    } else {
                        Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished (online): WebView already visible but URL is data/blank, or other. No primary styling action. URL: $url")
                    }
                } else if (isAdded) {
                    Log.d(WEBVIEW_DEBUG_TAG, "onPageFinished: OFFLINE page or state not yet online. Styling should have been handled by onPageCommitVisible. isOffline: ${pageViewModel.uiState.isCurrentlyOffline}")
                }
            }
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

    private fun logPageVisit(source: Int) {
        if (!isAdded || _binding == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Fragment not in a valid state to log history.")
            return
        }

        val currentOsrsApp = OSRSWikiApp.instance
        val currentTab = currentOsrsApp.currentTab
        val state = pageViewModel.uiState

        if (state.isLoading || state.error != null || state.htmlContent == null || state.wikiUrl == null || state.plainTextTitle == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Page not fully loaded or essential data missing. State: $state. Skipping history logging.")
            return
        }

        if (currentTab != null && currentTab.backStack.isNotEmpty()) {
            val lastBackStackUrl = currentTab.backStack.last().pageTitle.uri
            if (lastBackStackUrl == state.wikiUrl) {
                Log.d(HISTORY_DEBUG_TAG, "logPageVisit: Page ${state.wikiUrl} is already top of backstack. Updating scrollY if different.")
                val lastBackStackItem = currentTab.backStack.last()
                val newScrollY = binding.pageWebView.scrollY
                if (lastBackStackItem.scrollY != newScrollY) {
                    lastBackStackItem.scrollY = newScrollY
                    currentOsrsApp.commitTabState()
                    Log.d(HISTORY_DEBUG_TAG, "logPageVisit: Updated scrollY for ${state.wikiUrl} to $newScrollY.")
                }
                return
            }
        }

        Log.d(HISTORY_DEBUG_TAG, "logPageVisit: Logging visit for page: ${state.plainTextTitle}, URL: ${state.wikiUrl}")

        val pageTitleForHistory = CommonPageTitle(
            uri = state.wikiUrl!!,
            text = state.plainTextTitle!!,
            apiTitle = state.plainTextTitle!!,
            displayText = state.title ?: state.plainTextTitle!!,
            pageId = state.pageId ?: -1,
            wiki = WikiSite.OSRS_WIKI,
            thumbUrl = state.imageUrl
        )

        val historyEntry = HistoryEntry(
            pageTitle = pageTitleForHistory,
            timestamp = Date(),
            source = source
        )

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    AppDatabase.instance.historyEntryDao().upsertEntry(historyEntry)
                    Log.d(HISTORY_DEBUG_TAG, "Global history entry upserted for: ${pageTitleForHistory.apiTitle}")
                } catch (e: Exception) {
                    Log.e(HISTORY_DEBUG_TAG, "Error upserting history entry to Room DB", e)
                }
            }
        }

        if (currentTab == null) {
            Log.e(HISTORY_DEBUG_TAG, "logPageVisit: Current tab is null. Cannot add to tab backstack.")
            return
        }

        val pageBackStackItem = PageBackStackItem(
            pageTitle = pageTitleForHistory,
            historyEntry = historyEntry,
            scrollY = binding.pageWebView.scrollY
        )

        currentTab.backStack.add(pageBackStackItem)
        Log.d(HISTORY_DEBUG_TAG, "PageBackStackItem added to tab's backstack for: ${pageTitleForHistory.apiTitle}. New backstack size: ${currentTab.backStack.size}")
        currentOsrsApp.commitTabState()
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

    private fun applyWebViewStylingAndRevealBody(onWebViewStyledAndReadyToReveal: () -> Unit) {
        if (_binding == null || !isAdded) {
            Log.w(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody: Binding is null or fragment not added. Skipping.")
            return
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
        val escapedCssString = cssString.replace("\\", "\\\\").replace("`", "\\`").replace("'", "\\'").replace("\n", "\\n")

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
        binding.pageWebView.evaluateJavascript(injectCssJs) { cssResult ->
            Log.d(WEBVIEW_DEBUG_TAG, "JS for CSS injection evaluated. Result: $cssResult")
            if (!isAdded || _binding == null) {
                Log.w(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody: Fragment detached or binding null after CSS JS eval. Cannot proceed.")
                return@evaluateJavascript
            }

            if (cssResult != null && cssResult.contains("\"CSS Injected\"")) {
                val themeClass = if (isDarkMode()) "theme-dark" else "theme-light"
                val applyThemeAndRevealBodyJs = """
                    (function() {
                        if (!document.body) {
                            console.error('OSRSWikiApp: document.body not ready.');
                            return 'Error: No body element found.';
                        }
                        document.body.classList.remove('theme-light', 'theme-dark');
                        document.body.classList.add('$themeClass');
                        document.body.style.visibility = 'visible';
                        console.log('OSRSWikiApp: Applied theme class (' + '$themeClass' + ') and set body.style.visibility to visible.');
                        return 'Theme and visibility applied.';
                    })();
                """.trimIndent()
                Log.d(WEBVIEW_DEBUG_TAG, "Evaluating JS for theme and body visibility.")
                binding.pageWebView.evaluateJavascript(applyThemeAndRevealBodyJs) { themeAndRevealResult ->
                    Log.d(WEBVIEW_DEBUG_TAG, "JS for theme and body visibility evaluated. Result: $themeAndRevealResult")
                    if (isAdded && _binding != null) {
                        if (themeAndRevealResult != null && themeAndRevealResult.contains("\"Theme and visibility applied.\"")) {
                            onWebViewStyledAndReadyToReveal()
                        } else {
                            Log.e(WEBVIEW_DEBUG_TAG, "Failed to apply theme or make HTML body visible. Result: $themeAndRevealResult. Not calling reveal callback.")
                        }
                    }
                }
            } else {
                Log.w(WEBVIEW_DEBUG_TAG, "CSS injection failed. Result: $cssResult. Attempting fallback body reveal only.")
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
                binding.pageWebView.evaluateJavascript(justRevealBodyJs) { fallbackResult ->
                    Log.d(WEBVIEW_DEBUG_TAG, "JS for fallback body visibility evaluated. Result: $fallbackResult")
                    if (isAdded && _binding != null) {
                        if (fallbackResult != null && fallbackResult.contains("\"Body visibility set (fallback).\"")) {
                            Log.w(WEBVIEW_DEBUG_TAG, "Fallback body reveal succeeded, but CSS was not injected. Page will be unstyled.")
                            onWebViewStyledAndReadyToReveal()
                        } else {
                            Log.e(WEBVIEW_DEBUG_TAG, "Fallback JS also failed to make HTML body visible. Result: $fallbackResult. Not calling reveal callback.")
                        }
                    }
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
            try { idToLoad = currentIdToLoadArg.toInt() }
            catch (e: NumberFormatException) {
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
                Log.d(WEBVIEW_DEBUG_TAG, "Page with ID '$idToLoad' data already present. Reverting loading state and ensuring visibility.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                Log.i(WEBVIEW_DEBUG_TAG, "Requesting to load page by ID: $idToLoad (Title arg was: '$currentTitleToLoadArg')")
                pageContentLoader.loadPageById(idToLoad, currentTitleToLoadArg, forceNetwork)
            }
        } else if (!currentTitleToLoadArg.isNullOrBlank()) {
            if (!forceNetwork && currentViewModelPlainTextTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
                Log.d(WEBVIEW_DEBUG_TAG, "Page with title '$currentTitleToLoadArg' data already present. Reverting loading state and ensuring visibility.")
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false)
                updateUiFromViewModel()
                return
            } else {
                Log.i(WEBVIEW_DEBUG_TAG, "Requesting to load page by title: '$currentTitleToLoadArg' (ID arg was: '$currentIdToLoadArg')")
                pageContentLoader.loadPageByTitle(currentTitleToLoadArg, forceNetwork)
            }
        } else {
            Log.e(WEBVIEW_DEBUG_TAG, "Cannot load page: No valid pageId or pageTitle provided. pageIdArg: '$currentIdToLoadArg', pageTitleArg: '$currentTitleToLoadArg'")
            pageViewModel.uiState = PageUiState(isLoading = false, error = getString(R.string.error_no_article_identifier), title = getString(R.string.title_page_not_specified), plainTextTitle = getString(R.string.title_page_not_specified), pageId = null, htmlContent = null)
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        if (!isAdded || !isVisible || _binding == null) {
            Log.w(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Fragment not in a valid state.")
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
                val blankHtml = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"><style>html, body { background-color: transparent !important; visibility:hidden !important; }</style></head><body></body></html>"""
                binding.pageWebView.loadData(blankHtml, "text/html", "UTF-8")
            }
        } else {
            state.htmlContent?.let { htmlBodySnippet ->
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Preparing to load actual HTML content. Current WebView visibility: ${binding.pageWebView.visibility}, isOffline: ${state.isCurrentlyOffline}")
                binding.pageWebView.visibility = View.INVISIBLE
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Set WebView WIDGET to INVISIBLE before loadDataWithBaseURL.")

                val currentIsDarkMode = isDarkMode()
                val themeSpecificParchmentColorRes = if (currentIsDarkMode) R.color.osrs_parchment_bg_dark_theme_value else R.color.osrs_parchment_bg
                val backgroundColorInt = ContextCompat.getColor(requireContext(), themeSpecificParchmentColorRes)
                val backgroundColorHex = String.format("#%06X", (0xFFFFFF and backgroundColorInt))

                val finalHtml = """
                    <!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${state.title ?: pageTitleArg ?: "OSRS Wiki"}</title>
                    <style>html { background-color: $backgroundColorHex !important; } body { visibility: hidden; background-color: $backgroundColorHex !important; }</style>
                    </head><body> ${htmlBodySnippet}</body></html>
                """.trimIndent()

                val baseUrlToUse: String? = WikiSite.OSRS_WIKI.url()
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Using HTTPS base URL for loadDataWithBaseURL: '$baseUrlToUse' (isOffline: ${state.isCurrentlyOffline})")
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: Calling loadDataWithBaseURL with effective baseUrl: '$baseUrlToUse'. WebView visibility: ${binding.pageWebView.visibility}")
                binding.pageWebView.loadDataWithBaseURL(baseUrlToUse, finalHtml, "text/html", "UTF-8", null)
                Log.d(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: loadDataWithBaseURL called. Waiting for WebViewClient callbacks to make visible, style, and log history.")
            } ?: run {
                Log.w(WEBVIEW_DEBUG_TAG, "updateUiFromViewModel: htmlContent is null, but not loading and no error. Displaying 'content unavailable'.")
                if (binding.pageWebView.visibility != View.VISIBLE) {
                    binding.pageWebView.visibility = View.VISIBLE
                }
                binding.pageWebView.loadDataWithBaseURL(null, getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
        Log.i(WEBVIEW_DEBUG_TAG, "PageFragment UI updated. ViewModel plainTextTitle: '${state.plainTextTitle}', pageId: ${state.pageId}, isLoading: ${state.isLoading}, isOffline: ${state.isCurrentlyOffline}, error: ${state.error != null}, htmlContent isNull: ${state.htmlContent == null}")
    }


    private fun observeAndRefreshSaveButtonState() {
        pageStateObserverJob?.cancel()
        val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() } ?: pageTitleArg?.takeIf { it.isNotBlank() }
        if (plainTextForApi.isNullOrBlank()) {
            Log.e("PFragment_SAVE_TEST", "observeAndRefreshSaveButtonState - No plain text title. Cannot observe.")
            updateSaveIcon(null); return
        }
        val tempPageTitle = com.omiyawaki.osrswiki.page.PageTitle(text = plainTextForApi, wikiSite = WikiSite.OSRS_WIKI)
        Log.e("PFragment_SAVE_TEST", "observeAndRefreshSaveButtonState - Starting to observe apiTitle '${tempPageTitle.prefixedText}'")
        pageStateObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            val defaultListId = withContext(Dispatchers.IO) { AppDatabase.instance.readingListDao().let { it.getDefaultList() ?: it.createDefaultListIfNotExist() }.id }
            readingListPageDao.observePageByListIdAndTitle(tempPageTitle.wikiSite, tempPageTitle.wikiSite.languageCode, tempPageTitle.namespace(), tempPageTitle.prefixedText, defaultListId)
                .collectLatest { entry -> Log.e("PFragment_SAVE_TEST", "observeAndRefreshSaveButtonState - Observed ReadingListPage: $entry"); updateSaveIcon(entry) }
        }
    }

    private fun updateSaveIcon(entry: ReadingListPage?) {
        if (!isAdded || _binding == null) { L.v("updateSaveIcon: Fragment not in a state to update UI."); return }
        val isActuallySavedAndOffline = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isActuallySavedAndOffline))
        Log.e("PFragment_SAVE_TEST", "updateSaveIcon - Icon updated. IsActuallySavedAndOffline: $isActuallySavedAndOffline for apiTitle: ${entry?.apiTitle ?: pageViewModel.uiState.plainTextTitle ?: pageTitleArg}")
    }

    private fun refreshSaveButtonState() {
        Log.d("PFragment_SAVE_TEST", "Legacy refreshSaveButtonState called")
        val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: pageTitleArg?.takeIf { it.isNotBlank() }

        if (plainTextForApi.isNullOrBlank()) {
            updateSaveIcon(null)
            return
        }
        val tempPageTitle = com.omiyawaki.osrswiki.page.PageTitle(text = plainTextForApi, wikiSite = WikiSite.OSRS_WIKI)
        viewLifecycleOwner.lifecycleScope.launch {
            val defaultListId = withContext(Dispatchers.IO) {
                val rlDao = AppDatabase.instance.readingListDao()
                (rlDao.getDefaultList() ?: rlDao.createDefaultListIfNotExist()).id
            }
            val currentEntry = withContext(Dispatchers.IO) {
                readingListPageDao.getPageByListIdAndTitle(
                    tempPageTitle.wikiSite, tempPageTitle.wikiSite.languageCode,
                    tempPageTitle.namespace(), tempPageTitle.prefixedText, defaultListId, -1L
                )
            }
            updateSaveIcon(currentEntry)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageStateObserverJob?.cancel()
        _binding?.pageWebView?.let { webView -> webView.stopLoading(); (webView.parent as? ViewGroup)?.removeView(webView); webView.destroy(); Log.d(WEBVIEW_DEBUG_TAG, "WebView destroyed.") }
        _binding = null
        Log.d(WEBVIEW_DEBUG_TAG, "PageFragment onDestroyView, _binding set to null.")
    }

    companion object {
        private const val ARG_PAGE_ID = "pageId"
        private const val ARG_PAGE_TITLE = "pageTitle"

        @JvmStatic
        fun newInstance(pageId: String?, pageTitle: String?): PageFragment =
            PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_ID, pageId)
                    putString(ARG_PAGE_TITLE, pageTitle)
                }
            }
    }

    private inner class PageActionItemCallback : PageActionItem.Callback {
        private fun showThemedSnackbar(message: String, length: Int = Snackbar.LENGTH_LONG) {
            if (!isAdded || _binding == null) return
            val snackbar = Snackbar.make(binding.root, message, length).setAnchorView(binding.pageActionsTabLayout)
            val (snackbarBgColorResId, snackbarTextColorResId) = if (isDarkMode()) R.color.snackbar_background_light_appearance to R.color.snackbar_text_color_light_appearance else R.color.snackbar_background_dark_appearance to R.color.snackbar_text_color_dark_appearance
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), snackbarBgColorResId)).setTextColor(ContextCompat.getColor(requireContext(), snackbarTextColorResId)).show()
        }

        override fun onSaveSelected() {
            Log.e("SaveActionEntryTest", "onSaveSelected method in PageFragment was ENTERED.")
            Log.e("SaveActionDebug", "onSaveSelected CALLED for page: ${pageViewModel.uiState.plainTextTitle ?: pageTitleArg}")
            if (!isAdded || _binding == null) {
                Log.w("SaveActionDebug", "onSaveSelected exiting early: !isAdded (${!isAdded}), _binding == null (${_binding == null})")
                return
            }
            Log.e("PFragment_SAVE_TEST", "onSaveSelected --- ENTRY POINT REACHED --- current VM title: '${pageViewModel.uiState.plainTextTitle}', VM pageId: ${pageViewModel.uiState.pageId}, arg pageTitle: '$pageTitleArg'");
            val pvmPlainTextTitle = pageViewModel.uiState.plainTextTitle
            val pvmHtmlContent = pageViewModel.uiState.htmlContent
            val pTitleArg = pageTitleArg
            Log.e("PFragment_SAVE_TEST", "Values for plainTextForApi: pvmPlainTextTitle='${pvmPlainTextTitle}', pvmHtmlContent is " + (if(pvmHtmlContent != null) "NOT NULL" else "NULL") + ", pageTitleArg='${pTitleArg}'");
            val plainTextForApi = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() && pageViewModel.uiState.htmlContent != null }
                ?: pageTitleArg?.takeIf { it.isNotBlank() }
            Log.e("PFragment_SAVE_TEST", "Derived plainTextForApi: '${plainTextForApi}'");
            if (plainTextForApi.isNullOrBlank()) {
                Log.e("PFragment_SAVE_TEST", "plainTextForApi IS NULL OR BLANK. Will show snackbar and return.");
                if (isAdded && _binding != null) {
                    showThemedSnackbar(getString(R.string.cannot_save_page_no_title), Snackbar.LENGTH_SHORT)
                }
                return
            }
            Log.e("PFragment_SAVE_TEST", "plainTextForApi IS VALID. Proceeding with save logic.");
            var debugStep = "START"
            try {
                debugStep = "htmlTextForDisplay"; val htmlTextForDisplay = pageViewModel.uiState.title?.takeIf { it.isNotBlank() && pageViewModel.uiState.htmlContent != null } ?: plainTextForApi
                Log.d("PFragment_SAVE_TEST", "$debugStep determined for '$plainTextForApi'")
                debugStep = "currentThumb"; val currentThumb = pageViewModel.uiState.imageUrl
                Log.d("PFragment_SAVE_TEST", "$debugStep determined for '$plainTextForApi'")
                debugStep = "currentPageTitle"; val currentPageTitle = com.omiyawaki.osrswiki.page.PageTitle(namespace = null, text = plainTextForApi, wikiSite = WikiSite.OSRS_WIKI, thumbUrl = currentThumb, description = null, displayText = htmlTextForDisplay)
                Log.d("PFragment_SAVE_TEST", "$debugStep created for '$plainTextForApi': ${currentPageTitle.prefixedText}")
                debugStep = "titleForSnackbar"; val titleForSnackbar = currentPageTitle.prefixedText
                Log.d("PFragment_SAVE_TEST", "$debugStep determined for '$plainTextForApi': $titleForSnackbar. About to launch coroutine.")
                debugStep = "LAUNCHING_COROUTINE"
                viewLifecycleOwner.lifecycleScope.launch {
                    Log.e("PFragment_SAVE_TEST", "Save coroutine STARTED for '${titleForSnackbar}'")
                    var message: String = getString(R.string.error_generic_save_unsave); var existingEntry: ReadingListPage? = null
                    try {
                        Log.d("PFragment_SAVE_TEST", "Coroutine for '${titleForSnackbar}': Inside try block, before DAO calls.")
                        val readingListDao = AppDatabase.instance.readingListDao(); val localReadingListPageDao = AppDatabase.instance.readingListPageDao()
                        Log.d("PFragment_SAVE_TEST", "Coroutine for '${titleForSnackbar}': DAO instances obtained.")
                        val defaultList = withContext(Dispatchers.IO) { Log.d("PFragment_SAVE_TEST", "Coroutine for '${titleForSnackbar}': Getting default list (IO)."); readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist() }
                        Log.d("PFragment_SAVE_TEST", "Coroutine for '${titleForSnackbar}': Default list ID: ${defaultList.id}.")
                        existingEntry = withContext(Dispatchers.IO) { Log.d("PFragment_SAVE_TEST", "Coroutine for '${titleForSnackbar}': Getting page by list ID and title (IO) for apiTitle: ${currentPageTitle.prefixedText}."); localReadingListPageDao.getPageByListIdAndTitle(currentPageTitle.wikiSite, currentPageTitle.wikiSite.languageCode, currentPageTitle.namespace(), currentPageTitle.prefixedText, defaultList.id, -1L) }
                        Log.e("PFragment_SAVE_TEST", "DAO Query for apiTitle '${currentPageTitle.prefixedText}', Entry found: ${existingEntry != null}, Offline: ${existingEntry?.offline}, Status: ${existingEntry?.status}")
                        if (existingEntry != null) {
                            Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry IS NOT NULL. Offline: ${existingEntry.offline}, Status: ${existingEntry.status}")
                            if (existingEntry.offline && existingEntry.status == ReadingListPage.STATUS_SAVED) {
                                Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry.offline IS TRUE. Marking for DELETION.")
                                withContext(Dispatchers.IO) { localReadingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry)) }
                                message = "'$titleForSnackbar' offline version will be removed."
                            } else {
                                Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry.offline IS FALSE. Marking for SAVE/DOWNLOAD.")
                                val downloadWillBeAttempted = Prefs.isDownloadingReadingListArticlesEnabled
                                withContext(Dispatchers.IO) { localReadingListPageDao.markPagesForOffline(listOf(existingEntry), offline = true, forcedSave = false) }
                                message = if (downloadWillBeAttempted) "'$titleForSnackbar' queued for download." else "'$titleForSnackbar' marked for offline availability (will be saved without content if downloads disabled)."
                            }
                        } else {
                            Log.e("PFragment_SAVE_TEST", "Path taken: existingEntry IS NULL. Adding new page to list.")
                            val downloadEnabled = Prefs.isDownloadingReadingListArticlesEnabled
                            val titlesAdded = withContext(Dispatchers.IO) { localReadingListPageDao.addPagesToList(defaultList, listOf(currentPageTitle), downloadEnabled) }
                            if (titlesAdded.isNotEmpty()) { L.i("Page '$titleForSnackbar' added to list '${defaultList.title}'."); message = if (downloadEnabled) "'$titleForSnackbar' saved and queued for download." else "'$titleForSnackbar' saved to reading list."
                            } else { L.w("Page '$titleForSnackbar' was not added. It might already exist or an error occurred."); message = "Page '$titleForSnackbar' could not be saved (may already exist or error)." }
                        }
                    } catch (e: Exception) { Log.e("PFragment_SAVE_TEST", "Error during save/unsave for '${titleForSnackbar}' (Inside Coroutine)", e); message = getString(R.string.error_generic_save_unsave) }
                    if(isAdded && _binding != null) showThemedSnackbar(message)
                }
            } catch (e: Exception) { Log.e("PFragment_SAVE_TEST", "FATAL Exception in onSaveSelected for '$plainTextForApi' BEFORE coroutine launch at step '$debugStep'", e); if(isAdded && _binding != null) showThemedSnackbar(getString(R.string.error_generic_save_unsave)) }
        }

        override fun onFindInArticleSelected() { L.d("Find in article selected - Not yet implemented"); if (isAdded && _binding != null) showThemedSnackbar("Find in page: Not yet implemented.", Snackbar.LENGTH_SHORT) }
        override fun onThemeSelected() { L.d("Theme selected - Not yet implemented"); if (isAdded && _binding != null) showThemedSnackbar("Appearance: Not yet implemented.", Snackbar.LENGTH_SHORT) }
        override fun onContentsSelected() { L.d("Contents selected - Not yet implemented"); if (isAdded && _binding != null) showThemedSnackbar("Contents: Not yet implemented.", Snackbar.LENGTH_SHORT) }
    }
}