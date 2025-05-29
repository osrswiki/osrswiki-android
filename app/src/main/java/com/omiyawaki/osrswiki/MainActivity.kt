package com.omiyawaki.osrswiki // TODO: Verify this is your correct base package for MainActivity

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.page.ViewHideHandler // TODO: Ensure this import is correct if ViewHideHandler is in a different package
import com.omiyawaki.osrswiki.ui.main.MainFragment // TODO: Ensure this import is correct
import com.omiyawaki.osrswiki.ui.main.MainScrollableViewProvider // Import the interface

class MainActivity : AppCompatActivity(), MainScrollableViewProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainToolbarHideHandler: ViewHideHandler
    // TODO: Ensure ViewAnimations.ensureTranslationY is accessible, e.g., via:
    // import com.omiyawaki.osrswiki.util.ViewAnimations or com.omiyawaki.osrswiki.views.ViewAnimations

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.title = getString(R.string.app_name)

        mainToolbarHideHandler = ViewHideHandler(
            hideableView = binding.mainToolbarContainer,
            anchoredView = null,
            gravity = Gravity.TOP,
            updateElevation = true,
            shouldAlwaysShow = { false } // e.g., return true if a search bar in toolbar is focused
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, MainFragment.newInstance())
                .commitNow()
        }
    }

    // Implementation of MainFragment.MainScrollableViewProvider
    override fun updateScrollViewForToolbarHandler(scrollableView: View?) {
        // Ensure ViewHideHandler's setScrollableSource method is correctly called.
        // This method should now exist in your adapted ViewHideHandler.kt
        mainToolbarHideHandler.setScrollableSource(scrollableView)

        if (scrollableView == null) {
            // If no scrollable view is provided (e.g., current fragment doesn't have one,
            // or MainFragment is being detached), ensure the toolbar is displayed.
            mainToolbarHideHandler.ensureDisplayed()
        }
        // Optional: Log when the scrollable source changes
        // Log.d("MainActivity", "Scrollable source for toolbar hide handler updated: ${scrollableView?.javaClass?.simpleName ?: "null"}")
    }

    // TODO:
    // 1. Ensure ViewHideHandler.kt and its dependencies (DimenUtil, ViewAnimations) are correctly integrated
    //    in your project and their imports are correct.
    // 2. Review MainFragment.kt:
    //    - Ensure PlaceholderFragment (or your actual fragments like SearchFragment) uses NestedScrollView
    //      and correctly implements ScrollableContent to provide this NestedScrollView.
    //    - Ensure MainFragment.notifyMainActivityOfScrollableView() correctly calls this
    //      updateScrollViewForToolbarHandler method.
    // 3. Test the toolbar collapse behavior with the PlaceholderFragment.
    // 4. Plan the adaptation of ViewHideHandler to support RecyclerView for SearchFragment.
}
