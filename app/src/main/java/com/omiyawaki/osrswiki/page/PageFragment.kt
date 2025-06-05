package com.omiyawaki.osrswiki.page
import com.omiyawaki.osrswiki.theme.ThemeChooserDialog

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
import androidx.appcompat.widget.PopupMenu // Added import
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.common.models.PageTitle as CommonPageTitle
import com.omiyawaki.osrswiki.page.PageTitle as PagePackagePageTitle
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
    private var navigationSource: Int = HistoryEntry.SOURCE_INTERNAL_LINK

    private val pageActionItemCallback = PageActionItemCallback() // Existing callback for actions
    private var pageStateObserverJob: Job? = null

    private val WEBVIEW_DEBUG_TAG = "PFragment_WebViewDebug"
    private val HISTORY_DEBUG_TAG = "PFragment_HistoryDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
            navigationSource = it.getInt(ARG_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)
        }
        pageViewModel = PageViewModel()
        pageRepository = (requireActivity().applicationContext as OSRSWikiApp).pageRepository
        readingListPageDao = AppDatabase.instance.readingListPageDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appDb = AppDatabase.instance
        pageContentLoader = PageContentLoader(
            context = requireContext().applicationContext,
            pageRepository = pageRepository,
            pageViewModel = pageViewModel,
            readingListPageDao = appDb.readingListPageDao(),
            offlineObjectDao = appDb.offlineObjectDao(),
            coroutineScope = viewLifecycleOwner.lifecycleScope
        ) {
            if (isAdded && _binding != null) {
                updateUiFromViewModel()
                observeAndRefreshSaveButtonState()
            }
        }

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
                if (isAdded && _binding != null && pageViewModel.uiState.isCurrentlyOffline) {
                    val wasAlreadyVisible = binding.pageWebView.visibility == View.VISIBLE
                    if (!wasAlreadyVisible) {
                        applyWebViewStylingAndRevealBody {
                            if (isAdded && _binding != null) {
                                binding.pageWebView.visibility = View.VISIBLE
                                logPageVisit()
                            }
                        }
                    } else {
                        applyWebViewStylingAndRevealBody {}
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (isAdded && _binding != null && !pageViewModel.uiState.isCurrentlyOffline) {
                    val wasAlreadyVisible = binding.pageWebView.visibility == View.VISIBLE
                    if (!wasAlreadyVisible) {
                        applyWebViewStylingAndRevealBody {
                            if (isAdded && _binding != null) {
                                binding.pageWebView.visibility = View.VISIBLE
                                if (url != null && !url.startsWith("data:") && !url.equals("about:blank", ignoreCase = true)) {
                                    logPageVisit()
                                }
                            }
                        }
                    } else if (url != null && !url.startsWith("data:") && !url.equals("about:blank", ignoreCase = true)) {
                        applyWebViewStylingAndRevealBody {}
                    }
                }
            }
        }

        binding.pageWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let { Log.i("WebViewConsole", "[${it.sourceId()}:${it.lineNumber()}] ${it.message()}") }
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

        binding.errorTextView.setOnClickListener { initiatePageLoad(forceNetwork = true) }
    }

    // Method to be called from PageActivity to show the overflow menu
    fun showPageOverflowMenu(anchorView: View) {
        if (!isAdded || context == null) return
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_page_overflow, popup.menu)

        // TODO: Dynamically update menu item titles/icons if needed (e.g., "Save" vs "Unsave")
        // For example:
        // val saveItem = popup.menu.findItem(R.id.menu_page_overflow_save)
        // saveItem?.title = if (/* some condition for isSaved */ true) "Unsave page" else "Save page"

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_page_overflow_save -> {
                    pageActionItemCallback.onSaveSelected()
                    true
                }
                R.id.menu_page_overflow_find_in_article -> {
                    pageActionItemCallback.onFindInArticleSelected()
                    true
                }
                R.id.menu_page_overflow_appearance -> {
                    pageActionItemCallback.onThemeSelected()
                    true
                }
                R.id.menu_page_overflow_contents -> {
                    pageActionItemCallback.onContentsSelected()
                    true
                }
                else -> false
            }
        }
        popup.show()
        L.d("Page overflow menu shown.")
    }


    private fun logPageVisit() {
        if (!isAdded || _binding == null) { Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Fragment not in a valid state to log history."); return }
        val currentOsrsApp = OSRSWikiApp.instance
        val currentTab = currentOsrsApp.currentTab
        val state = pageViewModel.uiState

        if (state.isLoading || state.error != null || state.htmlContent == null || state.wikiUrl == null || state.plainTextTitle == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Page not fully loaded or essential data missing. State: $state. Skipping history logging.")
            return
        }

        if (currentTab != null && currentTab.backStack.isNotEmpty()) {
            val lastBackStackUrl = currentTab.backStack.last().pageTitle.wikiUrl
            if (lastBackStackUrl == state.wikiUrl) {
                val lastBackStackItem = currentTab.backStack.last()
                val newScrollY = binding.pageWebView.scrollY
                if (lastBackStackItem.scrollY != newScrollY) {
                    lastBackStackItem.scrollY = newScrollY
                    currentOsrsApp.commitTabState()
                }
                return
            }
        }

        val commonPageTitleForHistory = CommonPageTitle(
            wikiUrl = state.wikiUrl!!,
            displayText = state.title ?: state.plainTextTitle!!,
            pageId = state.pageId ?: -1,
            apiPath = state.plainTextTitle!!
        )

        val historyEntry = HistoryEntry(
            pageTitle = commonPageTitleForHistory,
            source = navigationSource
        )

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    AppDatabase.instance.historyEntryDao().upsertEntry(historyEntry)
                    Log.d(HISTORY_DEBUG_TAG, "Global history upserted for: ${commonPageTitleForHistory.apiPath}")
                } catch (e: Exception) {
                    L.e("$HISTORY_DEBUG_TAG: Error upserting history entry", e)
                }
            }
        }

        if (currentTab == null) { Log.e(HISTORY_DEBUG_TAG, "logPageVisit: Current tab is null. Cannot add to tab backstack."); return }

        val pageBackStackItem = PageBackStackItem(
            pageTitle = commonPageTitleForHistory,
            historyEntry = historyEntry,
            scrollY = binding.pageWebView.scrollY
        )
        currentTab.backStack.add(pageBackStackItem)
        Log.d(HISTORY_DEBUG_TAG, "PageBackStackItem added for: ${commonPageTitleForHistory.apiPath}. Stack size: ${currentTab.backStack.size}")
        currentOsrsApp.commitTabState()
    }

    private fun isDarkMode(): Boolean {
        if (!isAdded) return false
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun setWebViewWidgetBackgroundColor() {
        if (_binding == null || !isAdded) return
        binding.pageWebView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun applyWebViewStylingAndRevealBody(onWebViewStyledAndReadyToReveal: () -> Unit) {
        if (_binding == null || !isAdded) { Log.w(WEBVIEW_DEBUG_TAG, "applyWebViewStylingAndRevealBody: Binding is null or fragment not added. Skipping."); return }
        val cssString: String
        try { cssString = requireContext().assets.open("styles/wiki_content.css").bufferedReader().use { it.readText() }
        } catch (e: IOException) { Log.e(WEBVIEW_DEBUG_TAG, "Error reading wiki_content.css from assets", e); return }
        val escapedCssString = cssString.replace("\\", "\\\\").replace("`", "\\`").replace("'", "\\'").replace("\n", "\\n")
        val injectCssJs = """
            (function() {
                var style = document.getElementById('osrsWikiInjectedStyle');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'osrsWikiInjectedStyle';
                    style.type = 'text/css';
                    var head = document.head || document.getElementsByTagName('head')[0];
                    if (head) { head.appendChild(style); } else { return 'Error: No head element found.';}
                }
                style.innerHTML = '${escapedCssString}';
                return 'CSS Injected';
            })();
        """.trimIndent()
        binding.pageWebView.evaluateJavascript(injectCssJs) { cssResult ->
            if (!isAdded || _binding == null) return@evaluateJavascript
            if (cssResult != null && cssResult.contains("CSS Injected")) {
                val themeClass = if (isDarkMode()) "theme-dark" else "theme-light"
                val applyThemeAndRevealBodyJs = """
                    (function() {
                        if (!document.body) return 'Error: No body element found.';
                        document.body.classList.remove('theme-light', 'theme-dark');
                        document.body.classList.add('$themeClass');
                        document.body.style.visibility = 'visible';
                        return 'Theme and visibility applied.';
                    })();
                """.trimIndent()
                binding.pageWebView.evaluateJavascript(applyThemeAndRevealBodyJs) { themeAndRevealResult ->
                    if (isAdded && _binding != null && themeAndRevealResult != null && themeAndRevealResult.contains("Theme and visibility applied.")) {
                        onWebViewStyledAndReadyToReveal()
                    } else { Log.e(WEBVIEW_DEBUG_TAG, "Failed to apply theme/reveal. Result: $themeAndRevealResult") }
                }
            } else {
                Log.w(WEBVIEW_DEBUG_TAG, "CSS injection failed. Result: $cssResult")
                val fallbackJs = "(function(){if(document.body){document.body.style.visibility='visible';return 'Fallback Visible';}return 'Fallback Fail';})()"
                binding.pageWebView.evaluateJavascript(fallbackJs){ fbResult ->
                    if(isAdded && _binding != null && fbResult != null && fbResult.contains("Fallback Visible")) {
                        onWebViewStyledAndReadyToReveal()
                    } else {
                        Log.e(WEBVIEW_DEBUG_TAG, "Fallback JS also failed: $fbResult")
                    }
                }
            }
        }
    }

    private fun initiatePageLoad(forceNetwork: Boolean = false) {
        val currentIdToLoadArg = pageIdArg
        val currentTitleToLoadArg = pageTitleArg
        var idToLoad: Int? = null
        if (!currentIdToLoadArg.isNullOrBlank()) { try { idToLoad = currentIdToLoadArg.toInt() } catch (e: NumberFormatException) { idToLoad = null }}
        val currentViewModelPageId: Int? = pageViewModel.uiState.pageId
        val currentViewModelPlainTextTitle = pageViewModel.uiState.plainTextTitle
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null); updateUiFromViewModel()

        if (idToLoad != null) {
            if (!forceNetwork && currentViewModelPageId == idToLoad && contentAlreadyLoaded) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false); updateUiFromViewModel(); return
            } else { pageContentLoader.loadPageById(idToLoad, currentTitleToLoadArg, forceNetwork) }
        } else if (!currentTitleToLoadArg.isNullOrBlank()) {
            if (!forceNetwork && currentViewModelPlainTextTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false); updateUiFromViewModel(); return
            } else { pageContentLoader.loadPageByTitle(currentTitleToLoadArg, forceNetwork) }
        } else {
            pageViewModel.uiState = PageUiState(isLoading = false, error = getString(R.string.error_no_article_identifier), title = getString(R.string.title_page_not_specified), plainTextTitle = getString(R.string.title_page_not_specified), pageId = null, htmlContent = null)
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        if (!isAdded || !isVisible || _binding == null) return
        val state = pageViewModel.uiState
        binding.progressBar.isVisible = state.isLoading
        state.error?.let { binding.errorTextView.text = it; binding.errorTextView.isVisible = true; binding.pageWebView.visibility = View.INVISIBLE } ?: run { binding.errorTextView.isVisible = false }

        if (state.isLoading || state.error != null) {
            if (binding.pageWebView.visibility == View.VISIBLE || (state.isLoading && state.htmlContent == null)) { binding.pageWebView.visibility = View.INVISIBLE }
            if (state.isLoading && state.htmlContent == null && (binding.pageWebView.url == null || binding.pageWebView.url == "about:blank")) {
                val blankHtml = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>html,body{background-color:transparent !important;visibility:hidden !important;}</style></head><body></body></html>"
                binding.pageWebView.loadData(blankHtml, "text/html", "UTF-8")
            }
        } else {
            state.htmlContent?.let { htmlBodySnippet ->
                binding.pageWebView.visibility = View.INVISIBLE
                val currentIsDarkMode = isDarkMode()
                val themeSpecificParchmentColorRes = if (currentIsDarkMode) R.color.osrs_parchment_bg_dark_theme_value else R.color.osrs_parchment_bg
                val backgroundColorInt = ContextCompat.getColor(requireContext(), themeSpecificParchmentColorRes)
                val backgroundColorHex = String.format("#%06X", (0xFFFFFF and backgroundColorInt))
                val finalHtml = """
                    <!DOCTYPE html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>${state.title ?: pageTitleArg ?: "OSRS Wiki"}</title>
                    <style>html{background-color:$backgroundColorHex !important;}body{visibility:hidden;background-color:$backgroundColorHex !important;}</style>
                    </head><body> $htmlBodySnippet</body></html>
                """.trimIndent()
                val baseUrl = state.wikiUrl ?: WikiSite.OSRS_WIKI.url()
                binding.pageWebView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
            } ?: run {
                if (binding.pageWebView.visibility != View.VISIBLE) binding.pageWebView.visibility = View.VISIBLE
                binding.pageWebView.loadDataWithBaseURL(null, getString(R.string.label_content_unavailable), "text/html", "UTF-8", null)
            }
        }
    }

    private fun observeAndRefreshSaveButtonState() {
        pageStateObserverJob?.cancel()
        val titleForDaoLookup = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: pageTitleArg?.takeIf { it.isNotBlank() }

        if (titleForDaoLookup.isNullOrBlank()) { updateSaveIcon(null); return }

        val pagePackageTitle = PagePackagePageTitle(
            namespace = Namespace.MAIN,
            text = titleForDaoLookup,
            wikiSite = WikiSite.OSRS_WIKI
        )

        pageStateObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            val defaultListId = withContext(Dispatchers.IO) { AppDatabase.instance.readingListDao().let { it.getDefaultList() ?: it.createDefaultListIfNotExist() }.id }
            readingListPageDao.observePageByListIdAndTitle(
                pagePackageTitle.wikiSite,
                pagePackageTitle.wikiSite.languageCode,
                pagePackageTitle.namespace(),
                pagePackageTitle.prefixedText,
                defaultListId
            ).collectLatest { entry -> updateSaveIcon(entry) }
        }
    }

    private fun updateSaveIcon(entry: ReadingListPage?) {
        if (!isAdded || _binding == null) { return }
        val isSaved = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isSaved))
        Log.d("PFragment_SAVE_TEST", "Save icon updated. IsSaved: $isSaved for apiTitle: ${entry?.apiTitle}")
    }

    private fun refreshSaveButtonState() {
        val titleForDaoLookup = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() } ?: pageTitleArg
        if (titleForDaoLookup.isNullOrBlank()) { updateSaveIcon(null); return }

        val pagePackageTitle = PagePackagePageTitle(
            namespace = Namespace.MAIN,
            text = titleForDaoLookup,
            wikiSite = WikiSite.OSRS_WIKI
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val defaultListId = withContext(Dispatchers.IO) { AppDatabase.instance.readingListDao().let { it.getDefaultList() ?: it.createDefaultListIfNotExist() }.id }
            val currentEntry = withContext(Dispatchers.IO) {
                readingListPageDao.getPageByListIdAndTitle(
                    pagePackageTitle.wikiSite,
                    pagePackageTitle.wikiSite.languageCode,
                    pagePackageTitle.namespace(),
                    pagePackageTitle.prefixedText,
                    defaultListId,
                    ReadingListPage.STATUS_QUEUE_FOR_DELETE
                )
            }
            updateSaveIcon(currentEntry)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageStateObserverJob?.cancel()
        _binding?.pageWebView?.let { webView -> webView.stopLoading(); (webView.parent as? ViewGroup)?.removeView(webView); webView.destroy() }
        _binding = null
    }

    companion object {
        private const val ARG_PAGE_ID = "pageId"
        private const val ARG_PAGE_TITLE = "pageTitle"
        private const val ARG_PAGE_SOURCE = "pageSource"
        @JvmStatic
        fun newInstance(pageId: String?, pageTitle: String?, source: Int): PageFragment =
            PageFragment().apply { arguments = Bundle().apply {
                putString(ARG_PAGE_ID, pageId)
                putString(ARG_PAGE_TITLE, pageTitle)
                putInt(ARG_PAGE_SOURCE, source)
            }}
    }

    internal inner class PageActionItemCallback : PageActionItem.Callback { // Made internal for access from showPageOverflowMenu if needed, or keep private and call directly
        private fun showThemedSnackbar(message: String, length: Int = Snackbar.LENGTH_LONG) {
            if (!isAdded || _binding == null) return
            val snackbar = Snackbar.make(binding.root, message, length).setAnchorView(binding.pageActionsTabLayout)
            val (snackbarBgColorResId, snackbarTextColorResId) = if (isDarkMode()) R.color.snackbar_background_light_appearance to R.color.snackbar_text_color_light_appearance else R.color.snackbar_background_dark_appearance to R.color.snackbar_text_color_dark_appearance
            snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), snackbarBgColorResId)).setTextColor(ContextCompat.getColor(requireContext(), snackbarTextColorResId)).show()
        }

        override fun onSaveSelected() {
            if (!isAdded || _binding == null) { return }
            val titleForDaoLookup = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() && pageViewModel.uiState.htmlContent != null }
                ?: pageTitleArg?.takeIf { it.isNotBlank() }

            if (titleForDaoLookup.isNullOrBlank()) {
                if (isAdded && _binding != null) showThemedSnackbar(getString(R.string.cannot_save_page_no_title), Snackbar.LENGTH_SHORT)
                return
            }

            val pagePackagePageTitle = PagePackagePageTitle(
                namespace = Namespace.MAIN,
                text = titleForDaoLookup,
                wikiSite = WikiSite.OSRS_WIKI,
                displayText = pageViewModel.uiState.title ?: titleForDaoLookup,
                thumbUrl = pageViewModel.uiState.imageUrl
            )
            val titleForSnackbar = pagePackagePageTitle.displayText

            viewLifecycleOwner.lifecycleScope.launch {
                var message: String = getString(R.string.error_generic_save_unsave)
                try {
                    val readingListDao = AppDatabase.instance.readingListDao()
                    val localReadingListPageDao = AppDatabase.instance.readingListPageDao()
                    val defaultList = withContext(Dispatchers.IO) { readingListDao.getDefaultList() ?: readingListDao.createDefaultListIfNotExist() }

                    val existingEntry = withContext(Dispatchers.IO) {
                        localReadingListPageDao.getPageByListIdAndTitle(
                            pagePackagePageTitle.wikiSite,
                            pagePackagePageTitle.wikiSite.languageCode,
                            pagePackagePageTitle.namespace(),
                            pagePackagePageTitle.prefixedText,
                            defaultList.id,
                            ReadingListPage.STATUS_QUEUE_FOR_DELETE
                        )
                    }

                    if (existingEntry != null) {
                        if (existingEntry.offline && existingEntry.status == ReadingListPage.STATUS_SAVED) {
                            withContext(Dispatchers.IO) { localReadingListPageDao.markPagesForDeletion(defaultList.id, listOf(existingEntry)) }
                            message = "'$titleForSnackbar' offline version will be removed."
                        } else {
                            val downloadWillBeAttempted = Prefs.isDownloadingReadingListArticlesEnabled
                            withContext(Dispatchers.IO) { localReadingListPageDao.markPagesForOffline(listOf(existingEntry), offline = true, forcedSave = false) }
                            message = if (downloadWillBeAttempted) "'$titleForSnackbar' queued for download." else "'$titleForSnackbar' marked for offline availability."
                        }
                    } else {
                        val downloadEnabled = Prefs.isDownloadingReadingListArticlesEnabled
                        val titlesAdded = withContext(Dispatchers.IO) { localReadingListPageDao.addPagesToList(defaultList, listOf(pagePackagePageTitle), downloadEnabled) }
                        if (titlesAdded.isNotEmpty()) { message = if (downloadEnabled) "'$titleForSnackbar' saved and queued for download." else "'$titleForSnackbar' saved to reading list."
                        } else { message = "Page '$titleForSnackbar' could not be saved." }
                    }
                } catch (e: Exception) {
                    Log.e("PFragment_SAVE_TEST", "Error during save/unsave for '$titleForSnackbar'", e)
                    message = getString(R.string.error_generic_save_unsave)
                }
                if(isAdded && _binding != null) showThemedSnackbar(message)
            }
        }

        override fun onFindInArticleSelected() { if (isAdded && _binding != null) showThemedSnackbar("Find in page: Not yet implemented.", Snackbar.LENGTH_SHORT) }
        override fun onThemeSelected() {
            if (isAdded) {
                com.omiyawaki.osrswiki.theme.ThemeChooserDialog.newInstance()
                    .show(childFragmentManager, com.omiyawaki.osrswiki.theme.ThemeChooserDialog.TAG)
            }
        }
        override fun onContentsSelected() { if (isAdded && _binding != null) showThemedSnackbar("Contents: Not yet implemented.", Snackbar.LENGTH_SHORT) }
    }
}
