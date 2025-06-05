package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
// import androidx.appcompat.app.AppCompatActivity // Replaced by BaseActivity
import androidx.fragment.app.Fragment // Fully qualify Fragment for the deprecated method
import com.omiyawaki.osrswiki.MainActivity // Required for navigating to MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity // Added import
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.log.L

class PageActivity : BaseActivity() { // Changed to BaseActivity

    private lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK

    // Reference to the PageFragment
    private var pageFragment: PageFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // BaseActivity will call setTheme before this
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the MaterialToolbar as the ActionBar
        setSupportActionBar(binding.pageToolbar)
        // Remove default title to make space for custom layout elements (search, tabs, overflow)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayShowTitleEnabled(false) // Alternative way to hide title
        // Enable the Up button (back arrow)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pageTitleArg = intent.getStringExtra(EXTRA_PAGE_TITLE)
        pageIdArg = intent.getStringExtra(EXTRA_PAGE_ID)
        navigationSourceArg = intent.getIntExtra(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)

        L.d("PageActivity onCreate - Retrieved from Intent: Title: $pageTitleArg, ID: $pageIdArg, Source: $navigationSourceArg")

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(
                pageId = pageIdArg,
                pageTitle = pageTitleArg,
                source = navigationSourceArg
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG) // Added a tag
                .commit()
            L.d("PageActivity fragment committed. Initial Title: $pageTitleArg, Initial ID: $pageIdArg, Source: $navigationSourceArg")
        } else {
            pageFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
        }

        setupToolbarListeners()
    }

    @Deprecated(message = "Override of a deprecated Activity.onAttachFragment(). Consider migrating to FragmentManager.FragmentLifecycleCallbacks.")
    @Suppress("DEPRECATION") // Suppress the warning for overriding a deprecated method
    override fun onAttachFragment(fragment: Fragment) { // Ensure androidx.fragment.app.Fragment is used
        super.onAttachFragment(fragment)
        if (fragment.tag == FRAGMENT_TAG && fragment is PageFragment) {
            pageFragment = fragment
            L.d("PageFragment attached to PageActivity.")
        }
    }

    private fun setupToolbarListeners() {
        binding.pageToolbarSearchButton.setOnClickListener {
            L.d("Search button clicked in PageActivity toolbar.")
            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_NAVIGATE_TO_SEARCH
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainActivityIntent)
        }

        binding.pageToolbarTabCountsView.setOnClickListener {
            L.d("Tabs view clicked in PageActivity toolbar.")
            Toast.makeText(this, "Tab switcher not yet implemented.", Toast.LENGTH_SHORT).show()
        }

        binding.pageToolbarOverflowMenuButton.setOnClickListener { anchorView ->
            L.d("Overflow menu button clicked in PageActivity toolbar.")
            // Ensure pageFragment is up-to-date, especially if obtained initially in onAttachFragment
            // and not guaranteed to be the same instance across activity recreation if not handled.
            // However, findFragmentByTag is safer if there's a possibility of fragment recreation.
            val currentFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
            if (currentFragment is PageFragment) {
                this.pageFragment = currentFragment // Update our reference
                currentFragment.showPageOverflowMenu(anchorView)
            } else {
                // Attempt to use the potentially stale pageFragment reference if currentFragment is not found or wrong type
                // This part might need review based on how robust fragment instance retention is.
                if (pageFragment != null) {
                    L.w("PageFragment found via instance variable after findFragmentByTag failed or returned wrong type. Using potentially stale reference.")
                    pageFragment?.showPageOverflowMenu(anchorView)
                } else {
                    L.e("PageFragment not found when trying to show overflow menu via findFragmentByTag or instance variable.")
                    Toast.makeText(this, "Error: Could not show menu. Fragment not available.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        private const val FRAGMENT_TAG = "PageFragmentTag"

        fun newIntent(context: Context, pageTitle: String?, pageId: String?, source: Int): Intent {
            L.d("PageActivity.newIntent called with Title: $pageTitle, ID: $pageId, Source: $source")
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source)
            }
        }
    }
}