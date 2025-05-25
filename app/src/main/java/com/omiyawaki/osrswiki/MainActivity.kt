package com.omiyawaki.osrswiki

import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.MaterialToolbar
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.navigation.Router
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var appRouter: Router

    /**
     * Listener for back stack changes to update the toolbar.
     * This ensures the toolbar reflects the state of the currently visible fragment.
     */
    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_main)
        if (currentFragment is ScreenConfiguration) {
            updateToolbar(currentFragment)
        } else {
            // Set a default toolbar state if the fragment doesn't provide configuration
            // or if no fragment is currently in the container (e.g., after all pops).
            toolbar.title = getString(R.string.app_name) // Assumes R.string.app_name exists
            toolbar.navigationIcon = null
            invalidateOptionsMenu() // Clear any menu from a previous fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.main_app_toolbar)
        setSupportActionBar(toolbar)

        // Instantiate the application's router implementation.
        appRouter = AppRouterImpl(supportFragmentManager, R.id.fragment_container_main)

        // Register the listener for back stack changes.
        supportFragmentManager.addOnBackStackChangedListener(backStackListener)

        if (savedInstanceState == null) {
            // This is the first creation of the Activity.
            // Navigate to the initial screen.
            // Replace 'navigateToInitialScreen()' with the actual method in the Router interface,
            // e.g., 'navigateToSearchScreen()' or 'navigateToHomeFragment()'.
            appRouter.navigateToSearchScreen() // Ensure this method exists in Router/AppRouterImpl
        } else {
            // Activity is being recreated, FragmentManager will restore the back stack.
            // The listener should handle the toolbar update.
            // Call listener once to ensure toolbar is updated for the restored top fragment.
            backStackListener.onBackStackChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up by removing the listener.
        supportFragmentManager.removeOnBackStackChangedListener(backStackListener)
    }

    /**
     * Updates the toolbar's appearance based on the configuration provided by a Fragment.
     * @param config The ScreenConfiguration provided by the current fragment.
     */
    fun updateToolbar(config: ScreenConfiguration) {
        toolbar.title = config.getToolbarTitle(::getString)

        when (config.getNavigationIconType()) {
            NavigationIconType.NONE -> {
                toolbar.navigationIcon = null
            }
            NavigationIconType.BACK -> {
                // Ensure R.drawable.ic_arrow_back and R.string.nav_back_content_description exist.
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
                toolbar.setNavigationContentDescription(R.string.nav_back_content_description)
            }
            NavigationIconType.CLOSE -> {
                // Ensure R.drawable.ic_close and R.string.nav_close_content_description exist.
                toolbar.setNavigationIcon(R.drawable.ic_close)
                toolbar.setNavigationContentDescription(R.string.nav_close_content_description)
            }
            NavigationIconType.MENU -> {
                /* TODO: Set a menu icon (e.g., R.drawable.ic_menu) and content description */
                /* For now, behaves like NONE (no icon) */
                toolbar.navigationIcon = null
                toolbar.setNavigationContentDescription(null) /* Or R.string.nav_menu_content_description if defined */
            }
        }
        // Invalidate the options menu to trigger onPrepareOptionsMenu for the current fragment,
        // allowing it to update menu items.
        invalidateOptionsMenu()
    }

    /**
     * Allows fragments to dynamically update the toolbar title.
     * @param title The new title for the toolbar.
     */
    fun updateToolbarTitle(title: String?) {
        toolbar.title = title
    }

    /**
     * Handles the action when the toolbar's navigation icon (e.g., up arrow) is pressed.
     * Delegates the back navigation to the AppRouter.
     */
    override fun onSupportNavigateUp(): Boolean {
        // Attempt to navigate back using the router.
        // If the router handles it (e.g., pops a fragment), return true.
        // Otherwise, fall back to default behavior (which might finish the activity).
        return appRouter.goBack() || super.onSupportNavigateUp()
    }

    // Optional: If Fragments need direct access to the router instance.
    // Consider providing it via ViewModel or other dependency injection mechanisms
    // for better separation of concerns.
    // fun getAppRouter(): Router = appRouter
    fun getRouter(): Router = appRouter
}
