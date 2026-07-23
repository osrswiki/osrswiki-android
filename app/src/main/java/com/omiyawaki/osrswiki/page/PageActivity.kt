package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.ActionMode
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.views.ObservableWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageActivity : BaseActivity(), PageFragment.Callback {

    internal lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK
    private var snippetArg: String? = null
    private var thumbnailUrlArg: String? = null
    private var currentActionMode: ActionMode? = null
    private val articleBackStack = ArrayDeque<ArticleArgs>()
    private var isRunningDeepNavigationFixtureProbe = false

    private lateinit var pageActionBarManager: PageActionBarManager
    
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceRecognitionManager.handleActivityResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("PageActivity.onCreate() called")
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Forcefully set the elevation and disable the StateListAnimator to
        // prevent the theme from overwriting the elevation value.
        binding.pageAppbarLayout.stateListAnimator = null
        val elevationInDp = 9.75f
        binding.pageAppbarLayout.elevation = elevationInDp * resources.displayMetrics.density

        // Handle system window insets - let AppBarLayout extend under status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.pageFragmentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Calculate total bottom padding: action bar height + navigation gesture area
            val navBarHeight = resources.getDimensionPixelSize(R.dimen.nav_bar_height)
            val totalBottomPadding = navBarHeight + navigationBars.bottom
            
            // Apply side padding and bottom padding for safe area
            view.setPadding(systemBars.left, 0, systemBars.right, totalBottomPadding)
            insets
        }

        // Handle bottom safe area for the page action bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root.findViewById(R.id.page_action_bar)) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Use translationY to move the action bar up without affecting layout space
            view.translationY = -navigationBars.bottom.toFloat()
            
            // Apply horizontal margins for side insets
            val layoutParams = view.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            layoutParams.leftMargin = systemBars.left
            layoutParams.rightMargin = systemBars.right
            view.layoutParams = layoutParams
            
            insets
        }


        applyArticleArgs(restoreArticleState(savedInstanceState) ?: readArticleArgs(intent))
        if (savedInstanceState != null) {
            setIntent(newIntent(this, pageTitleArg, pageIdArg, navigationSourceArg, snippetArg, thumbnailUrlArg))
        }
        if (savedInstanceState == null) {
            replaceArticleFragmentIfFixtureProbeAllows()
        }
        setupToolbarListeners()
        setupBackNavigation()
        checkAndShowOfflineBanner()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle(STATE_CURRENT_ARTICLE_ARGS, currentArticleArgs().toBundle())
        outState.putParcelableArrayList(
            STATE_ARTICLE_BACK_STACK,
            ArrayList(articleBackStack.map { it.toBundle() })
        )
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showArticleFromIntent(intent, pushCurrent = true)
    }

    override fun onWebViewReady(webView: ObservableWebView) {
        // Static toolbar - no scroll attachment needed
    }

    private fun showArticleFromIntent(intent: Intent, pushCurrent: Boolean) {
        if (pushCurrent) {
            pushArticleArgsForNativeStack(readArticleArgs(intent), intent)
        } else {
            replaceCurrentArticleArgsForNativeStack(readArticleArgs(intent), intent)
        }
        replaceArticleFragmentIfFixtureProbeAllows()
        checkAndShowOfflineBanner()
    }

    private fun popArticleBackStack(): Boolean {
        if (!popArticleArgsFromNativeStack()) {
            return false
        }
        replaceArticleFragmentIfFixtureProbeAllows()
        checkAndShowOfflineBanner()
        return true
    }

    private fun pushArticleArgsForNativeStack(args: ArticleArgs, sourceIntent: Intent? = null) {
        articleBackStack.addLast(currentArticleArgs())
        replaceCurrentArticleArgsForNativeStack(args, sourceIntent)
    }

    private fun popArticleArgsFromNativeStack(): Boolean {
        if (articleBackStack.isEmpty()) {
            return false
        }
        replaceCurrentArticleArgsForNativeStack(articleBackStack.removeLast())
        return true
    }

    private fun replaceCurrentArticleArgsForNativeStack(args: ArticleArgs, sourceIntent: Intent? = null) {
        setIntent(sourceIntent ?: newIntentForArticleArgs(args))
        applyArticleArgs(args)
    }

    private fun restoreArticleState(savedInstanceState: Bundle?): ArticleArgs? {
        if (savedInstanceState == null) {
            return null
        }

        articleBackStack.clear()
        val restoredBackStack = BundleCompat.getParcelableArrayList(
            savedInstanceState,
            STATE_ARTICLE_BACK_STACK,
            Bundle::class.java
        )
            ?.mapNotNull { ArticleArgs.fromBundle(it) }
            .orEmpty()
        articleBackStack.addAll(restoredBackStack)
        return savedInstanceState.getBundle(STATE_CURRENT_ARTICLE_ARGS)?.let { ArticleArgs.fromBundle(it) }
    }

    private fun replaceArticleFragment() {
        val fragment = PageFragment.newInstance(
            pageId = pageIdArg,
            pageTitle = pageTitleArg,
            source = navigationSourceArg,
            snippet = snippetArg,
            thumbnailUrl = thumbnailUrlArg
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG)
            .commitNowAllowingStateLoss()
    }

    private fun replaceArticleFragmentIfFixtureProbeAllows() {
        if (isDeepNavigationFixtureProbeEnabledForDebugTests()) {
            return
        }
        replaceArticleFragment()
    }

    private fun readArticleArgs(intent: Intent): ArticleArgs {
        return ArticleArgs(
            pageTitle = intent.getStringExtra(EXTRA_PAGE_TITLE),
            pageId = intent.getStringExtra(EXTRA_PAGE_ID),
            navigationSource = intent.getIntExtra(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK),
            snippet = intent.getStringExtra(EXTRA_PAGE_SNIPPET),
            thumbnailUrl = intent.getStringExtra(EXTRA_PAGE_THUMBNAIL)
        )
    }

    private fun currentArticleArgs(): ArticleArgs {
        return ArticleArgs(
            pageTitle = pageTitleArg,
            pageId = pageIdArg,
            navigationSource = navigationSourceArg,
            snippet = snippetArg,
            thumbnailUrl = thumbnailUrlArg
        )
    }

    private fun newIntentForArticleArgs(args: ArticleArgs): Intent {
        if (isDeepNavigationFixtureProbeEnabledForDebugTests()) {
            return Intent(this, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, args.pageTitle)
                putExtra(EXTRA_PAGE_ID, args.pageId)
                putExtra(EXTRA_PAGE_SOURCE, args.navigationSource)
                putExtra(EXTRA_PAGE_SNIPPET, args.snippet)
                putExtra(EXTRA_PAGE_THUMBNAIL, args.thumbnailUrl)
                putExtra(EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS, true)
            }
        }
        return newIntent(
            context = this,
            pageTitle = args.pageTitle,
            pageId = args.pageId,
            source = args.navigationSource,
            snippet = args.snippet,
            thumbnailUrl = args.thumbnailUrl
        ).apply {
            if (isDeepNavigationFixtureProbeEnabledForDebugTests()) {
                putExtra(EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS, true)
            }
        }
    }

    private fun applyArticleArgs(args: ArticleArgs) {
        pageTitleArg = args.pageTitle
        pageIdArg = args.pageId
        navigationSourceArg = args.navigationSource
        snippetArg = args.snippet
        thumbnailUrlArg = args.thumbnailUrl

        if (!isDeepNavigationFixtureProbeEnabledForDebugTests()) {
            L.d("PageActivity - Applied article args:")
            L.d("  pageTitleArg: '$pageTitleArg'")
            L.d("  pageIdArg: '$pageIdArg'")
            L.d("  navigationSourceArg: $navigationSourceArg")
            L.d("  snippetArg: '$snippetArg'")
            L.d("  thumbnailUrlArg: '$thumbnailUrlArg'")
        }
    }

    override fun onPageSwipe(gravity: Int) {
        val direction = if (gravity == Gravity.START) "START (back)" else if (gravity == Gravity.END) "END (ToC)" else "UNKNOWN($gravity)"
        L.d("PageActivity: Received swipe, direction=$direction")
        
        if (gravity == Gravity.END) {
            // A swipe from right-to-left opens the ToC drawer.
            L.d("PageActivity: Opening ToC drawer")
            binding.pageDrawerLayout.openDrawer(GravityCompat.END)
        } else if (gravity == Gravity.START) {
            // A swipe from left-to-right triggers the back action.
            L.d("PageActivity: Triggering back action")
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun showContents() {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
        fragment?.showContents()
    }

    private fun setupToolbarListeners() {
        // Initialize voice recognition manager
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                // Open search activity with the voice query
                val intent = Intent(this, SearchActivity::class.java).apply {
                    putExtra("query", query)
                }
                startActivity(intent)
            }
        )
        
        val searchContainer = binding.pageToolbar.findViewById<TextView>(R.id.toolbar_search_container)
        // Ensure hint text is set on initialization
        if (searchContainer.hint.isNullOrBlank()) {
            searchContainer.setHint(R.string.page_toolbar_search_hint)
        }
        searchContainer.setOnClickListener {
            val searchActivityIntent = Intent(this, SearchActivity::class.java)
            startActivity(searchActivityIntent)
        }
        
        // Set up voice search button
        binding.pageToolbar.findViewById<ImageView>(R.id.toolbar_voice_search_button)?.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition()
        }

        binding.pageToolbar.findViewById<View>(R.id.toolbar_overflow_menu_button).setOnClickListener { anchorView ->
            val currentFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
            currentFragment?.showPageOverflowMenu(anchorView) ?: run {
                Toast.makeText(this, "Error: Could not show menu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (currentActionMode != null) {
            currentActionMode?.finish()
            return true
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.pageDrawerLayout.isDrawerOpen(binding.sidePanelContainer)) {
                    binding.pageDrawerLayout.closeDrawer(binding.sidePanelContainer)
                    return
                }

                if (popArticleBackStack()) {
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onPageStartActionMode(callback: ActionMode.Callback) {
        if (currentActionMode != null) { return }
        // Static toolbar - no expansion needed
        currentActionMode = startActionMode(callback)
    }

    override fun onPageStopActionMode() {
        currentActionMode = null
    }

    override fun onPageFinishActionMode() {
        currentActionMode?.finish()
    }

    override fun getPageToolbarContainer(): View = binding.pageAppbarLayout

    override fun getPageActionBarManager(): PageActionBarManager {
        if (!::pageActionBarManager.isInitialized) {
            pageActionBarManager = PageActionBarManager(binding)
        }
        return pageActionBarManager
    }

    private fun checkAndShowOfflineBanner() {
        if (isDeepNavigationFixtureProbeEnabledForDebugTests()) {
            return
        }
        val requestedArticleArgs = currentArticleArgs()
        val pageTitle = requestedArticleArgs.pageTitle
        if (pageTitle.isNullOrBlank()) {
            return
        }

        lifecycleScope.launch {
            val isOfflineMode = withContext(Dispatchers.IO) {
                // Check if we have no network connection
                val hasNetwork = hasNetworkConnection()
                if (hasNetwork) {
                    false // We have network, not in offline mode
                } else {
                    // No network, check if this page is saved offline
                    isPageSavedOffline(pageTitle)
                }
            }

            if (currentArticleArgs() != requestedArticleArgs) {
                return@launch
            }
            // Show offline banner if in offline mode
            binding.pageOfflineBanner.visibility = if (isOfflineMode) View.VISIBLE else View.GONE
        }
    }

    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun isPageSavedOffline(pageTitle: String): Boolean {
        return try {
            val readingListPageDao = AppDatabase.instance.readingListPageDao()
            val wikiSite = WikiSite.OSRS_WIKI
            val namespace = Namespace.MAIN
            
            val savedPage = readingListPageDao.findPageInAnyList(
                wiki = wikiSite,
                lang = wikiSite.languageCode,
                ns = namespace,
                apiTitle = pageTitle
            )
            
            savedPage?.offline == true && savedPage.status == ReadingListPage.STATUS_SAVED
        } catch (e: Exception) {
            false
        }
    }

    fun runDeepNavigationFixtureAuditForDebugTests(
        seed: Int = osrsDeepNavigationFixtureAudit.DEFAULT_SEED,
        startOffset: Int = osrsDeepNavigationFixtureAudit.DEFAULT_START_OFFSET,
        startCount: Int = osrsDeepNavigationFixtureAudit.DEFAULT_START_COUNT,
        targetDepth: Int = osrsDeepNavigationFixtureAudit.DEFAULT_DEPTH
    ): osrsDeepNavigationFixtureAuditResult {
        check(BuildConfig.DEBUG) {
            "Deep navigation fixture audit is DEBUG-only."
        }
        check(intent.getBooleanExtra(EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS, false)) {
            "Deep navigation fixture audit requires the test-gated fixture intent extra."
        }
        check(Looper.getMainLooper().thread === Thread.currentThread()) {
            "Deep navigation fixture audit must run on the main thread."
        }
        require(startCount >= 0) { "startCount must be non-negative." }
        require(targetDepth >= 0) { "targetDepth must be non-negative." }

        val startedAt = SystemClock.elapsedRealtime()
        var completedStarts = 0
        var forwardTransitions = 0
        var backTransitions = 0
        var mismatchCount = 0
        var firstMismatch: String? = null
        var finalActiveTitle: String? = null
        var finalActiveUrl: String? = null

        isRunningDeepNavigationFixtureProbe = true
        try {
            for (sequence in startOffset until startOffset + startCount) {
                val sampleOrdinal = osrsDeepNavigationFixtureAudit.sampleOrdinal(seed, sequence)
                val expectedStack = ArrayList<ArticleArgs>(targetDepth + 1)

                articleBackStack.clear()
                val rootArgs = fixtureArticleArgs(sampleOrdinal, depth = 0)
                replaceCurrentArticleArgsForNativeStack(rootArgs)
                expectedStack += rootArgs

                for (depth in 1..targetDepth) {
                    val nextArgs = fixtureArticleArgs(sampleOrdinal, depth)
                    pushArticleArgsForNativeStack(nextArgs)
                    forwardTransitions += 1
                    expectedStack += nextArgs

                    val observedArgs = currentArticleArgs()
                    if (observedArgs != nextArgs) {
                        mismatchCount += 1
                        if (firstMismatch == null) {
                            firstMismatch = "forward sample=$sampleOrdinal depth=$depth expected=${nextArgs.pageTitle} actual=${observedArgs.pageTitle}"
                        }
                        break
                    }
                }

                if (mismatchCount != 0) {
                    break
                }

                for (depth in targetDepth - 1 downTo 0) {
                    val expectedArgs = expectedStack[depth]
                    val restored = popArticleArgsFromNativeStack()
                    backTransitions += 1
                    val observedArgs = currentArticleArgs()

                    if (!restored || observedArgs != expectedArgs) {
                        mismatchCount += 1
                        if (firstMismatch == null) {
                            firstMismatch = "back sample=$sampleOrdinal depth=$depth expected=${expectedArgs.pageTitle} actual=${observedArgs.pageTitle}"
                        }
                        break
                    }
                }

                if (mismatchCount == 0 && articleBackStack.isNotEmpty()) {
                    mismatchCount += 1
                    firstMismatch = "stack-not-empty sample=$sampleOrdinal remaining=${articleBackStack.size}"
                }

                finalActiveTitle = currentArticleArgs().pageTitle
                finalActiveUrl = osrsDeepNavigationFixtureAudit.articleUrl(sampleOrdinal, depth = 0)

                if (mismatchCount != 0) {
                    break
                }

                completedStarts += 1
            }
        } finally {
            isRunningDeepNavigationFixtureProbe = false
        }

        val status = if (
            completedStarts == startCount &&
            forwardTransitions == startCount * targetDepth &&
            backTransitions == startCount * targetDepth &&
            mismatchCount == 0
        ) {
            "pass"
        } else {
            "mismatch"
        }

        return osrsDeepNavigationFixtureAuditResult(
            status = status,
            seed = seed,
            startOffset = startOffset,
            startCount = startCount,
            targetDepth = targetDepth,
            completedStarts = completedStarts,
            forwardTransitions = forwardTransitions,
            backTransitions = backTransitions,
            mismatchCount = mismatchCount,
            firstMismatch = firstMismatch,
            elapsedMilliseconds = SystemClock.elapsedRealtime() - startedAt,
            finalActiveTitle = finalActiveTitle,
            finalActiveUrl = finalActiveUrl
        )
    }

    private fun fixtureArticleArgs(sampleOrdinal: Int, depth: Int): ArticleArgs {
        return ArticleArgs(
            pageTitle = osrsDeepNavigationFixtureAudit.articleTitle(sampleOrdinal, depth),
            pageId = null,
            navigationSource = HistoryEntry.SOURCE_INTERNAL_LINK,
            snippet = null,
            thumbnailUrl = null
        )
    }

    private fun isDeepNavigationFixtureProbeEnabledForDebugTests(): Boolean {
        return BuildConfig.DEBUG &&
            (isRunningDeepNavigationFixtureProbe ||
                intent.getBooleanExtra(EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS, false))
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }
    
    
    
    override fun refreshThemeDependentElements() {
        super.refreshThemeDependentElements()
        L.d("PageActivity: Refreshing theme-dependent elements")
        
        try {
            val theme = this.theme
            val typedValue = android.util.TypedValue()
            
            // Refresh AppBarLayout background
            refreshAppBarLayout(theme, typedValue)
            
            // Refresh MaterialToolbar
            refreshToolbar(theme, typedValue)
            
            // Refresh offline banner
            refreshOfflineBanner(theme, typedValue)
            
            // Refresh side panel
            refreshSidePanel(theme, typedValue)
            
            // Refresh custom views
            refreshCustomViews(theme, typedValue)
            
            // Refresh action bar
            refreshActionBar(theme, typedValue)
            
            // Refresh status bar theming
            setupStatusBarTheming()
            
            // Run search bar specific refresh for search-specific colors
            refreshSearchBarSpecific(theme, typedValue)
            
            L.d("PageActivity: Theme-dependent elements refresh completed")
            
        } catch (e: Exception) {
            L.e("PageActivity: Error refreshing theme elements: ${e.message}")
        }
    }
    
    private fun refreshSearchBarSpecific(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            L.d("PageActivity: Running search bar specific refresh")
            
            // Get reference to the TextView search container
            val searchContainer = binding.pageToolbar.findViewById<TextView>(R.id.toolbar_search_container)
            
            // Ensure hint text is set (TextView handles hint colors automatically through theme)
            searchContainer?.let { textView ->
                if (textView.hint.isNullOrBlank()) {
                    textView.setHint(R.string.page_toolbar_search_hint)
                    L.d("PageActivity: Set search hint text")
                }
            }
            
            L.d("PageActivity: Search bar refresh completed - TextView handles hints properly")
            
        } catch (e: Exception) {
            L.e("PageActivity: Error in search bar refresh: ${e.message}")
        }
    }
    
    private fun refreshAppBarLayout(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // Refresh AppBarLayout background using ?attr/colorSurface
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                binding.pageAppbarLayout.setBackgroundColor(typedValue.data)
                L.d("PageActivity: Refreshed AppBarLayout background")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing AppBarLayout: ${e.message}")
        }
    }
    
    private fun refreshToolbar(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // Force toolbar to re-read theme attributes
            binding.pageToolbar.invalidate()
            
            // CRITICAL: Refresh search bar background drawable
            refreshSearchBarBackground(theme, typedValue)
            
            // Refresh toolbar icon tints (but not search text - that's handled separately)
            refreshToolbarIconTints(theme, typedValue)
            
            // Refresh any toolbar text colors
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                // The toolbar should pick up the new theme colors automatically
                L.d("PageActivity: Toolbar theme refreshed")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing toolbar: ${e.message}")
        }
    }
    
    private fun refreshSearchBarBackground(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // Find the search container LinearLayout (the one with shape_search_box background)
            val searchContainer = binding.pageToolbar.findViewById<android.view.View>(R.id.toolbar_search_container)?.parent as? android.view.ViewGroup
            searchContainer?.let { container ->
                // Force re-inflate the shape_search_box drawable with new theme
                val newBackground = resources.getDrawable(R.drawable.shape_search_box, theme)
                container.background = newBackground
                L.d("PageActivity: Search bar background drawable re-inflated with new theme")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing search bar background: ${e.message}")
        }
    }
    
    private fun refreshToolbarIconTints(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // Refresh search icon tint
            val searchIcon = binding.pageToolbar.findViewById<android.widget.ImageView>(R.id.toolbar_search_icon)
            if (theme.resolveAttribute(R.attr.placeholder_color, typedValue, true)) {
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    searchIcon, 
                    android.content.res.ColorStateList.valueOf(typedValue.data)
                )
            }
            
            // Note: Search text color refresh is handled separately in refreshSearchBarSpecific()
            // to ensure proper timing after BaseActivity's generic refresh
            
            // Refresh voice search button tint
            val voiceSearchButton = binding.pageToolbar.findViewById<android.widget.ImageView>(R.id.toolbar_voice_search_button)
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    voiceSearchButton, 
                    android.content.res.ColorStateList.valueOf(typedValue.data)
                )
            }
            
            // Refresh overflow menu button tint
            val overflowButton = binding.pageToolbar.findViewById<android.widget.ImageView>(R.id.toolbar_overflow_menu_button)
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                androidx.core.widget.ImageViewCompat.setImageTintList(
                    overflowButton, 
                    android.content.res.ColorStateList.valueOf(typedValue.data)
                )
            }
            
            L.d("PageActivity: Toolbar icon tints refreshed (search text handled separately)")
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing toolbar icon tints: ${e.message}")
        }
    }
    
    
    private fun getTextColorAttributeName(attr: Int): String {
        return when (attr) {
            android.R.attr.textColorHint -> "textColorHint"
            android.R.attr.textColorSecondary -> "textColorSecondary" 
            com.google.android.material.R.attr.colorOnSurfaceVariant -> "colorOnSurfaceVariant"
            com.google.android.material.R.attr.colorOnSurface -> "colorOnSurface"
            R.attr.secondary_text_color -> "secondary_text_color"
            else -> "unknown_attr_$attr"
        }
    }
    
    private fun refreshOfflineBanner(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // Refresh offline banner background (?attr/colorSecondaryContainer)
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true)) {
                binding.pageOfflineBanner.setBackgroundColor(typedValue.data)
            }
            
            // Refresh offline banner text color (?attr/colorOnSecondaryContainer)  
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValue, true)) {
                binding.pageOfflineBanner.setTextColor(typedValue.data)
            }
            
            L.d("PageActivity: Refreshed offline banner theme")
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing offline banner: ${e.message}")
        }
    }
    
    private fun refreshSidePanel(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            val sidePanelContainer = findViewById<android.view.View>(R.id.side_panel_container)
            
            // Refresh side panel background (?attr/colorSurface)
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                sidePanelContainer?.setBackgroundColor(typedValue.data)
                L.d("PageActivity: Refreshed side panel background")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing side panel: ${e.message}")
        }
    }
    
    private fun refreshCustomViews(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // Refresh DottedLineView (custom view that uses theme colors)
            val dottedLineView = findViewById<android.view.View>(R.id.toc_track)
            dottedLineView?.invalidate() // Force custom view to re-read theme
            
            // Refresh PageScrollerView 
            val pageScrollerView = findViewById<android.view.View>(R.id.page_scroller_view)
            pageScrollerView?.let { scrollerView ->
                // Refresh background tint (?attr/colorSurfaceVariant)
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
                    if (scrollerView is android.widget.ImageView) {
                        androidx.core.widget.ImageViewCompat.setImageTintList(
                            scrollerView, 
                            android.content.res.ColorStateList.valueOf(typedValue.data)
                        )
                    }
                }
                
                // Force complete refresh
                scrollerView.invalidate()
                
                L.d("PageActivity: Refreshed custom views")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing custom views: ${e.message}")
        }
    }
    
    private fun refreshActionBar(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue) {
        try {
            // The page action bar is an included layout, refresh it
            val pageActionBar = findViewById<android.view.View>(R.id.page_action_bar)
            pageActionBar?.let { actionBar ->
                // Force complete refresh of the action bar
                actionBar.invalidate()
                
                // Refresh background color
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                    actionBar.setBackgroundColor(typedValue.data)
                }
                
                // CRITICAL: Refresh compound drawable tints for all action bar buttons
                refreshActionBarButtonTints(theme, typedValue, actionBar)
                
                L.d("PageActivity: Refreshed action bar")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing action bar: ${e.message}")
        }
    }
    
    private fun refreshActionBarButtonTints(theme: android.content.res.Resources.Theme, typedValue: android.util.TypedValue, actionBar: android.view.View) {
        try {
            // Get the text color from theme for compound drawable tints
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                val tintColor = android.content.res.ColorStateList.valueOf(typedValue.data)
                
                // Refresh all action bar button compound drawable tints
                val buttonIds = arrayOf(
                    R.id.page_action_save,
                    R.id.page_action_find_in_article,
                    R.id.page_action_theme,
                    R.id.page_action_contents
                )
                
                for (buttonId in buttonIds) {
                    val button = actionBar.findViewById<com.google.android.material.textview.MaterialTextView>(buttonId)
                    button?.let { materialTextView ->
                        // Apply compound drawable tint using TextViewCompat
                        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(materialTextView, tintColor)
                        
                        // Also refresh the text color to ensure consistency
                        materialTextView.setTextColor(typedValue.data)
                        
                        L.d("PageActivity: Refreshed tint for button ${getButtonName(buttonId)}")
                    }
                }
                
                L.d("PageActivity: All action bar button tints refreshed")
            }
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing action bar button tints: ${e.message}")
        }
    }
    
    private fun getButtonName(buttonId: Int): String {
        return when (buttonId) {
            R.id.page_action_save -> "Save"
            R.id.page_action_find_in_article -> "Find"
            R.id.page_action_theme -> "Theme"
            R.id.page_action_contents -> "Contents"
            else -> "Unknown"
        }
    }
    
    private fun setupStatusBarTheming() {
        try {
            // Get the current theme's windowLightStatusBar setting
            val typedValue = android.util.TypedValue()
            val theme = this.theme
            val hasLightStatusBar = theme.resolveAttribute(android.R.attr.windowLightStatusBar, typedValue, true) && typedValue.data != 0
            
            // Apply the theme's status bar settings
            val windowInsetsController = androidx.core.view.ViewCompat.getWindowInsetsController(window.decorView)
            windowInsetsController?.let { controller ->
                controller.isAppearanceLightStatusBars = hasLightStatusBar
                L.d("PageActivity: Set status bar light mode: $hasLightStatusBar")
            }
            
            // Set status bar color from theme if available
            val statusBarColorTypedValue = android.util.TypedValue()
            if (theme.resolveAttribute(android.R.attr.statusBarColor, statusBarColorTypedValue, true)) {
                window.statusBarColor = statusBarColorTypedValue.data
                L.d("PageActivity: Applied theme status bar color")
            }
        } catch (e: Exception) {
            L.e("PageActivity: Error setting up status bar theming: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }

    private data class ArticleArgs(
        val pageTitle: String?,
        val pageId: String?,
        val navigationSource: Int,
        val snippet: String?,
        val thumbnailUrl: String?
    ) {
        fun toBundle(): Bundle {
            return Bundle().apply {
                putString(EXTRA_PAGE_TITLE, pageTitle)
                putString(EXTRA_PAGE_ID, pageId)
                putInt(EXTRA_PAGE_SOURCE, navigationSource)
                putString(EXTRA_PAGE_SNIPPET, snippet)
                putString(EXTRA_PAGE_THUMBNAIL, thumbnailUrl)
            }
        }

        companion object {
            fun fromBundle(bundle: Bundle): ArticleArgs {
                return ArticleArgs(
                    pageTitle = bundle.getString(EXTRA_PAGE_TITLE),
                    pageId = bundle.getString(EXTRA_PAGE_ID),
                    navigationSource = bundle.getInt(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK),
                    snippet = bundle.getString(EXTRA_PAGE_SNIPPET),
                    thumbnailUrl = bundle.getString(EXTRA_PAGE_THUMBNAIL)
                )
            }
        }
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        const val EXTRA_PAGE_SNIPPET = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SNIPPET"
        const val EXTRA_PAGE_THUMBNAIL = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_THUMBNAIL"
        const val EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS = "com.omiyawaki.osrswiki.page.EXTRA_DEEP_NAVIGATION_FIXTURE_PROBE_FOR_DEBUG_TESTS"
        const val FRAGMENT_TAG = "PageFragmentTag"
        private const val STATE_ARTICLE_BACK_STACK = "com.omiyawaki.osrswiki.page.STATE_ARTICLE_BACK_STACK"
        private const val STATE_CURRENT_ARTICLE_ARGS = "com.omiyawaki.osrswiki.page.STATE_CURRENT_ARTICLE_ARGS"

        fun newIntent(context: Context, updateItem: com.omiyawaki.osrswiki.news.model.UpdateItem, source: Int): Intent {
            L.d("PageActivity: Creating intent for UpdateItem")
            L.d("  UpdateItem.title: '${updateItem.title}'")
            L.d("  UpdateItem.articleUrl: '${updateItem.articleUrl}'")
            L.d("  UpdateItem.snippet: '${updateItem.snippet}'")
            L.d("  UpdateItem.imageUrl: '${updateItem.imageUrl}'")
            L.d("  source: $source")
            
            try {
                L.d("PageActivity: About to parse URL to extract title...")
                val canonicalTitle = getPageTitleFromUrl(updateItem.articleUrl)
                L.d("PageActivity: Successfully extracted canonical title: '$canonicalTitle' from URL: ${updateItem.articleUrl}")
                
                L.d("PageActivity: About to create intent with extracted title...")
                val intent = newIntent(
                    context = context,
                    pageTitle = canonicalTitle, 
                    pageId = null, 
                    source = source,
                    snippet = updateItem.snippet,
                    thumbnailUrl = updateItem.imageUrl
                )
                L.d("PageActivity: Intent created successfully for UpdateItem")
                return intent
            } catch (e: Exception) {
                L.e("PageActivity: Failed to create intent for UpdateItem", e)
                L.e("  Failing UpdateItem details:")
                L.e("    title: '${updateItem.title}'")
                L.e("    articleUrl: '${updateItem.articleUrl}'")
                L.e("    snippet: '${updateItem.snippet}'")
                L.e("    imageUrl: '${updateItem.imageUrl}'")
                throw e
            }
        }

        private fun getPageTitleFromUrl(url: String): String {
            L.d("PageActivity: Parsing URL: $url")
            
            try {
                // Validate URL format
                if (url.isBlank()) {
                    throw IllegalArgumentException("URL is blank")
                }
                
                if (!url.startsWith("http")) {
                    L.w("PageActivity: URL doesn't start with http: $url")
                }
                
                val pathSegment = url.substringAfterLast('/')
                L.d("PageActivity: Path segment: '$pathSegment'")
                
                if (pathSegment.isBlank()) {
                    throw IllegalArgumentException("Path segment is blank from URL: $url")
                }
                
                val withSpaces = pathSegment.replace('_', ' ')
                L.d("PageActivity: With spaces: '$withSpaces'")
                
                val decoded = java.net.URLDecoder.decode(withSpaces, "UTF-8")
                L.d("PageActivity: Final decoded title: '$decoded'")
                
                if (decoded.isBlank()) {
                    throw IllegalArgumentException("Decoded title is blank from URL: $url")
                }
                
                return decoded
            } catch (e: Exception) {
                L.e("PageActivity: Error parsing URL '$url'", e)
                throw e
            }
        }

        fun newIntent(
            context: Context, 
            pageTitle: String?, 
            pageId: String?, 
            source: Int,
            snippet: String? = null,
            thumbnailUrl: String? = null
        ): Intent {
            L.d("PageActivity: Creating intent with - pageTitle: '$pageTitle', pageId: '$pageId', source: $source")
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source)
                putExtra(EXTRA_PAGE_SNIPPET, snippet)
                putExtra(EXTRA_PAGE_THUMBNAIL, thumbnailUrl)
            }
        }
    }
}
