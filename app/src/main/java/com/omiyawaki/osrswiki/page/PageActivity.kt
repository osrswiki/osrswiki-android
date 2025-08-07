package com.omiyawaki.osrswiki.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.view.ActionMode
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textview.MaterialTextView
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

    private lateinit var pageActionBarManager: PageActionBarManager
    
    private var themeChangeReceiver: BroadcastReceiver? = null
    
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

        pageTitleArg = intent.getStringExtra(EXTRA_PAGE_TITLE)
        pageIdArg = intent.getStringExtra(EXTRA_PAGE_ID)
        navigationSourceArg = intent.getIntExtra(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)
        snippetArg = intent.getStringExtra(EXTRA_PAGE_SNIPPET)
        thumbnailUrlArg = intent.getStringExtra(EXTRA_PAGE_THUMBNAIL)
        
        L.d("PageActivity.onCreate() - Extracted intent extras:")
        L.d("  pageTitleArg: '$pageTitleArg'")
        L.d("  pageIdArg: '$pageIdArg'")
        L.d("  navigationSourceArg: $navigationSourceArg")
        L.d("  snippetArg: '$snippetArg'")
        L.d("  thumbnailUrlArg: '$thumbnailUrlArg'")

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(
                pageId = pageIdArg, 
                pageTitle = pageTitleArg, 
                source = navigationSourceArg,
                snippet = snippetArg,
                thumbnailUrl = thumbnailUrlArg
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG)
                .commit()
        }
        setupToolbarListeners()
        setupThemeChangeReceiver()
        checkAndShowOfflineBanner()
    }

    override fun onWebViewReady(webView: ObservableWebView) {
        // Static toolbar - no scroll attachment needed
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
        
        val searchContainer = binding.pageToolbar.findViewById<MaterialTextView>(R.id.toolbar_search_container)
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
        val pageTitle = pageTitleArg
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

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }
    
    private fun setupThemeChangeReceiver() {
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == com.omiyawaki.osrswiki.settings.AppearanceSettingsFragment.ACTION_THEME_CHANGED) {
                    L.d("PageActivity: Received theme change broadcast")
                    // Apply theme dynamically without recreation
                    applyThemeDynamically()
                }
            }
        }
        
        val filter = IntentFilter(com.omiyawaki.osrswiki.settings.AppearanceSettingsFragment.ACTION_THEME_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(themeChangeReceiver!!, filter)
        L.d("PageActivity: Theme change receiver registered")
    }
    
    private fun unregisterThemeChangeReceiver() {
        themeChangeReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            themeChangeReceiver = null
            L.d("PageActivity: Theme change receiver unregistered")
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
            
            L.d("PageActivity: Theme-dependent elements refresh completed")
            
        } catch (e: Exception) {
            L.e("PageActivity: Error refreshing theme elements: ${e.message}")
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
            
            // Refresh toolbar icon tints
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
            
            L.d("PageActivity: Toolbar icon tints refreshed")
        } catch (e: Exception) {
            L.w("PageActivity: Error refreshing toolbar icon tints: ${e.message}")
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
        unregisterThemeChangeReceiver()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        const val EXTRA_PAGE_SNIPPET = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SNIPPET"
        const val EXTRA_PAGE_THUMBNAIL = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_THUMBNAIL"
        const val FRAGMENT_TAG = "PageFragmentTag"

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
