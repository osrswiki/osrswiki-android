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
    private var contentsHandler: ContentsHandler? = null
    private lateinit var pageContentLoader: PageContentLoader
    private lateinit var pageLinkHandler: PageLinkHandler
    private var pageWebViewManager: PageWebViewManager? = null
    private var pageLoadCoordinator: PageLoadCoordinator? = null
    private lateinit var pageHistoryManager: PageHistoryManager
    private lateinit var pageReadingListManager: PageReadingListManager
    private var pageUiUpdater: PageUiUpdater? = null
    private lateinit var gestureDetector: GestureDetector
    private var nativeMapHandler: NativeMapHandler? = null

    private var callback: Callback? = null
    private var isFindInPageActive = false
    private var webViewReleasedWhileStopped = false
    private var releasedWebViewIndex = -1
    private var releasedWebViewLayoutParams: ViewGroup.LayoutParams? = null
    private var releasedRootView: ViewGroup? = null
    private var releasedWebViewScrollY = 0
    private var shouldRestoreReleasedWebViewScroll = false

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
        
        // Skip ContentsHandler and NativeMapHandler creation in debug mode (Coins page test)
        if (System.getProperty("debug.coins.test") != "true") {
            contentsHandler = ContentsHandler(this)
            nativeMapHandler = NativeMapHandler(this, binding)
        }
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

        val webViewManager = createPageWebViewManager()
        pageWebViewManager = webViewManager


        pageReadingListManager = PageReadingListManager(
            pageViewModel,
            readingListPageDao,
            viewLifecycleOwner.lifecycleScope,
            if (System.getProperty("debug.coins.test") == "true") null else callback?.getPageActionBarManager(),
            ::getPageTitleArg,
            requireContext().applicationContext,
            { snippetArg },
            { thumbnailUrlArg }
        )
        val uiUpdater = PageUiUpdater(binding, pageViewModel, webViewManager) { this }
        pageUiUpdater = uiUpdater
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
            if (isAdded && _binding != null && !webViewReleasedWhileStopped) {
                pageUiUpdater?.updateUi()
                pageReadingListManager.observeAndRefreshSaveButtonState()
            }
        }

        pageLoadCoordinator = PageLoadCoordinator(pageViewModel, pageContentLoader, uiUpdater) { this }
        pageLoadCoordinator?.initiatePageLoad(currentTheme, forceNetwork = false)
        binding.errorTextView.setOnClickListener {
            reloadCurrentPage()
        }

        // Setup the bottom action bar
        setupBottomActionBar()
    }

    private fun createPageWebViewManager(): PageWebViewManager {
        return PageWebViewManager(
            webView = binding.pageWebView,
            linkHandler = pageLinkHandler,
            onTitleReceived = { newTitle ->
                if (isAdded) {
                    val plainTextTitle =
                        HtmlCompat.fromHtml(newTitle, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    (activity as? AppCompatActivity)?.supportActionBar?.title = plainTextTitle
                }
            },
            jsInterface = nativeMapHandler?.jsInterface ?: null,
            jsInterfaceName = "OsrsWikiBridge",
            renderCallback = this,
            onRenderProgress = { progress ->
                pageContentLoader.updateRenderProgress(progress)
            },
            onRenderProcessGone = {
                handleWebViewRendererGone()
            }
        )
    }

    private fun handleWebViewRendererGone() {
        if (!isAdded || _binding == null) {
            return
        }
        L.w("PageFragment: WebView renderer gone; rebuilding article WebView and showing retry state.")
        nativeMapHandler?.cleanup()
        rebuildPageWebView()
        pageContentLoader.onRenderFailed(getString(R.string.error_webview_renderer_recovery))
        pageUiUpdater?.updateUi()
    }

    private fun rebuildPageWebView() {
        val currentBinding = _binding ?: return
        val root = currentBinding.root
        val oldWebView = currentBinding.pageWebView
        val oldIndex = root.indexOfChild(oldWebView).takeIf { it >= 0 } ?: 0
        val oldLayoutParams = oldWebView.layoutParams

        root.removeView(oldWebView)
        oldWebView.stopLoading()
        oldWebView.webChromeClient = null
        oldWebView.webViewClient = android.webkit.WebViewClient()
        oldWebView.destroy()

        val replacement = ObservableWebView(requireContext()).apply {
            id = R.id.page_web_view
            layoutParams = oldLayoutParams
            visibility = View.GONE
            clipToPadding = false
        }
        root.addView(replacement, oldIndex)
        _binding = FragmentPageBinding.bind(root)
        callback?.onWebViewReady(binding.pageWebView)

        if (System.getProperty("debug.coins.test") != "true") {
            nativeMapHandler = NativeMapHandler(this, binding)
        }
        setupGestureDetector()
        val webViewManager = createPageWebViewManager()
        pageWebViewManager = webViewManager
        pageUiUpdater = PageUiUpdater(binding, pageViewModel, webViewManager) { this }
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
                    val isMapScrolling = nativeMapHandler?.isHorizontalScrollInProgress ?: false
                    L.d("Gesture: dx=${dx.toInt()}, dy=${dy.toInt()}, velX=${velocityX.toInt()}, mapScrolling=${isMapScrolling}")
                    
                    if (isMapScrolling) {
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
            if (nativeMapHandler?.isHorizontalScrollInProgress == true) {
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
        val currentBinding = _binding ?: return
        val ownerScope = viewLifecycleOwner.lifecycleScope
        currentBinding.pageWebView.evaluateJavascript(script) { jsonString ->
            if (_binding == null || webViewReleasedWhileStopped) {
                return@evaluateJavascript
            }
            // Coroutine to move parsing off the main thread.
            ownerScope.launch {
                if (_binding == null || webViewReleasedWhileStopped) {
                    return@launch
                }
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
                        contentsHandler?.setup(fullToc)
                    } catch (e: Exception) {
                        L.e("Failed to parse TOC JSON", e)
                    }
                }
            }
        }
    }

    override fun onWebViewLoadFinished() {
        if (!::pageContentLoader.isInitialized || _binding == null || webViewReleasedWhileStopped) {
            return
        }
        pageContentLoader.updateRenderProgress(98)
    }

    override fun onPageReadyForDisplay() {
        val webViewManager = pageWebViewManager ?: return
        if (isAdded && _binding != null && !webViewReleasedWhileStopped) {
            webViewManager.finalizeAndRevealPage {
                val currentBinding = _binding ?: return@finalizeAndRevealPage
                if (!isAdded || webViewReleasedWhileStopped) {
                    return@finalizeAndRevealPage
                }
                // This block is now the final step. It runs only after the WebView
                // content is visible. Now we can safely set the final state.
                pageContentLoader.onPageRendered() // Sets progress to 100% and isLoading=false
                pageHistoryManager.logPageVisit(
                    snippet = snippetArg,
                    thumbnailUrl = thumbnailUrlArg
                ) // Log history after isLoading is set to false
                restoreReleasedWebViewScrollPositionIfNeeded(currentBinding.pageWebView)
                val extractedSections = pageViewModel.uiState.tableOfContentsSections
                if (extractedSections.isNotEmpty()) {
                    contentsHandler?.setup(extractedSections)
                } else {
                    fetchTableOfContents()
                }
                currentBinding.pageWebView.evaluateJavascript("javascript:measureAndPreloadMaps();", null)
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
        contentsHandler?.show()
    }
    
    fun toggleContents() {
        contentsHandler?.let {
            if (it.isVisible()) {
                it.hide()
            } else {
                it.show()
            }
        }
    }
    
    fun toggleFindInPage() {
        if (isFindInPageActive) {
            callback?.onPageFinishActionMode()
        } else {
            showFindInPage()
        }
    }

    private fun setupBottomActionBar() {
        if (System.getProperty("debug.coins.test") == "true") {
            // Skip action bar setup in debug mode
            return
        }
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
        pageLoadCoordinator?.initiatePageLoad(currentTheme, forceNetwork = true)
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
        pageUiUpdater?.updateUi()
        
        // Serial approach: Clear WebView FIRST, THEN start normal loading
        binding.pageWebView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                if (url == "about:blank") {
                    // Blank page loaded, now restore the managed client and start normal loading
                    pageWebViewManager?.restoreManagedWebViewClient()
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

    override fun onStart() {
        super.onStart()
        restoreStoppedWebViewResourcesIfNeeded()
    }

    override fun onStop() {
        releaseStoppedWebViewResources()
        super.onStop()
    }

    override fun onPause() {
        releaseStoppedWebViewResources()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        restoreStoppedWebViewResourcesIfNeeded()
    }

    private fun releaseStoppedWebViewResources() {
        val currentBinding = _binding ?: return
        val webViewManager = pageWebViewManager ?: return
        if (webViewReleasedWhileStopped) {
            return
        }
        if (activity?.isChangingConfigurations == true) {
            return
        }

        webViewReleasedWhileStopped = true
        if (::pageContentLoader.isInitialized) {
            pageContentLoader.cancelActivePageWork()
        }
        webViewManager.dispose()
        nativeMapHandler?.cleanup()
        nativeMapHandler = null
        contentsHandler = null

        val root = currentBinding.root
        val oldWebView = currentBinding.pageWebView
        val oldIndex = root.indexOfChild(oldWebView).takeIf { it >= 0 } ?: 0
        val oldLayoutParams = oldWebView.layoutParams
        releasedWebViewScrollY = oldWebView.scrollY
        shouldRestoreReleasedWebViewScroll = releasedWebViewScrollY > 0 &&
            pageViewModel.uiState.htmlContent != null
        releasedWebViewIndex = oldIndex
        releasedWebViewLayoutParams = oldLayoutParams
        releasedRootView = root

        root.removeView(oldWebView)
        destroyReleasedWebView(oldWebView)
        pageWebViewManager = null
        pageUiUpdater = null
        pageLoadCoordinator = null
        _binding = null

        pageViewModel.uiState = pageViewModel.uiState.copy(
            isLoading = false,
            progress = null,
            progressText = null
        )
    }

    private fun restoreStoppedWebViewResourcesIfNeeded() {
        if (!webViewReleasedWhileStopped || !isAdded) {
            return
        }
        if (!::pageLinkHandler.isInitialized || !::pageContentLoader.isInitialized) {
            return
        }

        val root = releasedRootView ?: _binding?.root ?: return
        if (root.findViewById<ObservableWebView>(R.id.page_web_view) == null) {
            val replacement = ObservableWebView(requireContext()).apply {
                id = R.id.page_web_view
                layoutParams = releasedWebViewLayoutParams
                    ?: ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                visibility = View.GONE
                clipToPadding = false
            }
            val insertionIndex = releasedWebViewIndex.takeIf { it >= 0 } ?: 0
            root.addView(replacement, insertionIndex)
        }
        _binding = FragmentPageBinding.bind(root)
        releasedWebViewIndex = -1
        releasedWebViewLayoutParams = null
        releasedRootView = null
        webViewReleasedWhileStopped = false
        callback?.onWebViewReady(binding.pageWebView)
        if (System.getProperty("debug.coins.test") != "true") {
            contentsHandler = ContentsHandler(this)
            nativeMapHandler = NativeMapHandler(this, binding)
        }
        setupGestureDetector()
        val webViewManager = createPageWebViewManager()
        pageWebViewManager = webViewManager
        val uiUpdater = PageUiUpdater(binding, pageViewModel, webViewManager) { this }
        pageUiUpdater = uiUpdater
        pageLoadCoordinator = PageLoadCoordinator(pageViewModel, pageContentLoader, uiUpdater) { this }

        val app = requireActivity().application as OSRSWikiApp
        pageLoadCoordinator?.initiatePageLoad(app.getCurrentTheme(), forceNetwork = false)
    }

    private fun restoreReleasedWebViewScrollPositionIfNeeded(webView: ObservableWebView) {
        if (!shouldRestoreReleasedWebViewScroll) {
            return
        }
        webView.post {
            webView.scrollTo(0, releasedWebViewScrollY)
            releasedWebViewScrollY = 0
            shouldRestoreReleasedWebViewScroll = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        callback?.onPageFinishActionMode()
        if (::pageContentLoader.isInitialized) {
            pageContentLoader.cancelActivePageWork()
        }
        pageWebViewManager?.dispose()
        if (::pageReadingListManager.isInitialized) {
            pageReadingListManager.cancelObserving()
        }
        // Clean up any maps created in this page to prevent bleed-through
        nativeMapHandler?.cleanup()
        _binding?.pageWebView?.run {
            destroyReleasedWebView(this)
        }
        webViewReleasedWhileStopped = false
        releasedRootView = null
        contentsHandler = null
        pageWebViewManager = null
        pageUiUpdater = null
        pageLoadCoordinator = null
        _binding = null
        releasedWebViewScrollY = 0
        shouldRestoreReleasedWebViewScroll = false
    }

    private fun destroyReleasedWebView(webView: ObservableWebView) {
        runCatching { webView.setOnTouchListener(null) }
        runCatching { webView.setOnScrollChangeListener(null) }
        runCatching { webView.onPause() }
        runCatching { webView.stopLoading() }
        runCatching { webView.loadUrl("about:blank") }
        runCatching { webView.clearHistory() }
        runCatching { webView.clearMatches() }
        runCatching { webView.removeAllViews() }
        runCatching { webView.webChromeClient = null }
        runCatching { webView.webViewClient = android.webkit.WebViewClient() }
        runCatching { webView.destroy() }
        requestWebViewHeapTrim()
    }

    private fun requestWebViewHeapTrim() {
        webViewReleaseCount += 1
        if (webViewReleaseCount % WEBVIEW_RELEASES_PER_HEAP_TRIM == 0) {
            System.gc()
            System.runFinalization()
        }
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
        private const val WEBVIEW_RELEASES_PER_HEAP_TRIM = 8
        private var webViewReleaseCount = 0
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
