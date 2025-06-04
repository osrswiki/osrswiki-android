package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.MainActivity // Required for navigating to MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.log.L

class PageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK

    // Reference to the PageFragment
    private var pageFragment: PageFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // The actual page title will be displayed within PageFragment or managed differently,
        // not in the global action bar title spot which is now used by search.
        // If a title is needed on the toolbar itself, it needs a dedicated TextView in the layout.

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(
                pageId = pageIdArg,
                pageTitle = pageTitleArg,
                source = navigationSourceArg
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG) // Added a tag
                .commit()
            // Fragment reference will be obtained in onAttachFragment or when needed
            L.d("PageActivity fragment committed. Initial Title: $pageTitleArg, Initial ID: $pageIdArg, Source: $navigationSourceArg")
        } else {
            // Re-obtain fragment reference on configuration change
            pageFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
        }

        setupToolbarListeners()
    }

    override fun onAttachFragment(fragment: androidx.fragment.app.Fragment) {
        super.onAttachFragment(fragment)
        if (fragment.tag == FRAGMENT_TAG && fragment is PageFragment) {
            pageFragment = fragment
            L.d("PageFragment attached to PageActivity.")
        }
    }

    private fun setupToolbarListeners() {
        // Search button listener
        binding.pageToolbarSearchButton.setOnClickListener {
            L.d("Search button clicked in PageActivity toolbar.")
            // TODO: Define ACTION_NAVIGATE_TO_SEARCH in MainActivity's companion object
            // TODO: Handle this action in MainActivity's onCreate or onNewIntent to navigate to SearchFragment
            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                action = "com.omiyawaki.osrswiki.NAVIGATE_TO_SEARCH" // Placeholder for MainActivity.ACTION_NAVIGATE_TO_SEARCH
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainActivityIntent)
            // Consider if PageActivity should finish after navigating to search
            // finish()
        }

        // Tabs view listener
        binding.pageToolbarTabCountsView.setOnClickListener {
            L.d("Tabs view clicked in PageActivity toolbar.")
            // TODO: Implement navigation to Tab Switcher UI
            Toast.makeText(this, "Tab switcher not yet implemented.", Toast.LENGTH_SHORT).show()
        }

        // Overflow menu button listener
        binding.pageToolbarOverflowMenuButton.setOnClickListener { anchorView ->
            L.d("Overflow menu button clicked in PageActivity toolbar.")
            pageFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
            if (pageFragment != null) {
                pageFragment?.showPageOverflowMenu(anchorView)
            } else {
                L.w("PageFragment not found when trying to show overflow menu.")
                Toast.makeText(this, "Error: Could not show menu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // Handle the Up button (back arrow) press
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        private const val FRAGMENT_TAG = "PageFragmentTag" // Tag for retrieving fragment

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
