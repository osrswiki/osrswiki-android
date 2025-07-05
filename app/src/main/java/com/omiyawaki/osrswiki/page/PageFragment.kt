package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.FragmentPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.serialization.json.Json

class PageFragment : Fragment() {

    private var _binding: FragmentPageBinding? = null
    val binding get() = _binding!!

    private lateinit var pageViewModel: PageViewModel
    private lateinit var pageRepository: PageRepository
    private lateinit var readingListPageDao: ReadingListPageDao

    // The PageFragment now owns the ContentsHandler.
    private lateinit var contentsHandler: ContentsHandler

    // Helper Classes
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var pageLinkHandler: PageLinkHandler
    private lateinit var pageWebViewManager: PageWebViewManager
    private lateinit var pageActionHandler: PageActionHandler
    private lateinit var pageLoadCoordinator: PageLoadCoordinator
    private lateinit var pageHistoryManager: PageHistoryManager
    private lateinit var pageReadingListManager: PageReadingListManager
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

        // Instantiate the ContentsHandler here. Its init block will handle wiring listeners.
        contentsHandler = ContentsHandler(this)

        val app = requireActivity().application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()

        val backgroundColorRes = when (currentTheme) {
            Theme.OSRS_DARK -> R.color.osrs_parchment_dark
            Theme.WIKI_LIGHT -> R.color.white
            Theme.WIKI_DARK -> R.color.page_bg_wiki_dark
            Theme.WIKI_BLACK -> R.color.black
            else -> R.color.osrs_parchment_light
        }
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColorRes))

        pageLinkHandler = PageLinkHandler(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            pageRepository,
            currentTheme
        )

        pageHistoryManager = PageHistoryManager(pageViewModel, viewLifecycleOwner.lifecycleScope) { this }

        pageWebViewManager = PageWebViewManager(
            webView = binding.pageWebView,
            linkHandler = pageLinkHandler,
            onPageReady = {
                if (isAdded && _binding != null) {
                    binding.pageWebView.visibility = View.VISIBLE
                    pageHistoryManager.logPageVisit()
                    fetchTableOfContents()
                }
            },
            onTitleReceived = { newTitle ->
                if (isAdded) {
                    val plainTextTitle = HtmlCompat.fromHtml(newTitle, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    (activity as? AppCompatActivity)?.supportActionBar?.title = plainTextTitle
                }
            }
        )

        pageActionHandler = PageActionHandler(this, pageViewModel, binding)

        pageUiUpdater = PageUiUpdater(binding, pageViewModel, pageWebViewManager) { this }

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
        pageLoadCoordinator.initiatePageLoad(currentTheme, forceNetwork = false)
        pageReadingListManager.observeAndRefreshSaveButtonState()

        binding.errorTextView.setOnClickListener { pageLoadCoordinator.initiatePageLoad(currentTheme, forceNetwork = true) }
    }

    private fun fetchTableOfContents() {
        val script = """
            (function() {
                var sections = [];
                var headerSpans = document.querySelectorAll('.mw-headline');
                for (var i = 0; i < headerSpans.length; i++) {
                    var span = headerSpans[i];
                    var header = span.parentElement;
                    if (span.id && (header.tagName === 'H2' || header.tagName === 'H3')) {
                        var level = parseInt(header.tagName.substring(1));
                        sections.push({
                            id: i + 1,
                            level: level,
                            anchor: span.id,
                            title: span.textContent.trim()
                        });
                    }
                }
                return JSON.stringify(sections);
            })();
        """
        binding.pageWebView.evaluateJavascript(script) { jsonString ->
            if (jsonString != null && jsonString != "null") {
                try {
                    val cleanedJson = jsonString.removeSurrounding("\"").replace("\\\"", "\"")
                    val sections = Json.decodeFromString<List<Section>>(cleanedJson)
                    val title = (activity as? AppCompatActivity)?.supportActionBar?.title?.toString() ?: "Top of page"
                    val leadSection = Section(0, 1, "", title)
                    val fullToc = mutableListOf(leadSection).apply { addAll(sections) }
                    // The fragment now directly tells its own handler to set up the contents.
                    contentsHandler.setup(fullToc)
                } catch (e: Exception) {
                    L.e("Failed to parse TOC JSON", e)
                }
            }
        }
    }

    fun showContents() {
        // The fragment tells its own handler to show.
        contentsHandler.show()
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
