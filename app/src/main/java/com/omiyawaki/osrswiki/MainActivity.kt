package com.omiyawaki.osrswiki

import android.content.Intent
import android.os.Bundle
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityMainBinding
import com.omiyawaki.osrswiki.navigation.AppRouterImpl
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.news.ui.NewsFragment
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.ui.main.MainFragment
import com.omiyawaki.osrswiki.util.log.L

class MainActivity : BaseActivity(),
    SavedPagesFragment.NavigationProvider,
    NewsFragment.OnNewsCardClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appRouter: AppRouterImpl

    companion object {
        const val ACTION_NAVIGATE_TO_SEARCH = "com.omiyawaki.osrswiki.ACTION_NAVIGATE_TO_SEARCH"
        // Define a source constant for analytics or tracking
        private const val NAVIGATION_SOURCE_NEWS_UPDATE = 1
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
            // The MainFragment no longer has tabs. We now launch the SearchFragment directly.
            // This mimics the behavior of tapping the search card in the new MainFragment feed.
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, SearchFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }
    }

    override fun displayPageFragment(pageApiTitle: String?, pageNumericId: String?, source: Int) {
        appRouter.navigateToPage(pageId = pageNumericId, pageTitle = pageApiTitle, source = source)
    }

    override fun onUpdateCardClicked(item: UpdateItem) {
        // The API requires the canonical page title, not the display title from the card.
        // We extract the correct title from the end of the URL provided in the UpdateItem.
        // For example, from ".../w/Update:Summer_Sweep_Up:_Combat" we extract "Update:Summer_Sweep_Up:_Combat".
        val canonicalTitle = item.articleUrl.substringAfterLast('/')

        appRouter.navigateToPage(
            pageId = null, // We don't have a numeric ID for news items
            pageTitle = canonicalTitle,
            source = NAVIGATION_SOURCE_NEWS_UPDATE
        )
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
