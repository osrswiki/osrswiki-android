package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    private val binding get() = _binding!!

    private lateinit var pageViewModel: PageViewModel
    private lateinit var pageRepository: PageRepository
    private lateinit var readingListPageDao: ReadingListPageDao

    // Refactored Helper Classes
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var pageLinkHandler: PageLinkHandler
    private lateinit var pageWebViewManager: PageWebViewManager
    private lateinit var pageActionHandler: PageActionHandler
    private lateinit var pageLoadCoordinator: PageLoadCoordinator
    private lateinit var pageHistoryManager: PageHistoryManager
    private lateinit var pageReadingListManager: PageReadingListManager
    private lateinit var pageHtmlFactory: PageHtmlFactory
    private lateinit var pageUiUpdater: PageUiUpdater

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null
    private var navigationSource: Int = HistoryEntry.SOURCE_INTERNAL_LINK

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

        pageHistoryManager = PageHistoryManager(pageViewModel, viewLifecycleOwner.lifecycleScope) { this }

        pageWebViewManager = PageWebViewManager(
            context = requireContext(),
            webView = binding.pageWebView,
            linkHandler = pageLinkHandler,
            onPageReady = {
                if (isAdded && _binding != null) {
                    binding.pageWebView.visibility = View.VISIBLE
                    pageHistoryManager.logPageVisit()
                }
            }
        )

        pageActionHandler = PageActionHandler(this, pageViewModel, binding)

        pageHtmlFactory = PageHtmlFactory()

        pageUiUpdater = PageUiUpdater(binding, pageViewModel, pageWebViewManager, pageHtmlFactory) { this }

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
                pageUiUpdater.updateUi()
                pageReadingListManager.observeAndRefreshSaveButtonState()
            }
        }

        pageLoadCoordinator = PageLoadCoordinator(pageViewModel, pageContentLoader, pageUiUpdater) { this }

        pageReadingListManager = PageReadingListManager(pageViewModel, readingListPageDao, viewLifecycleOwner.lifecycleScope) { this }

        binding.pageActionsTabLayout.callback = pageActionHandler.callback
        binding.pageActionsTabLayout.update()

        pageUiUpdater.updateUi()
        pageLoadCoordinator.initiatePageLoad(forceNetwork = false)
        pageReadingListManager.observeAndRefreshSaveButtonState()

        binding.errorTextView.setOnClickListener { pageLoadCoordinator.initiatePageLoad(forceNetwork = true) }
    }

    fun showPageOverflowMenu(anchorView: View) {
        if (!isAdded) return
        pageActionHandler.showPageOverflowMenu(anchorView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageReadingListManager.cancelObserving()
        _binding?.pageWebView?.let { webView ->
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        _binding = null
    }

    // Accessor methods for helper classes that need fragment context
    fun getPageIdArg(): String? = pageIdArg
    fun getPageTitleArg(): String? = pageTitleArg
    fun getNavigationSource(): Int = navigationSource
    fun provideBinding(): FragmentPageBinding? = _binding

    companion object {
        const val ARG_PAGE_ID = "pageId"
        const val ARG_PAGE_TITLE = "pageTitle"
        const val ARG_PAGE_SOURCE = "pageSource"

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
