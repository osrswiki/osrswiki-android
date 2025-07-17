package com.omiyawaki.osrswiki

import android.content.Intent
import android.os.Bundle
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.ui.map.MapFragment
import com.omiyawaki.osrswiki.util.log.L

class MainActivity : BaseActivity(),
    SavedPagesFragment.NavigationProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl
    private val mainFragment: MainFragment by lazy { MainFragment.newInstance() }
    private val mapFragment: MapFragment by lazy { MapFragment() }
    private lateinit var activeFragment: Fragment

    companion object {
        const val ACTION_NAVIGATE_TO_SEARCH = "com.omiyawaki.osrswiki.ACTION_NAVIGATE_TO_SEARCH"
        private const val MAIN_FRAGMENT_TAG = "main_fragment"
        private const val MAP_FRAGMENT_TAG = "map_fragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        L.d("MainActivity: onCreate: ContentView set.")

        appRouter = AppRouterImpl(supportFragmentManager, R.id.nav_host_container)
        L.d("MainActivity: onCreate: AppRouter initialized.")

        if (savedInstanceState == null) {
            L.d("MainActivity: onCreate: savedInstanceState is null, setting up initial fragments.")
            supportFragmentManager.beginTransaction()
                .add(R.id.nav_host_container, mapFragment, MAP_FRAGMENT_TAG)
                .add(R.id.nav_host_container, mainFragment, MAIN_FRAGMENT_TAG)
                .commit()

            activeFragment = mainFragment

            mapFragment.view?.doOnPreDraw { it.alpha = 0.0f }
            L.d("MainActivity: onCreate: Fragments added. MapFragment is transparent.")
        } else {
            L.d("MainActivity: onCreate: Restoring state.")
            val restoredMain = supportFragmentManager.findFragmentByTag(MAIN_FRAGMENT_TAG)!!
            activeFragment = if (restoredMain.view?.alpha == 1.0f) restoredMain else supportFragmentManager.findFragmentByTag(MAP_FRAGMENT_TAG)!!
            L.d("MainActivity: onCreate: Active fragment is ${activeFragment.javaClass.simpleName}")
        }

        setupBottomNav()
        handleIntentExtras(intent)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_news, R.id.nav_saved -> mainFragment
                R.id.nav_map -> mapFragment
                else -> null
            }

            if (selectedFragment != null && selectedFragment !== activeFragment) {
                L.d("MainActivity: Switching from ${activeFragment.javaClass.simpleName} to ${selectedFragment.javaClass.simpleName}")

                val newActiveFragment = selectedFragment
                val oldActiveFragment = activeFragment

                newActiveFragment.view?.alpha = 1.0f
                oldActiveFragment.view?.alpha = 0.0f

                newActiveFragment.view?.bringToFront()

                activeFragment = newActiveFragment
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

    override fun displayPageFragment(pageApiTitle: String?, pageNumericId: String?, source: Int) {
        appRouter.navigateToPage(pageId = pageNumericId, pageTitle = pageApiTitle, source = source)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (appRouter.goBack()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    @Deprecated(message = "Override of a deprecated Activity.onBackPressed(). Consider migrating to OnBackPressedDispatcher.")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!appRouter.goBack()) {
            super.onBackPressed()
        }
    }
}
