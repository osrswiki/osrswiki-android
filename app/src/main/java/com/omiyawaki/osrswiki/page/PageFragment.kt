package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.content.Intent
import android.widget.Toast
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
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
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.page.model.LeadSectionDetails
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.page.model.TocData
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.settings.AppearanceSettingsActivity
import com.omiyawaki.osrswiki.views.ObservableWebView
import com.omiyawaki.osrswiki.feedback.ReportIssueActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.abs

class PageFragment : Fragment(), RenderCallback, ThemeAware {

    interface Callback {
        fun onPageStartActionMode(callback: ActionMode.Callback)
        fun onPageStopActionMode()
        fun onPageFinishActionMode()
        fun onWebViewReady(webView: ObservableWebView)
        fun getPageToolbarContainer(): View
        fun getPageActionBarManager(): PageActionBarManager
        fun onPageSwipe(gravity: Int)
    }

    private var _binding: FragmentPageBinding? = null
    val binding get() = _binding!!

    private lateinit var pageViewModel: PageViewModel
    private lateinit var pageRepository: PageRepository
    private lateinit var readingListPageDao: ReadingListPageDao
    private lateinit var contentsHandler: ContentsHandler
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var pageLinkHandler: PageLinkHandler
    private lateinit var pageWebViewManager: PageWebViewManager
    private lateinit var pageLoadCoordinator: PageLoadCoordinator
    private lateinit var pageHistoryManager: PageHistoryManager
    private lateinit var pageReadingListManager: PageReadingListManager
    private lateinit var pageUiUpdater: PageUiUpdater
    private lateinit var gestureDetector: GestureDetector
    private lateinit var nativeMapHandler: NativeMapHandler

    private var callback: Callback? = null
    private var isFindInPageActive = false

