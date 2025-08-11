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
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.history.HistoryFragment
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.map.MapFragment
import com.omiyawaki.osrswiki.ui.more.MoreFragment
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.FontUtil
import android.widget.TextView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.omiyawaki.osrswiki.settings.ContentBoundsProvider
import com.omiyawaki.osrswiki.settings.PreviewGenerationManager

// Debug extension to trace all programmatic tab changes
fun BottomNavigationView.debugSelect(id: Int, src: String = "") {
    Log.w("BNV-TRACE", "select($id) from $src", Exception())
    selectedItemId = id
}

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl
    private lateinit var mainFragment: MainFragment
    private lateinit var mapFragment: MapFragment
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
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge but respect the theme's status bar settings
        enableEdgeToEdge()
        setupStatusBarTheming()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        L.d("MainActivity: onCreate: ContentView set.")

        // Handle system window insets to avoid content overlapping with status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only apply top and side padding to avoid overlapping with status bar
            // Bottom padding is handled by the constraint layout (bottom nav creates the gap)
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
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
            mapFragment = MapFragment()
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
                    // Set alpha values based on initial selection (async after views are created)
                    mainFragment.view?.alpha = if (activeFragment === mainFragment) 1.0f else 0.0f
                    mapFragment.view?.alpha = if (activeFragment === mapFragment) 1.0f else 0.0f
                    historyFragment.view?.alpha = if (activeFragment === historyFragment) 1.0f else 0.0f
                    savedPagesFragment.view?.alpha = if (activeFragment === savedPagesFragment) 1.0f else 0.0f
                    moreFragment.view?.alpha = if (activeFragment === moreFragment) 1.0f else 0.0f
                    
                    // Bring active fragment to front so it can receive touches
                    activeFragment.view?.bringToFront()
                    
                    L.d("MainActivity: Views alpha set and active fragment brought to front")
                }
                .commit()

            L.d("MainActivity: onCreate: Fragments added. Active fragment: ${activeFragment.javaClass.simpleName}")
        } else {
            L.d("MainActivity: onCreate: Restoring state.")
            val savedActiveTag = savedInstanceState.getString(ACTIVE_FRAGMENT_TAG, MAIN_FRAGMENT_TAG)
            
            // Restore fragments from FragmentManager and assign to properties
            mainFragment = supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG) as MainFragment
            mapFragment = supportFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG) as MapFragment
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
            
            // Use ViewTreeObserver to set alpha after views are created and laid out
            binding.navHostContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // Remove listener to avoid multiple calls
                    binding.navHostContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    // Now views should exist, set alpha values
                    mainFragment.view?.alpha = if (activeFragment === mainFragment) 1.0f else 0.0f
                    mapFragment.view?.alpha = if (activeFragment === mapFragment) 1.0f else 0.0f
                    historyFragment.view?.alpha = if (activeFragment === historyFragment) 1.0f else 0.0f
                    savedPagesFragment.view?.alpha = if (activeFragment === savedPagesFragment) 1.0f else 0.0f
                    moreFragment.view?.alpha = if (activeFragment === moreFragment) 1.0f else 0.0f
                    
                    // Bring active fragment to front to ensure it receives touches
                    activeFragment.view?.bringToFront()
                    
                    L.d("MainActivity: Alpha values restored after layout")
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
    
    private fun applyFontsToBottomNavigation(bottomNav: com.google.android.material.bottomnavigation.BottomNavigationView, selectedItemId: Int = bottomNav.selectedItemId) {
        // Access the BottomNavigationMenuView which contains the individual tabs
        val menuView = bottomNav.getChildAt(0) as com.google.android.material.bottomnavigation.BottomNavigationMenuView
        
        for (i in 0 until menuView.childCount) {
            val item = menuView.getChildAt(i)
            val menuItem = bottomNav.menu.getItem(i)
            val isActive = menuItem.itemId == selectedItemId
            
            // Find TextView children and apply font based on active state
            applyFontsToViewGroup(item as android.view.ViewGroup, isActive)
        }
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
        binding.bottomNav.setOnItemSelectedListener { item ->
            // Skip navigation if we're currently refreshing colors to prevent unwanted tab switches
            if (isRefreshingColors) {
                Log.d("MainActivity", "Skipping navigation during color refresh: ${item.itemId}")
                return@setOnItemSelectedListener true
            }
            
            Log.d("MainActivity", "Bottom nav item selected: ${item.itemId}")
            val selectedFragment = when (item.itemId) {
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
                    Log.w("MainActivity", "Unknown navigation item: ${item.itemId}")
                    null
                }
            }

            if (selectedFragment != null && selectedFragment !== activeFragment) {
                L.d("MainActivity: Switching from ${activeFragment.javaClass.simpleName} to ${selectedFragment.javaClass.simpleName}")

                activeFragment.view?.alpha = 0.0f
                selectedFragment.view?.alpha = 1.0f
                selectedFragment.view?.bringToFront()

                activeFragment = selectedFragment
                
                // Refresh fonts to update active/inactive styling
                try {
                    applyFontsToBottomNavigation(binding.bottomNav, item.itemId)
                } catch (e: Exception) {
                    L.e("MainActivity: Error refreshing navigation fonts: ${e.message}")
                }
                
                true
            } else {
                false
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleIntentExtras(it) }
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
        super.onResume() // This handles theme changes in BaseActivity
        
        // Capture live content bounds for theme previews (expert's solution)
        ContentBoundsProvider.publishFrom(this)
        
        // Initialize background preview generation as soon as Activity context is available
        // This ensures previews are ready when users navigate to appearance settings
        val app = application as OSRSWikiApp
        val currentTheme = app.getCurrentTheme()
        PreviewGenerationManager.initializeBackgroundGeneration(this, currentTheme)
        
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
    
    private fun refreshFragmentVisibility() {
        // Ensure only the active fragment is visible after theme changes
        try {
            mainFragment.view?.alpha = if (activeFragment === mainFragment) 1.0f else 0.0f
            mapFragment.view?.alpha = if (activeFragment === mapFragment) 1.0f else 0.0f
            historyFragment.view?.alpha = if (activeFragment === historyFragment) 1.0f else 0.0f
            savedPagesFragment.view?.alpha = if (activeFragment === savedPagesFragment) 1.0f else 0.0f
            moreFragment.view?.alpha = if (activeFragment === moreFragment) 1.0f else 0.0f
            
            // Ensure active fragment is in front
            activeFragment.view?.bringToFront()
            
            L.d("MainActivity: Fragment visibility refreshed after theme change")
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
        L.d("MainActivity: Refreshing bottom navigation colors comprehensively")
        
        try {
            val theme = this.theme
            val typedValue = TypedValue()
            
            // CRITICAL: Force active indicator color refresh (for the pill background)
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
                val indicatorColor = typedValue.data
                binding.bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(indicatorColor)
                L.d("MainActivity: Set new indicator color: ${Integer.toHexString(indicatorColor)}")
            }
            
            // Update icon and text color state lists
            if (theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, typedValue, true)) {
                val selectedColor = typedValue.data
                
                // Try comprehensive list of unselected color attributes
                val unselectedColorAttrs = arrayOf(
                    com.google.android.material.R.attr.colorOnSurface,          // Used in bottom nav selectors
                    com.google.android.material.R.attr.colorOnSurfaceVariant,   // Alternative for unselected state
                    R.attr.primary_text_color,              // App fallback
                    android.R.attr.textColorPrimary         // System fallback
                )
                
                for (unselectedAttr in unselectedColorAttrs) {
                    if (theme.resolveAttribute(unselectedAttr, typedValue, true)) {
                        val unselectedColor = typedValue.data
                        
                        // Create color state list for the new theme
                        val states = arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        )
                        val colors = intArrayOf(selectedColor, unselectedColor)
                        val colorStateList = ColorStateList(states, colors)
                        
                        // Apply new colors to bottom navigation
                        binding.bottomNav.itemIconTintList = colorStateList
                        binding.bottomNav.itemTextColor = colorStateList
                        
                        L.d("MainActivity: Applied new color state list (selected: ${Integer.toHexString(selectedColor)}, unselected: ${Integer.toHexString(unselectedColor)})")
                        break // Use the first unselected color that resolves
                    }
                }
            }
            
            // Refresh background color using comprehensive attribute list
            val backgroundColorAttrs = arrayOf(
                R.attr.bottom_nav_background_color,     // App-specific
                com.google.android.material.R.attr.colorSurface,            // Material 3 default
                R.attr.paper_color,                     // App fallback
                android.R.attr.colorBackground          // System fallback
            )
            
            for (backgroundAttr in backgroundColorAttrs) {
                if (theme.resolveAttribute(backgroundAttr, typedValue, true)) {
                    binding.bottomNav.setBackgroundColor(typedValue.data)
                    L.d("MainActivity: Applied new background color: ${Integer.toHexString(typedValue.data)}")
                    break
                }
            }
            
            // Force Material component refresh via selection cycling
            // CRITICAL: Use flag to prevent navigation changes during cycling
            try {
                val currentSelection = binding.bottomNav.selectedItemId
                val menu = binding.bottomNav.menu
                
                if (menu.size() > 1) {
                    for (i in 0 until menu.size()) {
                        val item = menu.getItem(i)
                        if (item.itemId != currentSelection) {
                            L.d("MainActivity: Cycling to refresh colors: ${item.itemId} → $currentSelection")
                            isRefreshingColors = true // Set flag to block navigation
                            binding.bottomNav.selectedItemId = item.itemId // This will be blocked by the flag
                            binding.bottomNav.post {
                                binding.bottomNav.selectedItemId = currentSelection // This will also be blocked
                                isRefreshingColors = false // Clear flag after cycling
                                L.d("MainActivity: Forced navigation refresh via selection cycling")
                            }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                L.w("MainActivity: Selection cycling failed: ${e.message}")
                isRefreshingColors = false // Ensure flag is cleared on error
            }
            
            // Force redraw and layout
            binding.bottomNav.invalidate()
            binding.bottomNav.requestLayout()
            
        } catch (e: Exception) {
            L.e("MainActivity: Failed to refresh bottom navigation colors: ${e.message}")
        }
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
        super.onDestroy()
    }
    
}
