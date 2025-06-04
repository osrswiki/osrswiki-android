package com.omiyawaki.osrswiki

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment // Existing import
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.navigation.AppRouterImpl // Added import for AppRouter
import com.omiyawaki.osrswiki.page.ViewHideHandler
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment // Added import for NavigationProvider
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.main.MainScrollableViewProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy
import com.omiyawaki.osrswiki.util.log.L

class MainActivity : AppCompatActivity(), MainScrollableViewProvider, SavedPagesFragment.NavigationProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainToolbarHideHandler: ViewHideHandler
    private lateinit var appRouter: AppRouterImpl // Added AppRouter instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize AppRouter
        appRouter = AppRouterImpl(supportFragmentManager, R.id.main_fragment_container)

        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.title = getString(R.string.app_name) // Default title

        mainToolbarHideHandler = ViewHideHandler(
            hideableView = binding.mainToolbarContainer,
            anchoredView = null,
            gravity = Gravity.TOP,
            updateElevation = true,
            shouldAlwaysShow = { false }
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, MainFragment.newInstance())
                .commitNow()
        }
    }

    // Implementation of MainFragment.MainScrollableViewProvider
    override fun updateToolbarState(hostFragment: Fragment?, scrollableView: View?, policy: ToolbarPolicy) {
        L.d("MainActivity: updateToolbarState called. Policy: $policy, HostFragment: ${hostFragment?.javaClass?.simpleName}")

        when (policy) {
            ToolbarPolicy.HIDDEN -> {
                binding.mainToolbarContainer.visibility = View.GONE
                mainToolbarHideHandler.enabled = false
                mainToolbarHideHandler.setScrollableSource(null)
            }
            ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT -> {
                binding.mainToolbarContainer.visibility = View.VISIBLE
                mainToolbarHideHandler.enabled = true
                mainToolbarHideHandler.setScrollableSource(scrollableView)
            }
        }
    }

    // Implementation of SavedPagesFragment.NavigationProvider
    override fun displayPageFragment(pageApiTitle: String?, pageNumericId: String?, source: Int) {
        L.d("MainActivity: displayPageFragment called with apiTitle='$pageApiTitle', numericId='$pageNumericId', source='$source'")
        // Pass the source to the appRouter
        appRouter.navigateToPage(pageId = pageNumericId, pageTitle = pageApiTitle, source = source)
    }

    // TODO: (Review existing TODOs from original file if they are still relevant)
    // 1. Ensure ViewHideHandler.kt and its dependencies (DimenUtil, ViewAnimations) are correctly integrated
    //   (This should be mostly complete, ViewAnimations not used by ViewHideHandler directly now)
    // 2. Review MainFragment.kt: (These points are addressed by the new policy system)
    // 3. Test the toolbar collapse behavior (Now conditional based on policy)
    // 4. Plan the adaptation of ViewHideHandler to support RecyclerView for SearchFragment. (This was done)
}