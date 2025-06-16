package com.omiyawaki.osrswiki

import android.content.Intent
import android.os.Bundle
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.util.log.L

class MainActivity : BaseActivity(), SavedPagesFragment.NavigationProvider {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl

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

        if (savedInstanceState == null) {
            L.d("MainActivity: onCreate: savedInstanceState is null, replacing with MainFragment.")
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fragment_container, MainFragment.newInstance(), MainFragment::class.java.simpleName)
                .commitNow()
            L.d("MainActivity: onCreate: MainFragment committed.")
        } else {
            L.d("MainActivity: onCreate: savedInstanceState is NOT null, fragment should be restored.")
        }

        handleIntentExtras(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleIntentExtras(it) }
    }

    private fun handleIntentExtras(intent: Intent) {
        if (intent.action == ACTION_NAVIGATE_TO_SEARCH) {
            L.d("MainActivity: Received ACTION_NAVIGATE_TO_SEARCH")
            binding.root.post {
                val mainFragment = supportFragmentManager.findFragmentByTag(MainFragment::class.java.simpleName) as? MainFragment
                if (mainFragment != null && mainFragment.isAdded) {
                    mainFragment.navigateToSearchTab()
                } else {
                    L.e("MainActivity: MainFragment not found or not added. Cannot navigate to search tab programmatically.")
                }
            }
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
        if (appRouter.goBack()) {
            return
        }
        super.onBackPressed()
    }
}
