package com.omiyawaki.osrswiki

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
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

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl
    private lateinit var mainFragment: MainFragment
    private lateinit var mapFragment: MapFragment
    private lateinit var historyFragment: HistoryFragment
    private lateinit var savedPagesFragment: SavedPagesFragment
    private lateinit var moreFragment: MoreFragment
    private lateinit var activeFragment: Fragment

    companion object {
        const val ACTION_NAVIGATE_TO_SEARCH = "com.omiyawaki.osrswiki.ACTION_NAVIGATE_TO_SEARCH"
        private const val MAIN_FRAGMENT_TAG = "main_fragment"
        private const val MAP_FRAGMENT_TAG = "map_fragment"
        private const val HISTORY_FRAGMENT_TAG = "history_fragment"
        private const val SAVED_PAGES_FRAGMENT_TAG = "saved_pages_fragment"
        private const val MORE_FRAGMENT_TAG = "more_fragment"
        private const val ACTIVE_FRAGMENT_TAG = "active_fragment_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        if (savedInstanceState == null) {
            L.d("MainActivity: onCreate: savedInstanceState is null, setting up initial fragments.")
            
            // Create new fragment instances
            mainFragment = MainFragment.newInstance()
            mapFragment = MapFragment()
            historyFragment = HistoryFragment.newInstance()
            savedPagesFragment = SavedPagesFragment()
            moreFragment = MoreFragment.newInstance()
            
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_container, mainFragment, MAIN_FRAGMENT_TAG)
                .add(R.id.nav_host_container, mapFragment, MAP_FRAGMENT_TAG)
                .add(R.id.nav_host_container, historyFragment, HISTORY_FRAGMENT_TAG)
                .add(R.id.nav_host_container, savedPagesFragment, SAVED_PAGES_FRAGMENT_TAG)
                .add(R.id.nav_host_container, moreFragment, MORE_FRAGMENT_TAG)
                .runOnCommit {
                    // Ensure only mainFragment is visible on startup
                    mapFragment.view?.alpha = 0.0f
                    historyFragment.view?.alpha = 0.0f
                    savedPagesFragment.view?.alpha = 0.0f
                    moreFragment.view?.alpha = 0.0f
                    mainFragment.view?.alpha = 1.0f
                    
                    // FIX: Bring mainFragment to front so it can receive touches
                    mainFragment.view?.bringToFront()
                }
                .commit()

            activeFragment = mainFragment
            L.d("MainActivity: onCreate: Fragments added. MainFragment is visible, others are hidden.")
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
            
            // Restore bottom navigation selected state
            val selectedItemId = when (activeFragment) {
                mapFragment -> R.id.nav_map
                historyFragment -> R.id.nav_search
                savedPagesFragment -> R.id.nav_saved
                moreFragment -> R.id.nav_more
                else -> R.id.nav_news
            }
            binding.bottomNav.selectedItemId = selectedItemId
            
            L.d("MainActivity: onCreate: Active fragment is ${activeFragment.javaClass.simpleName}")
        }

        setupBottomNav()
        setupFonts()
        setupBackNavigation()
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
        super.onSaveInstanceState(outState)
        val activeTag = when (activeFragment) {
            mapFragment -> MAP_FRAGMENT_TAG
            historyFragment -> HISTORY_FRAGMENT_TAG
            savedPagesFragment -> SAVED_PAGES_FRAGMENT_TAG
            moreFragment -> MORE_FRAGMENT_TAG
            else -> MAIN_FRAGMENT_TAG
        }
        outState.putString(ACTIVE_FRAGMENT_TAG, activeTag)
        L.d("MainActivity: onSaveInstanceState: Saved active fragment tag: $activeTag")
    }

    private fun setupBackNavigation() {
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                L.d("MainActivity: Back pressed, current fragment: ${activeFragment.javaClass.simpleName}")
                
                // If not on main fragment (Home), navigate to Home
                if (activeFragment !== mainFragment) {
                    L.d("MainActivity: Not on Home fragment, navigating to Home")
                    binding.bottomNav.selectedItemId = R.id.nav_news
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

    override fun onResume() {
        super.onResume() // This handles theme changes in BaseActivity
        
        // Post the theme change notification to ensure fragments are fully restored
        // and in a proper lifecycle state before receiving the notification
        binding.root.post {
            notifyFragmentsOfThemeChange()
            L.d("MainActivity: onResume: Notified fragments of theme change (posted)")
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
}
