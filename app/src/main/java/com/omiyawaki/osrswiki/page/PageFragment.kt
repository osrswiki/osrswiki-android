package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.common.models.PageTitle as CommonPageTitle
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageTitle as PagePackagePageTitle
import com.omiyawaki.osrswiki.page.action.PageActionItem
import com.omiyawaki.osrswiki.page.tabs.PageBackStackItem
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import androidx.core.view.isVisible

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var pageViewModel: PageViewModel
    private lateinit var pageRepository: PageRepository
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var readingListPageDao: ReadingListPageDao
    private lateinit var pageLinkHandler: PageLinkHandler
    private lateinit var pageWebViewManager: PageWebViewManager
    private lateinit var pageActionHandler: PageActionHandler

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null
    private var navigationSource: Int = HistoryEntry.SOURCE_INTERNAL_LINK

    private var pageStateObserverJob: Job? = null

    private val HISTORY_DEBUG_TAG = "PFragment_HistoryDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
            navigationSource = it.getInt(ARG_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)
        }
        pageViewModel = PageViewModel()

        val appInstance = requireActivity().applicationContext as OSRSWikiApp
        pageRepository = appInstance.pageRepository
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

        pageLinkHandler = PageLinkHandler(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            pageRepository,
            pageViewModel
        )

        pageWebViewManager = PageWebViewManager(
            context = requireContext(),
            webView = binding.pageWebView,
            linkHandler = pageLinkHandler,
            onPageReady = {
                logPageVisit()
            }
        )

        pageActionHandler = PageActionHandler(
            fragment = this,
            viewModel = pageViewModel,
            binding = binding
        )

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

        binding.pageActionsTabLayout.callback = pageActionHandler.callback
        binding.pageActionsTabLayout.update()

        updateUiFromViewModel()
        initiatePageLoad(forceNetwork = false)
        observeAndRefreshSaveButtonState()

        binding.errorTextView.setOnClickListener { initiatePageLoad(forceNetwork = true) }
    }

    fun showPageOverflowMenu(anchorView: View) {
        if (!isAdded) return
        pageActionHandler.showPageOverflowMenu(anchorView)
    }

    private fun logPageVisit() {
        if (!isAdded || _binding == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Fragment not in a valid state to log history.")
            return
        }
        val currentOsrsApp = OSRSWikiApp.instance
        val currentTab = currentOsrsApp.currentTab
        val state = pageViewModel.uiState

        if (state.isLoading || state.error != null || state.htmlContent == null || state.wikiUrl == null || state.plainTextTitle == null) {
            Log.w(HISTORY_DEBUG_TAG, "logPageVisit: Page not fully loaded or essential data missing. Skipping history logging.")
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

        if (currentTab == null) {
            Log.e(HISTORY_DEBUG_TAG, "logPageVisit: Current tab is null. Cannot add to tab backstack.")
            return
        }

        val pageBackStackItem = PageBackStackItem(
            pageTitle = commonPageTitleForHistory,
            historyEntry = historyEntry,
            scrollY = binding.pageWebView.scrollY
        )
        currentTab.backStack.add(pageBackStackItem)
        Log.d(HISTORY_DEBUG_TAG, "PageBackStackItem added for: ${commonPageTitleForHistory.apiPath}. Stack size: ${currentTab.backStack.size}")
        currentOsrsApp.commitTabState()
    }

    private fun initiatePageLoad(forceNetwork: Boolean = false) {
        val currentIdToLoadArg = pageIdArg
        val currentTitleToLoadArg = pageTitleArg
        var idToLoad: Int? = null
        if (!currentIdToLoadArg.isNullOrBlank()) {
            try {
                idToLoad = currentIdToLoadArg.toInt()
            } catch (e: NumberFormatException) {
                idToLoad = null
            }
        }
        val currentViewModelPageId: Int? = pageViewModel.uiState.pageId
        val currentViewModelPlainTextTitle = pageViewModel.uiState.plainTextTitle
        val contentAlreadyLoaded = pageViewModel.uiState.htmlContent != null && pageViewModel.uiState.error == null

        pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = true, error = null); updateUiFromViewModel()

        if (idToLoad != null) {
            if (!forceNetwork && currentViewModelPageId == idToLoad && contentAlreadyLoaded) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false); updateUiFromViewModel(); return
            } else {
                pageContentLoader.loadPageById(idToLoad, currentTitleToLoadArg, forceNetwork)
            }
        } else if (!currentTitleToLoadArg.isNullOrBlank()) {
            if (!forceNetwork && currentViewModelPlainTextTitle == currentTitleToLoadArg && contentAlreadyLoaded) {
                pageViewModel.uiState = pageViewModel.uiState.copy(isLoading = false); updateUiFromViewModel(); return
            } else {
                pageContentLoader.loadPageByTitle(currentTitleToLoadArg, forceNetwork)
            }
        } else {
            pageViewModel.uiState = PageUiState(isLoading = false, error = getString(R.string.error_no_article_identifier), title = getString(R.string.title_page_not_specified), plainTextTitle = getString(R.string.title_page_not_specified), pageId = null, htmlContent = null)
            updateUiFromViewModel()
        }
    }

    private fun updateUiFromViewModel() {
        if (!isAdded || !isVisible || _binding == null) return
        val state = pageViewModel.uiState
        binding.progressBar.isVisible = state.isLoading
        state.error?.let {
            binding.errorTextView.text = it; binding.errorTextView.isVisible = true; binding.pageWebView.visibility = View.INVISIBLE
        } ?: run { binding.errorTextView.isVisible = false }

        if (state.isLoading || state.error != null) {
            if (binding.pageWebView.visibility == View.VISIBLE || (state.isLoading && state.htmlContent == null)) {
                binding.pageWebView.visibility = View.INVISIBLE
            }
            if (state.isLoading && state.htmlContent == null && (binding.pageWebView.url == null || binding.pageWebView.url == "about:blank")) {
                binding.pageWebView.loadData("<html></html>", "text/html", "UTF-8")
            }
        } else {
            state.htmlContent?.let { htmlBodySnippet ->
                binding.pageWebView.visibility = View.INVISIBLE
                val currentTheme = (requireActivity().application as OSRSWikiApp).getCurrentTheme()
                pageWebViewManager.render(
                    htmlSnippet = htmlBodySnippet,
                    baseUrl = state.wikiUrl,
                    pageTitle = state.title ?: pageTitleArg,
                    theme = currentTheme
                )
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

        if (titleForDaoLookup.isNullOrBlank()) {
            updateSaveIcon(null)
            return
        }

        pageStateObserverJob = viewLifecycleOwner.lifecycleScope.launch {
            val pagePackageTitle = PagePackagePageTitle(
                namespace = Namespace.MAIN,
                text = titleForDaoLookup,
                wikiSite = WikiSite.OSRS_WIKI
            )
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
        if (!isAdded || _binding == null) {
            return
        }
        val isSaved = entry != null && entry.offline && entry.status == ReadingListPage.STATUS_SAVED
        binding.pageActionsTabLayout.updateActionItemIcon(PageActionItem.SAVE, PageActionItem.getSaveIcon(isSaved))
        Log.d("PFragment_SAVE_TEST", "Save icon updated. IsSaved: $isSaved for apiTitle: ${entry?.apiTitle}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageStateObserverJob?.cancel()
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading(); (webView.parent as? ViewGroup)?.removeView(webView); webView.destroy()
        }
        _binding = null
    }

    companion object {
        const val ARG_PAGE_ID = "pageId"
        const val ARG_PAGE_TITLE = "pageTitle"
        const val ARG_PAGE_SOURCE = "pageSource"
        private const val FRAGMENT_TAG = "PageFragmentTag"
        @JvmStatic
        fun newInstance(pageId: String?, pageTitle: String?, source: Int): PageFragment =
            PageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_ID, pageId)
                    putString(ARG_PAGE_TITLE, pageTitle)
                    putInt(ARG_PAGE_SOURCE, source)
                }
            }
    }
}
