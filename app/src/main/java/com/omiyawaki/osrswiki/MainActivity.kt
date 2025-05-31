package com.omiyawaki.osrswiki

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment // Added for hostFragment parameter
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.page.ViewHideHandler
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.main.MainScrollableViewProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy // Added import for ToolbarPolicy
import com.omiyawaki.osrswiki.util.log.L // Assuming L is your logger, add if not already present

class MainActivity : AppCompatActivity(), MainScrollableViewProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainToolbarHideHandler: ViewHideHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                // Detach any previous scrollable source from the handler as the toolbar is now hidden
                mainToolbarHideHandler.setScrollableSource(null)
                // Ensure translation is reset if it was partially hidden, though GONE should handle this.
                // binding.mainToolbarContainer.translationY = 0f
            }
            ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT -> {
                binding.mainToolbarContainer.visibility = View.VISIBLE
                mainToolbarHideHandler.enabled = true
                mainToolbarHideHandler.setScrollableSource(scrollableView)

                // If the fragment wants a collapsible toolbar but provides no scrollable view,
                // ViewHideHandler's setScrollableSource(null) with enabled=true
                // should call ensureDisplayed(), making the toolbar visible and static.
            }
        }

        // Future: Update toolbar title or menu items based on hostFragment if needed
        // if (hostFragment is SomeFragmentInterface) {
        //     supportActionBar?.title = hostFragment.getCustomTitle()
        // } else {
        //     supportActionBar?.title = getString(R.string.app_name) // Reset to default
        // }
    }

    // TODO: (Review existing TODOs from original file if they are still relevant)
    // 1. Ensure ViewHideHandler.kt and its dependencies (DimenUtil, ViewAnimations) are correctly integrated
    //    (This should be mostly complete, ViewAnimations not used by ViewHideHandler directly now)
    // 2. Review MainFragment.kt: (These points are addressed by the new policy system)
    // 3. Test the toolbar collapse behavior (Now conditional based on policy)
    // 4. Plan the adaptation of ViewHideHandler to support RecyclerView for SearchFragment. (This was done)
}
