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
import com.omiyawaki.osrswiki.activity.EdgeToEdgeInsetCoordinator
import androidx.core.graphics.Insets
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.history.HistoryFragment
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import android.net.Uri
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.main.MainNavigationInsetPolicy
import com.omiyawaki.osrswiki.ui.map.AndroidMapPreloader
import com.omiyawaki.osrswiki.undergroundmaps.osrsUndergroundMapsFragment
import com.omiyawaki.osrswiki.ui.more.MoreFragment
import android.view.Gravity
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.FontUtil
import android.widget.TextView
import com.omiyawaki.osrswiki.settings.ContentBoundsProvider
import com.omiyawaki.osrswiki.settings.Prefs
import com.omiyawaki.osrswiki.views.CustomBottomNavBar
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl
    private lateinit var mainFragment: MainFragment
    private lateinit var mapFragment: osrsUndergroundMapsFragment
    private lateinit var historyFragment: HistoryFragment
    private lateinit var savedPagesFragment: SavedPagesFragment
    private lateinit var moreFragment: MoreFragment
    private lateinit var activeFragment: Fragment
    
    private var themeChangeReceiver: BroadcastReceiver? = null
    private var isRefreshingColors: Boolean = false
    private var mainSystemBarInsets: Insets = Insets.NONE

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
        if (intent.getBooleanExtra("osrs_disable_article_prewarm", false)) {
            Prefs.disableArticlePrewarm = true
        }
        
        // Enable edge-to-edge but respect the theme's status bar settings
        enableEdgeToEdge()
        setupStatusBarTheming()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        L.d("MainActivity: onCreate: ContentView set.")
        
        // Handle system window insets to avoid content overlapping with status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostContainer) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val systemBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                cutout
            )
            mainSystemBarInsets = systemBars
            val navigationBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()),
                cutout
            )
            val layoutParams = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            val bottomMargin = MainNavigationInsetPolicy.hostBottomMarginForNavigationInset(navigationBars.bottom)
            if (layoutParams.bottomMargin != bottomMargin) {
                layoutParams.bottomMargin = bottomMargin
                view.layoutParams = layoutParams
            }

            // Apply top and side padding to avoid overlapping with status and side system bars.
            // The bottom margin tracks the translated bottom navigation.
            applyNavigationHostContentInsets()
            insets
        }

        // Handle system navigation bar insets for the bottom navigation using translation
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navigationBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()),
                cutout
            )
            val systemBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                cutout
            )
            
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
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navigationBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()),
                cutout
            )
            val systemBars = EdgeToEdgeInsetCoordinator.maxPerEdge(
                insets.getInsets(WindowInsetsCompat.Type.systemBars()),
                cutout
            )
            
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
            mapFragment = osrsUndergroundMapsFragment()
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
                .add(R.id.nav_host_container, historyFragment, HISTORY_FRAGMENT_TAG)
                .add(R.id.nav_host_container, savedPagesFragment, SAVED_PAGES_FRAGMENT_TAG)
                .add(R.id.nav_host_container, moreFragment, MORE_FRAGMENT_TAG)
                .runOnCommit {
                    refreshFragmentVisibility("initial setup")
                }
                .commit()

            L.d("MainActivity: onCreate: Fragments added. Active fragment: ${activeFragment.javaClass.simpleName}")
        } else {
            L.d("MainActivity: onCreate: Restoring state.")
            val savedActiveTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG, MAIN_FRAGMENT_TAG)
            
            // Restore fragments from FragmentManager and assign to properties
            mainFragment = restoreFragment(MAIN_FRAGMENT_TAG) { MainFragment.newInstance() }
            mapFragment = restoreFragment(MAP_FRAGMENT_TAG, addIfMissing = false) {
                osrsUndergroundMapsFragment()
            }
            historyFragment = restoreFragment(HISTORY_FRAGMENT_TAG) { HistoryFragment.newInstance() }
            savedPagesFragment = restoreFragment(SAVED_PAGES_FRAGMENT_TAG) { SavedPagesFragment() }
            moreFragment = restoreFragment(MORE_FRAGMENT_TAG) { MoreFragment.newInstance() }
            
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
                    refreshFragmentVisibility("restore after layout")
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
        binding.bottomNav.setSelectedItem(selectedNavId)
        setupThemeChangeReceiver()
        handleIntentExtras(intent)
        
        Log.i("StartupTiming", "MainActivity.onCreate() completed - Activity ready for display")
    }

    private inline fun <reified T : Fragment> restoreFragment(
        tag: String,
        addIfMissing: Boolean = true,
        factory: () -> T
    ): T {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing is T) {
            return existing
        }

        if (existing != null && !supportFragmentManager.isStateSaved) {
            L.w("MainActivity: Removing unexpected fragment ${existing.javaClass.simpleName} for tag $tag")
            supportFragmentManager.beginTransaction()
                .remove(existing)
                .commitNow()
        }

        val fragment = factory()
        if (addIfMissing && !supportFragmentManager.isStateSaved) {
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_container, fragment, tag)
                .commitNow()
        } else {
            L.d("MainActivity: Skipping fragment recreation for $tag after state save")
        }
        return fragment
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
                    Log.d("MainActivity", "Navigating to integrated realm map")
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
                if (switchToFragment(selectedFragment)) {
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
        if (intent.action == Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "osrswiki" && data.host == "page") {
                val raw = data.pathSegments?.lastOrNull() ?: data.lastPathSegment
                val title = raw?.let { Uri.decode(it).replace('_', ' ') }
                if (!title.isNullOrBlank()) {
                    startActivity(
                        PageActivity.newIntent(
                            this,
                            title,
                            null,
                            HistoryEntry.SOURCE_INTERNAL_LINK
                        )
                    )
                }
                intent.action = null
                intent.data = null
                return
            }
        }
        if (intent.action == ACTION_NAVIGATE_TO_SEARCH) {
            L.d("MainActivity: Received ACTION_NAVIGATE_TO_SEARCH")
            // SearchFragment is intentionally hosted by SearchActivity and reads that activity's
            // toolbar binding. Attaching it to exported MainActivity makes an explicit navigation
            // intent crash with ClassCastException. Consume the one-shot action before launching so
            // recreation cannot create duplicate search screens.
            intent.action = null
            startActivity(Intent(this, SearchActivity::class.java))
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
                    // Programmatic selection deliberately does not invoke the bottom-nav
                    // listener. Move the content first, then synchronize the visual selection;
                    // otherwise Back only repaints Home while leaving the previous tab active.
                    if (switchToFragment(mainFragment)) {
                        binding.bottomNav.setSelectedItem(R.id.nav_news)
                        applyFontsToBottomNavigation(binding.bottomNav, R.id.nav_news)
                    }
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
        super.onResume() // This handles theme changes in BaseActivity
        
        // Capture live content bounds for theme previews (expert's solution)
        ContentBoundsProvider.publishFrom(this)

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
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(LIFECYCLE_TAG, "onStop() called.")
    }
    
    /**
     * Standard Fragment navigation using show/hide transactions
     * This replaces the alpha-based navigation approach with proper fragment lifecycle management
     */
    private fun switchToFragment(fragment: Fragment): Boolean {
        if (fragment === activeFragment) return true
        if (supportFragmentManager.isStateSaved) {
            L.d("MainActivity: Skipping fragment switch after state save")
            return false
        }
        
        L.d("MainActivity: Switching to ${fragment.javaClass.simpleName} using STANDARD navigation")
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // Hide current fragment (triggers onPause/onStop)
        activeFragment.let { transaction.hide(it) }
        
        // Lazily create the map so Home startup does not initialize MapLibre or the realm catalog.
        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            val tag = if (fragment === mapFragment) MAP_FRAGMENT_TAG else null
            transaction.add(R.id.nav_host_container, fragment, tag)
        }
        transaction.commit()
        
        activeFragment = fragment
        applyNavigationHostContentInsets()
        return true
    }

    private fun applyNavigationHostContentInsets() {
        if (!::binding.isInitialized) return
        val mapOwnsInsets = ::activeFragment.isInitialized && activeFragment === mapFragment
        binding.navHostContainer.setPadding(
            if (mapOwnsInsets) 0 else mainSystemBarInsets.left,
            if (mapOwnsInsets) 0 else mainSystemBarInsets.top,
            if (mapOwnsInsets) 0 else mainSystemBarInsets.right,
            0
        )
    }
    
    private fun refreshFragmentVisibility(reason: String = "theme change") {
        // Ensure only the active fragment is visible after theme changes using standard navigation
        if (!::mainFragment.isInitialized || !::mapFragment.isInitialized ||
            !::historyFragment.isInitialized || !::savedPagesFragment.isInitialized ||
            !::moreFragment.isInitialized || !::activeFragment.isInitialized
        ) {
            L.d("MainActivity: Skipping fragment visibility refresh before fragments are initialized ($reason)")
            return
        }

        if (supportFragmentManager.isStateSaved) {
            L.d("MainActivity: Skipping fragment visibility refresh after state save ($reason)")
            return
        }

        try {
            val transaction = supportFragmentManager.beginTransaction()
            if (activeFragment !== mainFragment) transaction.hide(mainFragment)
            if (mapFragment.isAdded && activeFragment !== mapFragment) transaction.hide(mapFragment)
            if (activeFragment !== historyFragment) transaction.hide(historyFragment)
            if (activeFragment !== savedPagesFragment) transaction.hide(savedPagesFragment)
            if (activeFragment !== moreFragment) transaction.hide(moreFragment)
            if (activeFragment.isAdded) {
                transaction.show(activeFragment)
            } else {
                val tag = if (activeFragment === mapFragment) MAP_FRAGMENT_TAG else null
                transaction.add(R.id.nav_host_container, activeFragment, tag)
            }
            transaction.commit()
            applyNavigationHostContentInsets()
            
            L.d("MainActivity: Fragment visibility refreshed using standard navigation ($reason)")
        } catch (e: Exception) {
            L.w("MainActivity: Fragment visibility refresh skipped: ${e.message}")
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
    
    override fun onDestroy() {
        Log.d(LIFECYCLE_TAG, "onDestroy() called.")
        unregisterThemeChangeReceiver()
        AndroidMapPreloader.getInstance().destroy()
        super.onDestroy()
    }
    
}
