package com.omiyawaki.osrswiki

import android.content.Intent // Added for onNewIntent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.page.ViewHideHandler
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.main.MainScrollableViewProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy
import com.omiyawaki.osrswiki.util.log.L

class MainActivity : AppCompatActivity(), MainScrollableViewProvider, SavedPagesFragment.NavigationProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainToolbarHideHandler: ViewHideHandler
    private lateinit var appRouter: AppRouterImpl

    // Public accessor for the main toolbar
    val mainBindingToolbar: MaterialToolbar get() = binding.mainToolbar

    companion object {
        const val ACTION_NAVIGATE_TO_SEARCH = "com.omiyawaki.osrswiki.ACTION_NAVIGATE_TO_SEARCH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        L.d("MainActivity: onCreate: ContentView set.")

        appRouter = AppRouterImpl(supportFragmentManager, R.id.main_fragment_container)
        L.d("MainActivity: onCreate: AppRouter initialized.")

        setSupportActionBar(binding.mainToolbar)
        L.d("MainActivity: onCreate: mainToolbar set as SupportActionBar.")
        supportActionBar?.title = getString(R.string.app_name)
        L.d("MainActivity: onCreate: Default title set to '${supportActionBar?.title}'")

        mainToolbarHideHandler = ViewHideHandler(
            hideableView = binding.mainToolbarContainer,
            anchoredView = null,
            gravity = Gravity.TOP,
            updateElevation = true,
            shouldAlwaysShow = { false }
        )
        L.d("MainActivity: onCreate: mainToolbarHideHandler initialized.")

        if (savedInstanceState == null) {
            L.d("MainActivity: onCreate: savedInstanceState is null, replacing with MainFragment.")
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, MainFragment.newInstance(), MainFragment::class.java.simpleName) // Added tag
                .commitNow()
            L.d("MainActivity: onCreate: MainFragment committed.")
        } else {
            L.d("MainActivity: onCreate: savedInstanceState is NOT null, fragment should be restored.")
        }

        handleIntentExtras(intent) // Handle intent that started this activity
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        L.d("MainActivity: onNewIntent called.")
        // It's important to set the new intent, as getIntent() will return the original one otherwise
        setIntent(intent)
        intent?.let { handleIntentExtras(it) }
    }

    private fun handleIntentExtras(intent: Intent) {
        if (intent.action == ACTION_NAVIGATE_TO_SEARCH) {
            L.d("MainActivity: Received ACTION_NAVIGATE_TO_SEARCH")
            // Ensure MainFragment is present before calling its method
            // Using post to ensure fragment transaction (if any) from onCreate has completed
            binding.root.post {
                val mainFragment = supportFragmentManager.findFragmentByTag(MainFragment::class.java.simpleName) as? MainFragment
                if (mainFragment != null && mainFragment.isAdded) {
                    mainFragment.navigateToSearchTab()
                    L.d("MainActivity: Instructed MainFragment to navigate to search tab.")
                } else {
                    L.e("MainActivity: MainFragment not found or not added. Cannot navigate to search tab programmatically.")
                    // Fallback: Could store a flag and have MainFragment check on attach,
                    // or try finding by ID if tag fails (R.id.main_fragment_container)
                    val mainFragmentById = supportFragmentManager.findFragmentById(R.id.main_fragment_container) as? MainFragment
                    if (mainFragmentById != null && mainFragmentById.isAdded) {
                         mainFragmentById.navigateToSearchTab()
                         L.d("MainActivity: Instructed MainFragment (found by ID) to navigate to search tab.")
                    } else {
                        L.e("MainActivity: MainFragment also not found by ID. Navigation might fail if activity restarts before fragment is ready.")
                    }
                }
            }
        }
    }


    override fun updateToolbarState(hostFragment: Fragment?, scrollableView: View?, policy: ToolbarPolicy) {
        L.d("MainActivity: updateToolbarState called. Policy: $policy, HostFragment: ${hostFragment?.javaClass?.simpleName}, Current ToolbarContainer Visibility: ${binding.mainToolbarContainer.visibility}")

        when (policy) {
            ToolbarPolicy.HIDDEN -> {
                binding.mainToolbarContainer.visibility = View.GONE
                mainToolbarHideHandler.enabled = false
                mainToolbarHideHandler.setScrollableSource(null)
                L.d("MainActivity: updateToolbarState: Policy HIDDEN applied. mainToolbarContainer.visibility SET TO GONE.")
            }
            ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT -> {
                binding.mainToolbarContainer.visibility = View.VISIBLE
                mainToolbarHideHandler.enabled = true
                mainToolbarHideHandler.setScrollableSource(scrollableView)
                L.d("MainActivity: updateToolbarState: Policy COLLAPSIBLE_WITH_CONTENT applied. mainToolbarContainer.visibility SET TO VISIBLE.")
            }
        }
        L.d("MainActivity: updateToolbarState: AFTER apply. mainToolbarContainer.visibility IS NOW ${binding.mainToolbarContainer.visibility}")
    }

    override fun displayPageFragment(pageApiTitle: String?, pageNumericId: String?, source: Int) {
        L.d("MainActivity: displayPageFragment called with apiTitle='$pageApiTitle', numericId='$pageNumericId', source='$source'. Current mainToolbarContainer visibility: ${binding.mainToolbarContainer.visibility}")
        appRouter.navigateToPage(pageId = pageNumericId, pageTitle = pageApiTitle, source = source)
        L.d("MainActivity: displayPageFragment: appRouter.navigateToPage called. Check toolbar visibility shortly after if transaction is async.")
    }

    override fun onResume() {
        super.onResume()
        val initialIsMainToolbarShown = binding.mainToolbar.isShown
        val initialMainToolbarHeight = binding.mainToolbar.height
        val initialMainToolbarMenuSize = binding.mainToolbar.menu.size()
        val initialMainToolbarTitle = supportActionBar?.title
        L.d("MainActivity: onResume (Initial Check): mainToolbarContainer.visibility=${binding.mainToolbarContainer.visibility}, mainToolbar.isShown=$initialIsMainToolbarShown, mainToolbar.height=$initialMainToolbarHeight, mainToolbar.menu.size=$initialMainToolbarMenuSize, supportActionBar.title='$initialMainToolbarTitle'")

        invalidateOptionsMenu()
        L.d("MainActivity: onResume: invalidateOptionsMenu() called.")

        binding.root.post {
            val postedIsMainToolbarShown = binding.mainToolbar.isShown
            val postedMainToolbarHeight = binding.mainToolbar.height
            val postedMainToolbarMenuSize = binding.mainToolbar.menu.size()
            val postedMainToolbarTitle = supportActionBar?.title
            val tabsMenuItem = binding.mainToolbar.menu.findItem(R.id.menu_tabs)
            val isTabsActionViewPresent = tabsMenuItem?.actionView != null
            L.d("MainActivity: onResume (Posted Check): mainToolbar.isShown=$postedIsMainToolbarShown, mainToolbar.height=$postedMainToolbarHeight, mainToolbar.menu.size=$postedMainToolbarMenuSize, title='$postedMainToolbarTitle', menu_tabs found: ${tabsMenuItem != null}, menu_tabs.actionView present: $isTabsActionViewPresent")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        L.d("MainActivity: onSupportNavigateUp called.")
        if (appRouter.goBack()) {
            L.d("MainActivity: onSupportNavigateUp: AppRouter handled back navigation.")
            return true
        }
        L.d("MainActivity: onSupportNavigateUp: AppRouter did not handle back, calling super.")
        return super.onSupportNavigateUp()
    }

    override fun onBackPressed() { // Changed from override fun onBackPressed() to match typical signature
        L.d("MainActivity: onBackPressed called.")
        if (appRouter.goBack()) {
            L.d("MainActivity: onBackPressed: AppRouter handled back navigation.")
            return
        }
        L.d("MainActivity: onBackPressed: AppRouter did not handle back, calling super.onBackPressed().")
        super.onBackPressed() // Ensure super.onBackPressed() is called if not handled
    }
}