    private var pageIdArg: String? = null
    private var pageTitleArg: String? = null
    private var navigationSource: Int = HistoryEntry.SOURCE_INTERNAL_LINK
    private var snippetArg: String? = null
    private var thumbnailUrlArg: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? Callback ?: throw RuntimeException("$context must implement PageFragment.Callback")
        // Proactively warm up the database on a background thread.
        // This triggers the potentially slow, one-time database creation/migration
        // so it doesn't block the main thread later when it's first accessed.
        lifecycleScope.launch(Dispatchers.IO) {
            L.d("Warming up database instance...")
            AppDatabase.instance
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pageIdArg = it.getString(ARG_PAGE_ID)
            pageTitleArg = it.getString(ARG_PAGE_TITLE)
            navigationSource = it.getInt(ARG_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)
            snippetArg = it.getString(ARG_PAGE_SNIPPET)
            thumbnailUrlArg = it.getString(ARG_PAGE_THUMBNAIL)
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
        callback?.onWebViewReady(binding.pageWebView)
        setupGestureDetector()
        contentsHandler = ContentsHandler(this)
        nativeMapHandler = NativeMapHandler(this, binding)
        val app = requireActivity().application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()
        val backgroundColorRes = when (currentTheme) {
            Theme.OSRS_DARK -> R.color.osrs_parchment_dark
            else -> R.color.osrs_parchment_light
        }
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColorRes))

        pageLinkHandler =
            PageLinkHandler(requireContext(), viewLifecycleOwner.lifecycleScope, pageRepository, currentTheme)
        pageHistoryManager = PageHistoryManager(pageViewModel, viewLifecycleOwner.lifecycleScope) { this }

        pageWebViewManager = PageWebViewManager(
            webView = binding.pageWebView,
            linkHandler = pageLinkHandler,
            onTitleReceived = { newTitle ->
                if (isAdded) {
                    val plainTextTitle =
                        HtmlCompat.fromHtml(newTitle, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    (activity as? AppCompatActivity)?.supportActionBar?.title = plainTextTitle
                }
            },
            jsInterface = nativeMapHandler.jsInterface,
            jsInterfaceName = "OsrsWikiBridge",
            renderCallback = this,
            onRenderProgress = { progress ->
                pageContentLoader.updateRenderProgress(progress)
            }
        )


        pageReadingListManager = PageReadingListManager(
            pageViewModel,
            readingListPageDao,
            viewLifecycleOwner.lifecycleScope,
            callback?.getPageActionBarManager(),
            ::getPageTitleArg,
            requireContext().applicationContext,
            { snippetArg },
            { thumbnailUrlArg }
        )
        pageUiUpdater = PageUiUpdater(binding, pageViewModel, pageWebViewManager) { this }
        val pageHtmlBuilder = PageHtmlBuilder(requireContext().applicationContext)
        val pageAssetDownloader = PageAssetDownloader(OkHttpClientFactory.offlineClient, pageRepository)

        pageContentLoader = PageContentLoader(
            context = requireContext().applicationContext,
            pageRepository = pageRepository,
            pageAssetDownloader = pageAssetDownloader,
            pageHtmlBuilder = pageHtmlBuilder,
            pageViewModel = pageViewModel,
            coroutineScope = viewLifecycleOwner.lifecycleScope
        ) {
            if (isAdded && _binding != null) {
                pageUiUpdater.updateUi()
                pageReadingListManager.observeAndRefreshSaveButtonState()
            }
        }

        pageLoadCoordinator = PageLoadCoordinator(pageViewModel, pageContentLoader, pageUiUpdater) { this }
        pageLoadCoordinator.initiatePageLoad(currentTheme, forceNetwork = false)
        binding.errorTextView.setOnClickListener {
            reloadCurrentPage()
        }

        // Setup the bottom action bar
        setupBottomActionBar()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            private val swipeThreshold = 100
            private val swipeVelocityThreshold = 100

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) {
                    return false
                }
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                var result = false
                try {
                    L.d("Gesture: dx=${dx.toInt()}, dy=${dy.toInt()}, velX=${velocityX.toInt()}, mapScrolling=${nativeMapHandler.isHorizontalScrollInProgress}")
                    
                    if (nativeMapHandler.isHorizontalScrollInProgress) {
                        L.d("Gesture: Blocked by map horizontal scroll")
                        return false
                    }
                    if (abs(dx) > abs(dy) &&
                        abs(dx) > swipeThreshold &&
                        abs(velocityX) > swipeVelocityThreshold
                    ) {
                        val direction = if (dx > 0) "START" else "END"
                        L.d("Gesture: Valid swipe detected, direction=$direction")

                        if (dx > 0) {
                            callback?.onPageSwipe(Gravity.START)
                        } else {
                            callback?.onPageSwipe(Gravity.END)
                        }
                        result = true
                    } else {
                        L.d("Gesture: Failed thresholds - dx_vs_dy=${abs(dx) > abs(dy)}, dx_threshold=${abs(dx) > swipeThreshold}, vel_threshold=${abs(velocityX) > swipeVelocityThreshold}")
                    }
                } catch (exception: Exception) {
                    L.e("Error during swipe detection.", exception)
                }
                return result
            }
        }
        gestureDetector = GestureDetector(requireContext(), gestureListener)
        binding.pageWebView.setOnTouchListener { _, event ->
            // If JS signaled an active horizontal interaction (e.g., Highcharts),
            // do not feed events into the back-swipe GestureDetector.
            if (::nativeMapHandler.isInitialized && nativeMapHandler.isHorizontalScrollInProgress) {
                return@setOnTouchListener false
            }
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun fetchTableOfContents() {
        val script = """
            (function() {
                var tocData = { leadSectionDetails: null, sections: [] };
                var headerSpans = document.querySelectorAll('.mw-headline');
                for (var i = 0; i < headerSpans.length; i++) {
                    var span = headerSpans[i];
                    var header = span.parentElement;
                    if (span.id && (header.tagName === 'H2' || header.tagName === 'H3')) {
                        var level = parseInt(header.tagName.substring(1));
                        var computedStyle = window.getComputedStyle(span);
                        tocData.sections.push({
                            id: i + 1, level: level, anchor: span.id, title: span.textContent.trim(),
                            isItalic: computedStyle.fontStyle === 'italic',
                            isBold: parseInt(computedStyle.fontWeight) >= 700 || computedStyle.fontWeight === 'bold' || computedStyle.fontWeight === 'bolder'
                        });
                    }
                }
                var leadHeader = document.querySelector('h1.page-header');
                if (leadHeader) {
                    var leadStyle = window.getComputedStyle(leadHeader);
                    tocData.leadSectionDetails = {
                        title: leadHeader.textContent.trim(),
                        isItalic: leadStyle.fontStyle === 'italic',
                        isBold: parseInt(leadStyle.fontWeight) >= 700 || leadStyle.fontWeight === 'bold' || leadStyle.fontWeight === 'bolder'
                    };
                }
                return JSON.stringify(tocData);
            })();
        """.trimIndent()
        binding.pageWebView.evaluateJavascript(script) { jsonString ->
            // Coroutine to move parsing off the main thread.
            viewLifecycleOwner.lifecycleScope.launch {
                if (jsonString != null && jsonString != "null" && jsonString != "\"\"") {
                    try {
                        val fullToc = withContext(Dispatchers.Default) {
                            // Heavy parsing now happens on a background thread.
                            val unescapedJson = Json.decodeFromString<String>(jsonString)
                            val tocData = Json.decodeFromString<TocData>(unescapedJson)

                            val leadSection = tocData.leadSectionDetails?.let {
                                Section(0, 1, "", it.title, it.isItalic, it.isBold)
                            } ?: run {
                                val fallbackTitle =
                                    (activity as? AppCompatActivity)?.supportActionBar?.title?.toString() ?: "Top of page"
                                Section(0, 1, "", fallbackTitle, isItalic = false, isBold = true)
                            }
                            mutableListOf(leadSection).apply { addAll(tocData.sections) }
                        }
                        // Switch back to the main thread to update the UI.
                        contentsHandler.setup(fullToc)
                    } catch (e: Exception) {
                        L.e("Failed to parse TOC JSON", e)
                    }
                }
            }
        }
    }

    override fun onWebViewLoadFinished() {
        pageContentLoader.updateRenderProgress(98)
    }

    override fun onPageReadyForDisplay() {
        if (isAdded && _binding != null) {
            pageWebViewManager.finalizeAndRevealPage {
                // This block is now the final step. It runs only after the WebView
                // content is visible. Now we can safely set the final state.
                pageContentLoader.onPageRendered() // Sets progress to 100% and isLoading=false
                pageHistoryManager.logPageVisit(
                    snippet = snippetArg,
                    thumbnailUrl = thumbnailUrlArg
                ) // Log history after isLoading is set to false
                fetchTableOfContents()
                binding.pageWebView.evaluateJavascript("javascript:measureAndPreloadMaps();", null)
            }
        }
    }

    fun showFindInPage() {
        if (isFindInPageActive) return
        val script =
            "document.querySelectorAll('.collapsible-closed').forEach(function(e) { e.classList.remove('collapsible-closed'); });"
        binding.pageWebView.evaluateJavascript(script, null)
        val manager = FindInPageManager(requireContext(), binding.pageWebView) {
            isFindInPageActive = false
            callback?.onPageStopActionMode()
        }
        isFindInPageActive = true
        callback?.onPageStartActionMode(manager)
    }

    fun showContents() {
        contentsHandler.show()
    }

    private fun setupBottomActionBar() {
        val actionBarManager = callback?.getPageActionBarManager()
        actionBarManager?.setupActionBar(this)
    }

    fun showPageOverflowMenu(anchorView: View) {
        if (isAdded) {
            val popup = PopupMenu(requireContext(), anchorView)
            popup.menuInflater.inflate(R.menu.menu_page_overflow, popup.menu)
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_page_share -> {
                        handleSharePage()
                        true
                    }
                    R.id.menu_page_go_to_top -> {
                        handleGoToTop()
                        true
                    }
                    R.id.menu_page_copy_link -> {
                        handleCopyLink()
                        true
                    }
                    R.id.menu_page_refresh -> {
                        handleRefreshPage()
                        true
                    }
                    R.id.menu_page_open_browser -> {
                        handleOpenInBrowser()
                        true
                    }
                    R.id.menu_page_view_history -> {
                        handleViewPageHistory()
                        true
                    }
                    R.id.menu_page_report_issue -> {
                        handleReportIssue()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun handleSharePage() {
        val pageTitle = pageTitleArg ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://oldschool.runescape.wiki/w/${pageTitle.replace(" ", "_")}")
            putExtra(Intent.EXTRA_SUBJECT, pageTitle)
        }
        startActivity(Intent.createChooser(shareIntent, "Share page"))
    }

    private fun handleGoToTop() {
        binding.pageWebView.scrollTo(0, 0)
        Toast.makeText(requireContext(), "Scrolled to top", Toast.LENGTH_SHORT).show()
    }

    private fun handleCopyLink() {
        val pageTitle = pageTitleArg ?: return
        val url = "https://oldschool.runescape.wiki/w/${pageTitle.replace(" ", "_")}"
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Page URL", url)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun reloadCurrentPage() {
        val app = requireActivity().application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()
        pageLoadCoordinator.initiatePageLoad(currentTheme, forceNetwork = true)
    }

    private fun handleRefreshPage() {
        // Reset UI state to exactly match normal loading initial conditions
        pageViewModel.uiState = pageViewModel.uiState.copy(
            htmlContent = null,
            isLoading = false,
            progress = null,
            progressText = null,
            error = null
        )
        pageUiUpdater.updateUi()
        
        // Serial approach: Clear WebView FIRST, THEN start normal loading
        val originalWebViewClient = binding.pageWebView.webViewClient
        binding.pageWebView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                if (url == "about:blank") {
                    // Blank page loaded, now restore original client and start normal loading
                    binding.pageWebView.webViewClient = originalWebViewClient
                    reloadCurrentPage()
                }
            }
        }
        binding.pageWebView.loadUrl("about:blank")
    }

    private fun handleOpenInBrowser() {
        val pageTitle = pageTitleArg ?: return
        val url = "https://oldschool.runescape.wiki/w/${pageTitle.replace(" ", "_")}"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No browser app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleViewPageHistory() {
        val pageTitle = pageTitleArg ?: return
        val historyUrl = "https://oldschool.runescape.wiki/w/Special:History/${pageTitle.replace(" ", "_")}"
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(historyUrl))
        try {
            startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open page history", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleReportIssue() {
        val intent = ReportIssueActivity.newIntent(requireContext())
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        callback?.onPageFinishActionMode()
        pageReadingListManager.cancelObserving()
        // Clean up any maps created in this page to prevent bleed-through
        if (::nativeMapHandler.isInitialized) {
            nativeMapHandler.cleanup()
        }
        _binding?.pageWebView?.destroy()
        _binding = null
    }



    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    fun getPageIdArg(): String? = pageIdArg
    fun getPageTitleArg(): String? = pageTitleArg
    fun getNavigationSource(): Int = navigationSource
    fun provideBinding(): FragmentPageBinding? = _binding

    override fun onThemeChanged() {
        if (!isAdded || _binding == null) {
            return
        }
        
        L.d("PageFragment: Theme changed, updating WebView theme")
        val app = requireActivity().application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()
        
        // Update fragment background color immediately
        val backgroundColorRes = when (currentTheme) {
            Theme.OSRS_DARK -> R.color.osrs_parchment_dark
            else -> R.color.osrs_parchment_light
        }
        view?.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColorRes))
        
        // Update WebView theme instantly via JavaScript without reload
        updateWebViewTheme(currentTheme)
    }
    
    private fun updateWebViewTheme(theme: Theme) {
        val isDark = theme.isDark()
        
        // Use the theme utility script for instant theme switching
        val script = "if (window.OSRSWikiTheme) { window.OSRSWikiTheme.switchTheme($isDark); }"
        
        binding.pageWebView.evaluateJavascript(script) { result ->
            L.d("PageFragment: WebView theme updated to: $theme, result: $result")
        }
    }

    companion object {
        const val ARG_PAGE_ID = "pageId"
        const val ARG_PAGE_TITLE = "pageTitle"
        const val ARG_PAGE_SOURCE = "pageSource"
        const val ARG_PAGE_SNIPPET = "pageSnippet"
        const val ARG_PAGE_THUMBNAIL = "pageThumbnail"
        @JvmStatic
        fun newInstance(
            pageId: String?, 
            pageTitle: String?, 
            source: Int,
            snippet: String? = null,
            thumbnailUrl: String? = null
        ): PageFragment = PageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PAGE_ID, pageId)
                putString(ARG_PAGE_TITLE, pageTitle)
                putInt(ARG_PAGE_SOURCE, source)
                putString(ARG_PAGE_SNIPPET, snippet)
                putString(ARG_PAGE_THUMBNAIL, thumbnailUrl)
            }
        }
    }
}
