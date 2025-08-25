package com.omiyawaki.osrswiki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.history.HistoryFragment
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.map.AndroidMapPreloader
import com.omiyawaki.osrswiki.ui.map.StandardNavigationMapFragment
import com.omiyawaki.osrswiki.ui.more.MoreFragment
import com.omiyawaki.osrswiki.debug.ColorExtractor
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.FontUtil
import kotlinx.coroutines.launch
import android.widget.TextView
import com.omiyawaki.osrswiki.views.CustomBottomNavBar
import com.omiyawaki.osrswiki.settings.ContentBoundsProvider
import com.omiyawaki.osrswiki.settings.PreviewGenerationManager
import com.omiyawaki.osrswiki.settings.ActivityContextPool
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.search.CleanedSearchResultItem

// Debug extension to trace all programmatic tab changes
fun CustomBottomNavBar.debugSelect(id: Int, src: String = "") {
    Log.w("CBNV-TRACE", "select($id) from $src", Exception())
    setSelectedItem(id)
}

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl
    private lateinit var mainFragment: MainFragment
    private lateinit var mapFragment: StandardNavigationMapFragment
    private lateinit var historyFragment: HistoryFragment
    private lateinit var savedPagesFragment: SavedPagesFragment
    private lateinit var moreFragment: MoreFragment
    private lateinit var activeFragment: Fragment
    
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var isRefreshingColors: Boolean = false

    companion object {
        const val ACTION_NAVIGATE_TO_SEARCH = "com.omiyawaki.osrswiki.ACTION_NAVIGATE_TO_SEARCH"
        private const val MAIN_FRAGMENT_TAG = "main_fragment"
        private const val MAP_FRAGMENT_TAG = "map_fragment"
        private const val HISTORY_FRAGMENT_TAG = "history_fragment"
        private const val SAVED_PAGES_FRAGMENT_TAG = "saved_pages_fragment"
        private const val MORE_FRAGMENT_TAG = "more_fragment"
        private const val ACTIVE_FRAGMENT_TAG = "active_fragment_tag"
        private const val SAVED_SELECTED_NAV_ID = "selected_nav_id"
        private const val LIFECYCLE_TAG = "MainActivityLifecycle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LIFECYCLE_TAG, "onCreate() called. Saved state is ${if (savedInstanceState == null) "null" else "present"}")
        Log.i("StartupTiming", "MainActivity.onCreate() - Main activity starting")
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge but respect the theme's status bar settings
        enableEdgeToEdge()
        setupStatusBarTheming()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        L.d("MainActivity: onCreate: ContentView set.")
        
        // Start background map preloading immediately to enable instant map rendering
        lifecycleScope.launch {
            L.d("MainActivity: Starting background map preloading...")
            AndroidMapPreloader.getInstance().preloadMapInBackground(this@MainActivity)
        }

        // Handle system window insets to avoid content overlapping with status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only apply top and side padding to avoid overlapping with status bar
            // Bottom padding is handled by the constraint layout (bottom nav creates the gap)
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // Handle system navigation bar insets for the bottom navigation using translation
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Use translationY to move the view up without affecting layout space
            view.translationY = -navigationBars.bottom.toFloat()
            
            // Still apply horizontal margins for side insets
            val layoutParams = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            layoutParams.leftMargin = systemBars.left
            layoutParams.rightMargin = systemBars.right
            view.layoutParams = layoutParams
            
            insets
        }

        // Handle system navigation bar insets for the bottom navigation border using translation
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavBorder) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Use translationY to move the border up without affecting layout space
            view.translationY = -navigationBars.bottom.toFloat()
            
            // Still apply horizontal margins for side insets
            val layoutParams = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            layoutParams.leftMargin = systemBars.left
            layoutParams.rightMargin = systemBars.right
            view.layoutParams = layoutParams
            
            insets
        }

        appRouter = AppRouterImpl(supportFragmentManager, R.id.nav_host_container)
        L.d("MainActivity: onCreate: AppRouter initialized.")

        // Determine which navigation item should be selected
        val selectedNavId = savedInstanceState?.getInt(SAVED_SELECTED_NAV_ID) ?: R.id.nav_news
        L.d("MainActivity: onCreate: Selected nav ID: $selectedNavId (from savedState: ${savedInstanceState != null})")

        if (savedInstanceState == null) {
            L.d("MainActivity: onCreate: savedInstanceState is null, setting up initial fragments.")
            
            // Create new fragment instances
            mainFragment = MainFragment.newInstance()
            mapFragment = StandardNavigationMapFragment.newInstance(null, null, null, null)
            historyFragment = HistoryFragment.newInstance()
            savedPagesFragment = SavedPagesFragment()
            moreFragment = MoreFragment.newInstance()
            
            // Set initial active fragment based on selected nav (default or restored)
            // CRITICAL: This must be set synchronously BEFORE setupBottomNav() is called
            activeFragment = when (selectedNavId) {
                R.id.nav_map -> mapFragment
                R.id.nav_search -> historyFragment
                R.id.nav_saved -> savedPagesFragment
                R.id.nav_more -> moreFragment
                else -> mainFragment
            }
            
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_container, mainFragment, MAIN_FRAGMENT_TAG)
                .add(R.id.nav_host_container, mapFragment, MAP_FRAGMENT_TAG)
                .add(R.id.nav_host_container, historyFragment, HISTORY_FRAGMENT_TAG)
                .add(R.id.nav_host_container, savedPagesFragment, SAVED_PAGES_FRAGMENT_TAG)
                .add(R.id.nav_host_container, moreFragment, MORE_FRAGMENT_TAG)
                .runOnCommit {
                    // Hide all fragments except the active one using standard navigation
                    val transaction = supportFragmentManager.beginTransaction()
                    if (activeFragment !== mainFragment) transaction.hide(mainFragment)
                    if (activeFragment !== mapFragment) transaction.hide(mapFragment)
                    if (activeFragment !== historyFragment) transaction.hide(historyFragment)
                    if (activeFragment !== savedPagesFragment) transaction.hide(savedPagesFragment)
                    if (activeFragment !== moreFragment) transaction.hide(moreFragment)
                    transaction.commit()
                    
                    L.d("MainActivity: Standard navigation setup complete - only ${activeFragment.javaClass.simpleName} visible")
                }
                .commit()

            L.d("MainActivity: onCreate: Fragments added. Active fragment: ${activeFragment.javaClass.simpleName}")
        } else {
            L.d("MainActivity: onCreate: Restoring state.")
            val savedActiveTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG, MAIN_FRAGMENT_TAG)
            
            // Restore fragments from FragmentManager and assign to properties
            mainFragment = supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG) as MainFragment
            mapFragment = supportFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG) as StandardNavigationMapFragment
            historyFragment = supportFragmentManager.findFragmentByTag(HISTORY_FRAGMENT_TAG) as HistoryFragment
            savedPagesFragment = supportFragmentManager.findFragmentByTag(SAVED_PAGES_FRAGMENT_TAG) as SavedPagesFragment
            moreFragment = supportFragmentManager.findFragmentByTag(MORE_FRAGMENT_TAG) as MoreFragment
            
            activeFragment = when (savedActiveTag) {
                MAP_FRAGMENT_TAG -> mapFragment
                HISTORY_FRAGMENT_TAG -> historyFragment
                SAVED_PAGES_FRAGMENT_TAG -> savedPagesFragment
                MORE_FRAGMENT_TAG -> moreFragment
                else -> mainFragment
            }
            
            // Execute pending transactions to ensure fragments are attached
            supportFragmentManager.executePendingTransactions()
            
            // Use ViewTreeObserver to set fragment visibility after views are created using standard navigation
            binding.navHostContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // Remove listener to avoid multiple calls
                    binding.navHostContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    // Use standard navigation to set proper fragment visibility
                    val transaction = supportFragmentManager.beginTransaction()
                    if (activeFragment !== mainFragment) transaction.hide(mainFragment)
                    if (activeFragment !== mapFragment) transaction.hide(mapFragment)
                    if (activeFragment !== historyFragment) transaction.hide(historyFragment)
                    if (activeFragment !== savedPagesFragment) transaction.hide(savedPagesFragment)
                    if (activeFragment !== moreFragment) transaction.hide(moreFragment)
                    transaction.show(activeFragment)
                    transaction.commit()
                    
                    L.d("MainActivity: Standard navigation restored after layout")
                }
            })
            
            L.d("MainActivity: onCreate: Active fragment is ${activeFragment.javaClass.simpleName}")
        }

        // Set up bottom navigation AFTER fragment setup
        setupBottomNav()
        setupFonts()
        setupBackNavigation()
        
        // CRITICAL: Set the selected item AFTER all setup is complete
        // This prevents triggering the listener cascade during initialization
        L.d("MainActivity: onCreate: Setting bottom nav selectedItemId to $selectedNavId")
        binding.bottomNav.debugSelect(selectedNavId, "MainActivity#onCreate")
        setupThemeChangeReceiver()
        handleIntentExtras(intent)
        
        Log.i("StartupTiming", "MainActivity.onCreate() completed - Activity ready for display")
        
        // DEBUG: Extract actual colors for testing
        binding.root.post {
            ColorExtractor.exportColorsToJSON(this)
            testSearchItemColors()
            testSearchAdapterDirectly()
        }
    }
    
    private fun setupFonts() {
        L.d("MainActivity: Setting up navigation fonts...")
        
        // Apply fonts to bottom navigation labels
        // The BottomNavigationView creates TextViews internally, we need to traverse and apply fonts
        try {
            applyFontsToBottomNavigation(binding.bottomNav)
        } catch (e: Exception) {
            L.e("MainActivity: Error applying fonts to navigation: ${e.message}")
        }
        
        L.d("MainActivity: Navigation fonts setup complete")
    }
    
    private fun applyFontsToBottomNavigation(bottomNav: CustomBottomNavBar, selectedItemId: Int = bottomNav.selectedItemId) {
        // CustomBottomNavBar uses MaterialTextViews directly, making font application much simpler
        // The font styles are already applied via the CustomBottomNavButton style in styles.xml
        // This method is kept for compatibility but doesn't need to do complex traversal
        L.d("MainActivity: Font application for CustomBottomNavBar (managed by styles.xml)")
    }
    
    private fun applyFontsToViewGroup(viewGroup: android.view.ViewGroup, isActive: Boolean = false) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is TextView -> {
                    L.d("MainActivity: Navigation label: ${child.text}")
                }
                is android.view.ViewGroup -> {
                    applyFontsToViewGroup(child, isActive)
                }
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { itemId ->
            // Skip navigation if we're currently refreshing colors to prevent unwanted tab switches
            if (isRefreshingColors) {
                Log.d("MainActivity", "Skipping navigation during color refresh: $itemId")
                return@setOnItemSelectedListener true
            }
            
            Log.d("MainActivity", "Bottom nav item selected: $itemId")
            val selectedFragment = when (itemId) {
                R.id.nav_news -> {
                    Log.d("MainActivity", "Navigating to Home (MainFragment)")
                    mainFragment
                }
                R.id.nav_saved -> {
                    Log.d("MainActivity", "Navigating to Saved Pages")
                    savedPagesFragment
                }
                R.id.nav_map -> {
                    Log.d("MainActivity", "Navigating to Map")
                    mapFragment
                }
                R.id.nav_search -> {
                    // Check if current fragment is already HistoryFragment
                    if (activeFragment === historyFragment) {
                        // Second tap - open search activity
                        Log.d("MainActivity", "Second tap on search - opening SearchActivity")
                        val intent = Intent(this, SearchActivity::class.java)
                        startActivity(intent)
                        return@setOnItemSelectedListener true
                    }
                    Log.d("MainActivity", "Navigating to History/Search")
                    historyFragment
                }
                R.id.nav_more -> {
                    Log.d("MainActivity", "Navigating to More")
                    moreFragment
                }
                else -> {
                    Log.w("MainActivity", "Unknown navigation item: $itemId")
                    null
                }
            }

            if (selectedFragment != null && selectedFragment !== activeFragment) {
                L.d("MainActivity: Switching from ${activeFragment.javaClass.simpleName} to ${selectedFragment.javaClass.simpleName}")
                switchToFragment(selectedFragment)
                
                // Refresh fonts to update active/inactive styling
                try {
                    applyFontsToBottomNavigation(binding.bottomNav, itemId)
                } catch (e: Exception) {
                    L.e("MainActivity: Error refreshing navigation fonts: ${e.message}")
                }
                
                true
            } else {
                false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent) {
        if (intent.action == ACTION_NAVIGATE_TO_SEARCH) {
            L.d("MainActivity: Received ACTION_NAVIGATE_TO_SEARCH")
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, SearchFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }


    override fun onSupportNavigateUp(): Boolean {
        if (appRouter.goBack()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d(LIFECYCLE_TAG, "onSaveInstanceState() called.")
        super.onSaveInstanceState(outState)
        val activeTag = when (activeFragment) {
            mapFragment -> MAP_FRAGMENT_TAG
            historyFragment -> HISTORY_FRAGMENT_TAG
            savedPagesFragment -> SAVED_PAGES_FRAGMENT_TAG
            moreFragment -> MORE_FRAGMENT_TAG
            else -> MAIN_FRAGMENT_TAG
        }
        outState.putString(ACTIVE_FRAGMENT_TAG, activeTag)
        
        // CRITICAL: Save the currently selected bottom navigation item
        // This is the key fix for the navigation restoration issue
        val selectedNavId = binding.bottomNav.selectedItemId
        outState.putInt(SAVED_SELECTED_NAV_ID, selectedNavId)
        
        L.d("MainActivity: onSaveInstanceState: Saved active fragment tag: $activeTag, nav ID: $selectedNavId")
    }

    private fun setupBackNavigation() {
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                L.d("MainActivity: Back pressed, current fragment: ${activeFragment.javaClass.simpleName}")
                
                // If not on main fragment (Home), navigate to Home
                if (activeFragment !== mainFragment) {
                    L.d("MainActivity: Not on Home fragment, navigating to Home")
                    binding.bottomNav.debugSelect(R.id.nav_news, "MainActivity#backCallback")
                    return
                }
                
                // If on Home fragment, check appRouter first, then exit app
                L.d("MainActivity: On Home fragment, checking appRouter")
                if (!appRouter.goBack()) {
                    L.d("MainActivity: AppRouter returned false, exiting app")
                    finish()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onStart() {
        super.onStart()
        Log.d(LIFECYCLE_TAG, "onStart() called.")
    }
    
    override fun onResume() {
        Log.d(LIFECYCLE_TAG, "onResume() called.")
        Log.i("StartupTiming", "MainActivity.onResume() - Activity becoming available for preview generation")
        super.onResume() // This handles theme changes in BaseActivity
        
        // Capture live content bounds for theme previews (expert's solution)
        ContentBoundsProvider.publishFrom(this)
        
        // Reset state to allow generation even if WorkManager ran first
        PreviewGenerationManager.resetState()
        Log.i("StartupTiming", "MainActivity reset preview generation state")
        
        // Register this Activity for WebView context pooling
        Log.i("StartupTiming", "MainActivity registering with ActivityContextPool...")
        lifecycleScope.launch {
            ActivityContextPool.registerActivity(this@MainActivity)
            Log.i("StartupTiming", "MainActivity registered with ActivityContextPool - Now available for preview generation")
        }
        
        // Initialize background preview generation as soon as Activity context is available
        // This ensures previews are ready when users navigate to appearance settings
        val app = application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()
        Log.i("StartupTiming", "MainActivity starting preview generation for theme: ${currentTheme.tag}")
        lifecycleScope.launch {
            PreviewGenerationManager.initializeBackgroundGeneration(this@MainActivity, currentTheme)
        }
        
        // Post the theme change notification to ensure fragments are fully restored
        // and in a proper lifecycle state before receiving the notification
        binding.root.post {
            notifyFragmentsOfThemeChange()
            L.d("MainActivity: onResume: Notified fragments of theme change (posted)")
            
            // Ensure proper alpha state after theme change
            refreshFragmentVisibility()
        }
        
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(LIFECYCLE_TAG, "onPause() called.")
        
        // Unregister this Activity from WebView context pooling
        lifecycleScope.launch {
            ActivityContextPool.unregisterActivity(this@MainActivity)
        }
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(LIFECYCLE_TAG, "onStop() called.")
    }
    
    /**
     * Standard Fragment navigation using show/hide transactions
     * This replaces the alpha-based navigation approach with proper fragment lifecycle management
     */
    private fun switchToFragment(fragment: Fragment) {
        if (fragment === activeFragment) return
        
        L.d("MainActivity: Switching to ${fragment.javaClass.simpleName} using STANDARD navigation")
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // Hide current fragment (triggers onPause/onStop)
        activeFragment.let { transaction.hide(it) }
        
        // Show new fragment (triggers onStart/onResume)
        transaction.show(fragment)
        transaction.commit()
        
        activeFragment = fragment
    }
    
    private fun refreshFragmentVisibility() {
        // Ensure only the active fragment is visible after theme changes using standard navigation
        try {
            val transaction = supportFragmentManager.beginTransaction()
            if (activeFragment !== mainFragment) transaction.hide(mainFragment)
            if (activeFragment !== mapFragment) transaction.hide(mapFragment)
            if (activeFragment !== historyFragment) transaction.hide(historyFragment)
            if (activeFragment !== savedPagesFragment) transaction.hide(savedPagesFragment)
            if (activeFragment !== moreFragment) transaction.hide(moreFragment)
            transaction.show(activeFragment)
            transaction.commit()
            
            L.d("MainActivity: Fragment visibility refreshed after theme change using standard navigation")
        } catch (e: Exception) {
            L.e("MainActivity: Error refreshing fragment visibility: ${e.message}")
        }
    }
    
    private fun setupStatusBarTheming() {
        // Get the current theme's windowLightStatusBar setting
        val typedValue = TypedValue()
        val theme = this.theme
        val hasLightStatusBar = theme.resolveAttribute(android.R.attr.windowLightStatusBar, typedValue, true) && typedValue.data != 0
        
        // Get the status bar color from theme
        val statusBarColorTypedValue = TypedValue()
        val hasStatusBarColor = theme.resolveAttribute(android.R.attr.statusBarColor, statusBarColorTypedValue, true)
        
        // Apply the theme's status bar settings
        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.let { controller ->
            controller.isAppearanceLightStatusBars = hasLightStatusBar
            L.d("MainActivity: Set status bar light mode: $hasLightStatusBar")
        }
        
        // Set status bar color from theme if available
        if (hasStatusBarColor) {
            window.statusBarColor = statusBarColorTypedValue.data
            L.d("MainActivity: Applied theme status bar color: ${Integer.toHexString(statusBarColorTypedValue.data)}")
        }
    }
    
    private fun setupThemeChangeReceiver() {
        themeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == com.omiyawaki.osrswiki.settings.AppearanceSettingsFragment.ACTION_THEME_CHANGED) {
                    L.d("MainActivity: Received theme change broadcast")
                    // Apply theme dynamically without recreation
                    applyThemeDynamically()
                }
            }
        }
        
        val filter = IntentFilter(com.omiyawaki.osrswiki.settings.AppearanceSettingsFragment.ACTION_THEME_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(themeChangeReceiver!!, filter)
        L.d("MainActivity: Theme change receiver registered")
    }
    
    private fun unregisterThemeChangeReceiver() {
        themeChangeReceiver?.let { receiver ->
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            themeChangeReceiver = null
            L.d("MainActivity: Theme change receiver unregistered")
        }
    }
    
    override fun refreshThemeDependentElements() {
        super.refreshThemeDependentElements()
        L.d("MainActivity: Refreshing theme-dependent elements")
        
        // Refresh status bar theming
        setupStatusBarTheming()
        
        // Refresh fragment visibility to ensure proper alpha states
        refreshFragmentVisibility()
        
        // Refresh navigation fonts with new theme
        try {
            applyFontsToBottomNavigation(binding.bottomNav)
        } catch (e: Exception) {
            L.e("MainActivity: Error refreshing navigation fonts: ${e.message}")
        }
        
        // CRITICAL: Force BottomNavigationView to refresh its theme colors
        // BottomNavigationView has internal color management that needs special handling
        try {
            refreshBottomNavigationColors()
        } catch (e: Exception) {
            L.e("MainActivity: Error refreshing bottom navigation colors: ${e.message}")
        }
        
        // Refresh bottom navigation border color
        try {
            refreshBottomNavigationBorder()
        } catch (e: Exception) {
            L.e("MainActivity: Error refreshing bottom navigation border: ${e.message}")
        }
        
        // CRITICAL: Notify all fragments that theme has changed
        // This ensures fragments refresh their theme-dependent UI elements
        notifyFragmentsOfThemeChange()
        
        L.d("MainActivity: Theme-dependent elements refresh completed")
    }
    
    private fun refreshBottomNavigationColors() {
        L.d("MainActivity: CustomBottomNavBar colors managed by @color/bottom_nav_item_color resource")
        // CustomBottomNavBar uses color state lists defined in bottom_nav_item_color.xml
        // Colors automatically update with theme changes - no manual refresh needed
    }
    
    private fun refreshBottomNavigationBorder() {
        L.d("MainActivity: Refreshing bottom navigation border color")
        
        try {
            val borderView = findViewById<View>(R.id.bottom_nav_border)
            if (borderView != null) {
                val typedValue = TypedValue()
                if (theme.resolveAttribute(R.attr.border_color, typedValue, true)) {
                    val borderColor = typedValue.data
                    borderView.setBackgroundColor(borderColor)
                    L.d("MainActivity: Set new border color: ${Integer.toHexString(borderColor)}")
                } else {
                    L.w("MainActivity: Could not resolve border_color attribute")
                }
            } else {
                L.w("MainActivity: bottom_nav_border view not found")
            }
        } catch (e: Exception) {
            L.e("MainActivity: Failed to refresh bottom navigation border: ${e.message}")
        }
    }
    
    private fun testSearchAdapterDirectly() {
        Log.e("ColorTest", "\n=== DIRECT ADAPTER TEST ===")
        
        // Create test data
        val testItem = CleanedSearchResultItem(
            id = "1",
            title = "Test Dragon Item",
            snippet = "This is a test snippet about dragons",
            thumbnailUrl = null
        )
        
        // Create binding
        val testBinding = ItemSearchResultBinding.inflate(layoutInflater)
        
        // Create fake listener
        val listener = object : com.omiyawaki.osrswiki.search.SearchAdapter.OnItemClickListener {
            override fun onItemClick(item: CleanedSearchResultItem) {}
        }
        
        // Create view holder
        val viewHolder = com.omiyawaki.osrswiki.search.SearchAdapter.SearchResultViewHolder(testBinding, listener)
        
        // Test with query
        viewHolder.bind(testItem, "dragon")
        
        // Check final colors
        val titleColor = testBinding.searchItemTitle.currentTextColor
        val snippetColor = testBinding.searchItemSnippet.currentTextColor
        
        Log.e("ColorTest", "After real adapter bind:")
        Log.e("ColorTest", "Title: #${Integer.toHexString(titleColor)}")
        Log.e("ColorTest", "Snippet: #${Integer.toHexString(snippetColor)}")
        
        // Check the actual SpannableString spans
        val titleText = testBinding.searchItemTitle.text
        val snippetText = testBinding.searchItemSnippet.text
        
        if (titleText is SpannableString) {
            val titleSpans = titleText.getSpans(0, titleText.length, ForegroundColorSpan::class.java)
            Log.e("ColorTest", "Title has ${titleSpans.size} color spans")
            titleSpans.forEachIndexed { index, span ->
                val start = titleText.getSpanStart(span)
                val end = titleText.getSpanEnd(span)
                Log.e("ColorTest", "  Title span $index: [${start}-${end}] color=#${Integer.toHexString(span.foregroundColor)}")
            }
        }
        
        if (snippetText is SpannableString) {
            val snippetSpans = snippetText.getSpans(0, snippetText.length, ForegroundColorSpan::class.java)
            Log.e("ColorTest", "Snippet has ${snippetSpans.size} color spans")
            snippetSpans.forEachIndexed { index, span ->
                val start = snippetText.getSpanStart(span)
                val end = snippetText.getSpanEnd(span)
                Log.e("ColorTest", "  Snippet span $index: [${start}-${end}] color=#${Integer.toHexString(span.foregroundColor)}")
            }
        }
        
        // The real question: what color is the NON-HIGHLIGHTED text?
        Log.e("ColorTest", "\nNON-HIGHLIGHTED text uses TextView's currentTextColor:")
        Log.e("ColorTest", "  Title non-highlighted: #${Integer.toHexString(titleColor)}")
        Log.e("ColorTest", "  Snippet non-highlighted: #${Integer.toHexString(snippetColor)}")
        
        if (titleColor != snippetColor) {
            Log.e("ColorTest", "🔴 NON-HIGHLIGHTED TEXT COLORS DON'T MATCH!")
        } else {
            Log.e("ColorTest", "✅ Non-highlighted text colors match!")
        }
    }
    
    private fun testSearchItemColors() {
        Log.e("ColorTest", "=== MAIN ACTIVITY SEARCH ITEM COLOR TEST ===")
        
        // Create test item binding
        val testBinding = ItemSearchResultBinding.inflate(layoutInflater)
        
        // Set sample text
        testBinding.searchItemTitle.text = "Test Title"
        testBinding.searchItemSnippet.text = "Test Snippet"
        
        // Get colors
        val titleColor = testBinding.searchItemTitle.currentTextColor
        val snippetColor = testBinding.searchItemSnippet.currentTextColor
        
        Log.e("ColorTest", "Title color: #${Integer.toHexString(titleColor)} (${titleColor})")
        Log.e("ColorTest", "Snippet color: #${Integer.toHexString(snippetColor)} (${snippetColor})")
        
        if (titleColor != snippetColor) {
            Log.e("ColorTest", "🔴 COLORS DON'T MATCH!")
            Log.e("ColorTest", "Difference: ${titleColor - snippetColor}")
        } else {
            Log.e("ColorTest", "✅ Colors match!")
        }
        
        // Check theme attributes
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        Log.e("ColorTest", "colorOnSurface: #${Integer.toHexString(typedValue.data)} (${typedValue.data})")
        
        // Check if colors match colorOnSurface
        if (titleColor == typedValue.data) {
            Log.e("ColorTest", "Title matches colorOnSurface")
        } else {
            Log.e("ColorTest", "Title DOES NOT match colorOnSurface")
        }
        
        if (snippetColor == typedValue.data) {
            Log.e("ColorTest", "Snippet matches colorOnSurface")
        } else {
            Log.e("ColorTest", "Snippet DOES NOT match colorOnSurface")
        }
        
        // Now test with SpannableString (simulating what the adapter does)
        Log.e("ColorTest", "\n=== TESTING WITH SPANNABLE STRING ===")
        
        // Apply highlighting to title
        val titleSpannable = SpannableString("Test Dragon Title")
        // Highlight "Dragon"
        val dragonStart = 5
        val dragonEnd = 11
        titleSpannable.setSpan(
            ForegroundColorSpan(android.graphics.Color.parseColor("#8B7355")),
            dragonStart,
            dragonEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        titleSpannable.setSpan(
            StyleSpan(Typeface.BOLD),
            dragonStart,
            dragonEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Apply highlighting to snippet
        val snippetSpannable = SpannableString("Test dragon snippet")
        // Highlight "dragon"
        val snippetDragonStart = 5
        val snippetDragonEnd = 11
        snippetSpannable.setSpan(
            ForegroundColorSpan(android.graphics.Color.parseColor("#8B7355")),
            snippetDragonStart,
            snippetDragonEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        snippetSpannable.setSpan(
            StyleSpan(Typeface.BOLD),
            snippetDragonStart,
            snippetDragonEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        // Set the spannables
        testBinding.searchItemTitle.text = titleSpannable
        testBinding.searchItemSnippet.text = snippetSpannable
        
        // Apply font to title (like adapter does)
        testBinding.searchItemTitle.applyAlegreyaHeadline()
        
        // Check colors after spannable and font application
        val titleColorAfter = testBinding.searchItemTitle.currentTextColor
        val snippetColorAfter = testBinding.searchItemSnippet.currentTextColor
        
        Log.e("ColorTest", "After Spannable - Title color: #${Integer.toHexString(titleColorAfter)} (${titleColorAfter})")
        Log.e("ColorTest", "After Spannable - Snippet color: #${Integer.toHexString(snippetColorAfter)} (${snippetColorAfter})")
        
        if (titleColorAfter != snippetColorAfter) {
            Log.e("ColorTest", "🔴 COLORS DON'T MATCH AFTER SPANNABLE!")
            Log.e("ColorTest", "Difference: ${titleColorAfter - snippetColorAfter}")
        } else {
            Log.e("ColorTest", "✅ Colors still match after spannable!")
        }
    }
    
    override fun onDestroy() {
        Log.d(LIFECYCLE_TAG, "onDestroy() called.")
        unregisterThemeChangeReceiver()
        super.onDestroy()
    }
    
}
