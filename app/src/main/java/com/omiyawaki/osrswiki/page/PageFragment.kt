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
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.network.OkHttpClientFactory
import com.omiyawaki.osrswiki.network.RetrofitClient
import com.omiyawaki.osrswiki.page.model.LeadSectionDetails
import com.omiyawaki.osrswiki.page.model.Section
import com.omiyawaki.osrswiki.page.model.TocData
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.db.ReadingListPageDao
import com.omiyawaki.osrswiki.savedpages.SavedPageSyncWorker
import com.omiyawaki.osrswiki.savedpages.osrsSavedPageRevisionProbe
import com.omiyawaki.osrswiki.settings.osrsDownloadSettings
import com.omiyawaki.osrswiki.settings.osrsSavedPageUpdateTrigger
import com.omiyawaki.osrswiki.theme.Theme
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.settings.AppearanceSettingsActivity
import com.omiyawaki.osrswiki.settings.Prefs
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
        fun onPageSwipe(gravity: Int, velocityX: Float = 0f)
        fun onPageSwipeProgress(gravity: Int, progress: Float) {}
        fun onPageSwipeCancelled() {}
        fun isContentsDrawerOpen(): Boolean = false
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
    private val horizontalGestureOwnership = ArticleHorizontalGestureOwnership()
    private var lastAppliedCollapsePreference = Prefs.isCollapseTablesEnabled
    private var lastAppliedFloorNumberingMode = Prefs.floorNumberingMode
    private var lastAppliedWrapTableCells = Prefs.wrapTableCells
    private var lastPointerDownX = Float.NaN
    private var lastPointerDownY = Float.NaN
    private var lastPointerDownRawX = Float.NaN
    private var lastPointerDownRawY = Float.NaN
    private var interactiveSwipe: osrsArticleInteractiveSwipe? = null
    private var lastInteractiveDx = 0f
    private var lastInteractiveVx = 0f
    private var lastInteractiveEventX = 0f
    private var lastInteractiveEventTime = 0L
    private var consumedInteractiveSwipe = false
    private var injectingWebViewCancel = false
    private var ignoreInteractiveSwipeForSystemBackEdge = false

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
    private var restoreScrollYArg: Int = 0
    private var lastObservedScrollY = 0
    private val scrollCaptureListener = ObservableWebView.OnScrollChangeListener { _, scrollY, _ ->
        lastObservedScrollY = maxOf(lastObservedScrollY, scrollY)
    }

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
            restoreScrollYArg = it.getInt(ARG_PAGE_SCROLL_Y, 0)
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
        trackWebViewScrollPosition()
        
        // Skip ContentsHandler and NativeMapHandler creation in debug mode (Coins page test)
        if (System.getProperty("debug.coins.test") != "true") {
            contentsHandler = ContentsHandler(this)
            nativeMapHandler = NativeMapHandler(this, binding)
        }
        val app = requireActivity().application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()
        lastAppliedCollapsePreference = Prefs.isCollapseTablesEnabled
        lastAppliedWrapTableCells = Prefs.wrapTableCells
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
        // A single process-owned downloader lets list dwell work and article foreground work share
        // the same cache/in-flight generation across fragment recreation.
        val pageAssetDownloader = app.pageAssetDownloader

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
        binding.articleSwipeRefresh.setOnRefreshListener {
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
        trackWebViewScrollPosition()
        val webViewManager = createPageWebViewManager()
        pageWebViewManager = webViewManager
        pageUiUpdater = PageUiUpdater(binding, pageViewModel, webViewManager) { this }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        horizontalGestureOwnership.reset()
        val slop = ViewConfiguration.get(requireContext()).scaledPagingTouchSlop
        interactiveSwipe = osrsArticleInteractiveSwipe(touchSlop = slop)
        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            private val swipeThreshold = 72
            private val swipeVelocityThreshold = 80

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
                    val generation = horizontalGestureOwnership.currentGeneration ?: return false
                    val isLocalScrollOwned = horizontalGestureOwnership.owns(generation)
                    L.d("Gesture: dx=${dx.toInt()}, dy=${dy.toInt()}, velX=${velocityX.toInt()}, localScrollOwned=$isLocalScrollOwned")
                    
                    if (isLocalScrollOwned) {
                        L.d("Gesture: Blocked by local horizontal scroll")
                        return false
                    }
                    if (abs(dx) > abs(dy) &&
                        abs(dx) > swipeThreshold &&
                        abs(velocityX) > swipeVelocityThreshold
                    ) {
                        val gravity = if (dx > 0) Gravity.START else Gravity.END
                        L.d("Gesture: Swipe candidate detected, generation=$generation")
                        when (horizontalGestureOwnership.registerNavigationCandidate(generation)) {
                            ArticleHorizontalGestureOwnership.NavigationDecision.WAITING_FOR_CLASSIFICATION ->
                                resolveArticleSwipeOwnership(generation, gravity)
                            ArticleHorizontalGestureOwnership.NavigationDecision.ALLOW_NAVIGATION -> {
                                dispatchPageSwipeIfEnabled(gravity)
                                horizontalGestureOwnership.finishPointer(generation)
                            }
                            ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION ->
                                horizontalGestureOwnership.finishPointer(generation)
                            ArticleHorizontalGestureOwnership.NavigationDecision.STALE -> Unit
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
        binding.pageWebView.setOnTouchListener { view, event ->
            if (injectingWebViewCancel) {
                return@setOnTouchListener false
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val systemGestures = ViewCompat.getRootWindowInsets(view)
                    ?.getInsets(WindowInsetsCompat.Type.systemGestures())
                ignoreInteractiveSwipeForSystemBackEdge = osrsArticleSystemBackEdge.contains(
                    event.x,
                    view.width.toFloat(),
                    systemGestures?.left ?: 0,
                    systemGestures?.right ?: 0
                )
            }
            if (ignoreInteractiveSwipeForSystemBackEdge) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    ignoreInteractiveSwipeForSystemBackEdge = false
                }
                return@setOnTouchListener false
            }
            val tracker = interactiveSwipe
            val generation = when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastPointerDownX = event.x
                    lastPointerDownY = event.y
                    lastPointerDownRawX = event.rawX
                    lastPointerDownRawY = event.rawY
                    lastInteractiveDx = 0f
                    lastInteractiveVx = 0f
                    lastInteractiveEventX = event.rawX
                    lastInteractiveEventTime = event.eventTime
                    consumedInteractiveSwipe = false
                    tracker?.reset()
                    // Keep DrawerLayout from stealing this pointer before DOM
                    // classifies a local table scroller. Chrome swipe is owned by
                    // this listener, not the drawer.
                    var ancestor = view.parent
                    while (ancestor != null) {
                        ancestor.requestDisallowInterceptTouchEvent(true)
                        ancestor = ancestor.parent
                    }
                    horizontalGestureOwnership.beginPointer()
                }
                else -> horizontalGestureOwnership.currentGeneration
            }

            if (event.actionMasked == MotionEvent.ACTION_MOVE &&
                tracker != null &&
                !lastPointerDownRawX.isNaN() &&
                !horizontalGestureOwnership.ownsCurrentPointer() &&
                horizontalGestureOwnership.hasDomClassification()
            ) {
                val dx = event.rawX - lastPointerDownRawX
                val dy = event.rawY - lastPointerDownRawY
                val dt = (event.eventTime - lastInteractiveEventTime).coerceAtLeast(1L)
                lastInteractiveVx = (event.rawX - lastInteractiveEventX) * 1000f / dt
                lastInteractiveEventX = event.rawX
                lastInteractiveEventTime = event.eventTime
                lastInteractiveDx = dx
                val contentsOpen = callback?.isContentsDrawerOpen() == true
                val axis = tracker.onMove(dx, dy, contentsOpen = contentsOpen)
                if (tracker.isTracking && axis != null) {
                    if (!consumedInteractiveSwipe) {
                        consumedInteractiveSwipe = true
                        val cancel = MotionEvent.obtain(
                            event.downTime,
                            event.eventTime,
                            MotionEvent.ACTION_CANCEL,
                            event.x,
                            event.y,
                            event.metaState
                        )
                        try {
                            gestureDetector.onTouchEvent(cancel)
                            injectingWebViewCancel = true
                            view.onTouchEvent(cancel)
                        } finally {
                            injectingWebViewCancel = false
                            cancel.recycle()
                        }
                    }
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    val gravity = tracker.gravity()
                    val span = interactiveSpanPx(view, axis)
                    if (gravity != null) {
                        val action = when (gravity) {
                            Gravity.START -> ReaderSwipeAction.BACK
                            Gravity.END -> ReaderSwipeAction.CONTENTS
                            else -> null
                        }
                        if (action != null && ReaderGesturePolicy.isEnabled(action, Prefs.readerPreferences)) {
                            callback?.onPageSwipeProgress(gravity, tracker.progress(dx, span))
                        }
                    }
                }
            }

            if (!horizontalGestureOwnership.ownsCurrentPointer() && !consumedInteractiveSwipe) {
                gestureDetector.onTouchEvent(event)
            }

            if (generation != null) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP -> {
                        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                        val isTap = !lastPointerDownX.isNaN() &&
                            kotlin.math.abs(event.x - lastPointerDownX) <= slop &&
                            kotlin.math.abs(event.y - lastPointerDownY) <= slop
                        if (isTap) {
                            dismissFindInPageIfActive()
                        }
                        if (tracker != null && !lastPointerDownRawX.isNaN()) {
                            lastInteractiveDx = event.rawX - lastPointerDownRawX
                            tracker.onMove(
                                lastInteractiveDx,
                                event.rawY - lastPointerDownRawY,
                                contentsOpen = callback?.isContentsDrawerOpen() == true
                            )
                            if (tracker.isTracking) {
                                consumedInteractiveSwipe = true
                            }
                        }
                        if (consumedInteractiveSwipe && tracker != null) {
                            settleInteractiveSwipe(tracker, view)
                        }
                        if (!horizontalGestureOwnership.isAwaitingNavigationDecision(generation)) {
                            horizontalGestureOwnership.finishPointer(generation)
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (consumedInteractiveSwipe) {
                            tracker?.reset()
                            callback?.onPageSwipeCancelled()
                        }
                        horizontalGestureOwnership.finishPointer(generation)
                    }
                }
            }
            consumedInteractiveSwipe
        }
    }

    private fun interactiveSpanPx(view: View, axis: osrsArticleInteractiveSwipe.Axis): Float {
        return when (axis) {
            osrsArticleInteractiveSwipe.Axis.BACK -> view.width.toFloat().coerceAtLeast(1f)
            osrsArticleInteractiveSwipe.Axis.CONTENTS ->
                osrsArticleInteractiveSwipe.CONTENTS_DRAWER_WIDTH_DP * view.resources.displayMetrics.density
        }
    }

    private fun settleInteractiveSwipe(tracker: osrsArticleInteractiveSwipe, view: View) {
        val axis = tracker.axis
        val gravity = tracker.gravity()
        val commit = axis != null &&
            gravity != null &&
            tracker.shouldCommit(
                lastInteractiveDx,
                lastInteractiveVx,
                interactiveSpanPx(view, axis)
            )
        tracker.reset()
        consumedInteractiveSwipe = false
        val swipeGravity = gravity
        if (!commit || swipeGravity == null) {
            callback?.onPageSwipeCancelled()
            return
        }
        val generation = horizontalGestureOwnership.currentGeneration
        if (generation == null) {
            callback?.onPageSwipeCancelled()
            return
        }
        if (horizontalGestureOwnership.owns(generation)) {
            callback?.onPageSwipeCancelled()
            return
        }
        when (horizontalGestureOwnership.registerNavigationCandidate(generation)) {
            ArticleHorizontalGestureOwnership.NavigationDecision.WAITING_FOR_CLASSIFICATION ->
                resolveArticleSwipeOwnership(generation, swipeGravity)
            ArticleHorizontalGestureOwnership.NavigationDecision.ALLOW_NAVIGATION -> {
                dispatchPageSwipeIfEnabled(swipeGravity)
                horizontalGestureOwnership.finishPointer(generation)
            }
            ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION -> {
                callback?.onPageSwipeCancelled()
                horizontalGestureOwnership.finishPointer(generation)
            }
            ArticleHorizontalGestureOwnership.NavigationDecision.STALE ->
                callback?.onPageSwipeCancelled()
        }
    }

    private fun resolveArticleSwipeOwnership(generation: Long, gravity: Int) {
        val webView = _binding?.pageWebView
        if (webView == null) {
            horizontalGestureOwnership.finishPointer(generation)
            return
        }
        val domSequence = horizontalGestureOwnership.domSequenceFor(generation)
        if (domSequence == null) {
            // The ownership bridge is not installed yet (page still loading). Keep interior
            // article chrome usable; OS edge swipes never reach this path.
            horizontalGestureOwnership.recordFinalClassification(generation, null)
            dispatchPageSwipeIfEnabled(gravity)
            horizontalGestureOwnership.finishPointer(generation)
            return
        }
        webView.evaluateJavascript(articleHorizontalGestureSnapshotQuery(domSequence)) { rawResult ->
            if (_binding == null) {
                horizontalGestureOwnership.finishPointer(generation)
                return@evaluateJavascript
            }
            val snapshot = decodeArticleHorizontalGestureSnapshot(rawResult)
            when (horizontalGestureOwnership.recordFinalClassification(generation, snapshot)) {
                ArticleHorizontalGestureOwnership.NavigationDecision.ALLOW_NAVIGATION ->
                    dispatchPageSwipeIfEnabled(gravity)
                ArticleHorizontalGestureOwnership.NavigationDecision.BLOCK_NAVIGATION ->
                    if (snapshot == null) {
                        dispatchPageSwipeIfEnabled(gravity)
                    } else {
                        callback?.onPageSwipeCancelled()
                    }
                ArticleHorizontalGestureOwnership.NavigationDecision.WAITING_FOR_CLASSIFICATION,
                ArticleHorizontalGestureOwnership.NavigationDecision.STALE ->
                    callback?.onPageSwipeCancelled()
            }
            horizontalGestureOwnership.finishPointer(generation)
        }
    }

    private fun dispatchPageSwipeIfEnabled(gravity: Int) {
        val action = when (gravity) {
            Gravity.START -> ReaderSwipeAction.BACK
            Gravity.END -> ReaderSwipeAction.CONTENTS
            else -> return
        }
        if (gravity == Gravity.END && callback?.isContentsDrawerOpen() == true) {
            callback?.onPageSwipe(gravity, lastInteractiveVx)
            return
        }
        // Read at dispatch time so changes made while PageActivity is paused apply immediately.
        if (ReaderGesturePolicy.isEnabled(action, Prefs.readerPreferences)) {
            callback?.onPageSwipe(gravity, lastInteractiveVx)
        } else {
            callback?.onPageSwipeCancelled()
            L.d("Gesture: $action navigation disabled by reader preference")
        }
    }

    /** Binds a primary DOM touch sequence to the exact native ACTION_DOWN that preceded it. */
    internal fun onArticleDomTouchSequence(sequence: Long) {
        horizontalGestureOwnership.bindNextDomTouchSequence(sequence)
    }

    /** Called on the view thread when DOM content explicitly is not a local horizontal scroller. */
    internal fun onArticleHorizontalScrollNotOwned() {
        horizontalGestureOwnership.markCurrentPointerUnowned()
    }

    /** Called when a local scroller reaches its edge and releases the rest of this pointer. */
    internal fun onArticleHorizontalScrollReleased() {
        horizontalGestureOwnership.releaseCurrentPointerClaim()
    }

    /** Called on the view thread when DOM content or a native article map claims this gesture. */
    internal fun onArticleHorizontalScrollClaimed() {
        if (!horizontalGestureOwnership.claimCurrentPointer() || !::gestureDetector.isInitialized) {
            return
        }
        interactiveSwipe?.reset()
        consumedInteractiveSwipe = false
        callback?.onPageSwipeCancelled()
        val cancel = MotionEvent.obtain(
            android.os.SystemClock.uptimeMillis(),
            android.os.SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            0f,
            0f,
            0
        )
        try {
            gestureDetector.onTouchEvent(cancel)
        } finally {
            cancel.recycle()
        }
        var ancestor = binding.pageWebView.parent
        while (ancestor != null) {
            ancestor.requestDisallowInterceptTouchEvent(true)
            ancestor = ancestor.parent
        }
    }

    private fun fetchTableOfContents() {
        val script = """
            (function() {
                var tocData = { leadSectionDetails: null, sections: [] };
                var headerSpans = document.querySelectorAll('h2[id], h3[id], .mw-headline[id]');
                for (var i = 0; i < headerSpans.length; i++) {
                    var span = headerSpans[i];
                    var header = span.tagName === 'H2' || span.tagName === 'H3' ? span : span.parentElement;
                    if (!header || (header.tagName !== 'H2' && header.tagName !== 'H3')) continue;
                    var clone = span.cloneNode(true);
                    var hideSel = document.body.classList.contains('floornumber-setting-us')
                        ? '.floornumber-gb, .floornumber-help'
                        : '.floornumber-us, .floornumber-help';
                    clone.querySelectorAll(hideSel).forEach(function(node) {
                        node.remove();
                    });
                    var title = (clone.textContent || '').replace(/\\s+/g, ' ').trim();
                    var anchor = span.id;
                    if (anchor && title) {
                        var level = parseInt(header.tagName.substring(1));
                        var computedStyle = window.getComputedStyle(span);
                        tocData.sections.push({
                            id: tocData.sections.length + 1, level: level, anchor: anchor, title: title,
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

    fun captureViewRestore(): osrsArticleViewRestore {
        val webView = _binding?.pageWebView
        val nativeScroll = maxOf(webView?.scrollY ?: 0, lastObservedScrollY)
        L.d("captureViewRestore native=${webView?.scrollY} last=$lastObservedScrollY -> $nativeScroll")
        return osrsArticleViewRestore(scrollY = nativeScroll)
    }

    fun captureViewRestore(onCaptured: (osrsArticleViewRestore) -> Unit) {
        val snapshot = captureViewRestore()
        val webView = _binding?.pageWebView
        if (webView == null) {
            onCaptured(snapshot)
            return
        }
        var delivered = false
        fun deliver(restore: osrsArticleViewRestore) {
            if (delivered) {
                return
            }
            delivered = true
            onCaptured(restore)
        }
        webView.postDelayed({ deliver(snapshot) }, 180)
        webView.evaluateJavascript(
            "(function(){return Math.round(window.pageYOffset||document.documentElement.scrollTop||0);})()"
        ) { result ->
            val jsCss = result?.trim('"')?.toFloatOrNull()?.toInt() ?: 0
            val jsDevice = (jsCss * webView.scale).toInt()
            val captured = maxOf(snapshot.scrollY, jsCss, jsDevice)
            L.d("captureViewRestore jsCss=$jsCss jsDevice=$jsDevice scale=${webView.scale} -> $captured")
            deliver(osrsArticleViewRestore(scrollY = captured))
        }
    }

    override fun onPageReadyForDisplay() {
        val webViewManager = pageWebViewManager ?: return
        if (isAdded && _binding != null && !webViewReleasedWhileStopped) {
            webViewManager.finalizeAndRevealPage(peekRestoreScrollY()) {
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
                currentBinding.articleSwipeRefresh.isRefreshing = false
                scheduleSavedSnapshotRefreshIfNeeded()
            }
        }
    }

    private fun scheduleSavedSnapshotRefreshIfNeeded() {
        val titleText = pageViewModel.uiState.plainTextTitle?.takeIf { it.isNotBlank() }
            ?: pageTitleArg?.takeIf { it.isNotBlank() }
            ?: return
        val context = context ?: return
        val settings = osrsDownloadSettings.load()
        val isOnline = osrsDownloadSettings.isOnline(context)
        val isUnmetered = osrsDownloadSettings.isUnmetered(context)
        if (!settings.shouldRefreshSnapshot(
                osrsSavedPageUpdateTrigger.ACCESS,
                isOnline,
                isUnmetered
            )
        ) {
            return
        }
        val localRevision = pageViewModel.uiState.revisionId
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val wikiSite = WikiSite.OSRS_WIKI
            val savedPage = readingListPageDao.findPageInAnyList(
                wikiSite,
                wikiSite.languageCode,
                Namespace.MAIN,
                titleText
            ) ?: readingListPageDao.findPageInAnyList(
                wikiSite,
                wikiSite.languageCode,
                Namespace.MAIN,
                titleText.replace(" ", "_")
            ) ?: return@launch
            if (!savedPage.offline || savedPage.status != ReadingListPage.STATUS_SAVED) {
                return@launch
            }
            val remote = osrsSavedPageRevisionProbe.fetchRemoteRevision(
                savedPage.apiTitle,
                OkHttpClientFactory.offlineClient
            ) ?: return@launch
            val savedRevision = localRevision ?: savedPage.revId
            if (!osrsSavedPageRevisionProbe.snapshotNeedsRefresh(savedRevision, remote.revisionId)) {
                return@launch
            }
            readingListPageDao.transitionPageToForcedOfflineSave(savedPage.id)
            withContext(Dispatchers.Main) {
                SavedPageSyncWorker.enqueue(context)
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

    private fun dismissFindInPageIfActive() {
        if (!isFindInPageActive) return
        callback?.onPageFinishActionMode()
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

    fun openFloorNumberingSettings() {
        startActivity(AppearanceSettingsActivity.newIntent(requireContext(), highlightFloorNumbering = true))
    }

    private fun applyFloorNumberingPreference() {
        pageWebViewManager?.refreshFloorNumberingPreference()
        val html = pageViewModel.uiState.htmlContent ?: return
        val sections = PageTableOfContentsExtractor.extract(
            pageViewModel.uiState.title,
            html,
            osrsArticleFloorConvention.resolved()
        )
        pageViewModel.uiState = pageViewModel.uiState.copy(tableOfContentsSections = sections)
        contentsHandler?.setup(sections)
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
        _binding?.pageWebView?.onPause()
        super.onStop()
    }

    override fun onPause() {
        _binding?.pageWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        _binding?.pageWebView?.onResume()
        restoreStoppedWebViewResourcesIfNeeded()
        pageWebViewManager?.refreshReaderTextScale()
        val collapsePreference = Prefs.isCollapseTablesEnabled
        if (collapsePreference != lastAppliedCollapsePreference) {
            lastAppliedCollapsePreference = collapsePreference
            pageWebViewManager?.refreshTableCollapsePreference()
        }
        val floorNumberingMode = Prefs.floorNumberingMode
        if (floorNumberingMode != lastAppliedFloorNumberingMode) {
            lastAppliedFloorNumberingMode = floorNumberingMode
            applyFloorNumberingPreference()
        }
        val wrapTableCells = Prefs.wrapTableCells
        if (wrapTableCells != lastAppliedWrapTableCells) {
            lastAppliedWrapTableCells = wrapTableCells
            pageWebViewManager?.refreshWrapTableCellsPreference()
        }
    }

    private fun releaseStoppedWebViewResources() {
        horizontalGestureOwnership.reset()
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
        lastObservedScrollY = maxOf(lastObservedScrollY, oldWebView.scrollY)
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
        trackWebViewScrollPosition()
        val webViewManager = createPageWebViewManager()
        pageWebViewManager = webViewManager
        val uiUpdater = PageUiUpdater(binding, pageViewModel, webViewManager) { this }
        pageUiUpdater = uiUpdater
        pageLoadCoordinator = PageLoadCoordinator(pageViewModel, pageContentLoader, uiUpdater) { this }

        val app = requireActivity().application as OSRSWikiApp
        pageLoadCoordinator?.initiatePageLoad(app.getCurrentTheme(), forceNetwork = false)
    }

    private fun trackWebViewScrollPosition() {
        val webView = _binding?.pageWebView ?: return
        webView.removeOnScrollChangeListener(scrollCaptureListener)
        webView.addOnScrollChangeListener(scrollCaptureListener)
        lastObservedScrollY = maxOf(lastObservedScrollY, webView.scrollY)
    }

    private fun peekRestoreScrollY(): Int {
        val navigationScrollY = restoreScrollYArg
        val releasedScrollY = if (shouldRestoreReleasedWebViewScroll) releasedWebViewScrollY else 0
        return maxOf(navigationScrollY, releasedScrollY)
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
        horizontalGestureOwnership.reset()
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
        const val ARG_PAGE_SCROLL_Y = "pageScrollY"
        private const val WEBVIEW_RELEASES_PER_HEAP_TRIM = 8
        private var webViewReleaseCount = 0
        @JvmStatic
        fun newInstance(
            pageId: String?, 
            pageTitle: String?, 
            source: Int,
            snippet: String? = null,
            thumbnailUrl: String? = null,
            scrollY: Int = 0
        ): PageFragment = PageFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PAGE_ID, pageId)
                putString(ARG_PAGE_TITLE, pageTitle)
                putInt(ARG_PAGE_SOURCE, source)
                putString(ARG_PAGE_SNIPPET, snippet)
                putString(ARG_PAGE_THUMBNAIL, thumbnailUrl)
                putInt(ARG_PAGE_SCROLL_Y, scrollY)
            }
        }
    }
}

data class osrsArticleViewRestore(
    val scrollY: Int
)
