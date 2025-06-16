package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.log.L

class PageActivity : BaseActivity() {

    private lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK

    private var pageFragment: PageFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayShowTitleEnabled(false)
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
                .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG)
                .commit()
        } else {
            pageFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
        }
        setupToolbarListeners()
    }

    @Deprecated(message = "Override of a deprecated Activity.onAttachFragment(). Consider migrating to FragmentManager.FragmentLifecycleCallbacks.")
    @Suppress("DEPRECATION")
    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment.tag == FRAGMENT_TAG && fragment is PageFragment) {
            pageFragment = fragment
            L.d("PageFragment attached to PageActivity.")
        }
    }

    private fun setupToolbarListeners() {
        // Since this is the PageActivity, the EditText should not be focusable.
        // Clicking it should navigate to the search screen.
        val searchEditText = findViewById<android.widget.EditText>(R.id.toolbar_search_edit_text)
        searchEditText.isFocusable = false
        searchEditText.isFocusableInTouchMode = false
        searchEditText.setOnClickListener {
            L.d("Search 'button' clicked in PageActivity toolbar.")
            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_NAVIGATE_TO_SEARCH
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainActivityIntent)
        }

        findViewById<com.omiyawaki.osrswiki.views.TabCountsView>(R.id.toolbar_tab_counts_view).setOnClickListener {
            L.d("Tabs view clicked in PageActivity toolbar.")
            Toast.makeText(this, "Tab switcher not yet implemented.", Toast.LENGTH_SHORT).show()
        }

        findViewById<android.widget.ImageView>(R.id.toolbar_overflow_menu_button).setOnClickListener { anchorView ->
            L.d("Overflow menu button clicked in PageActivity toolbar.")
            val currentFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
            currentFragment?.showPageOverflowMenu(anchorView) ?: run {
                L.e("PageFragment not found when trying to show overflow menu.")
                Toast.makeText(this, "Error: Could not show menu.", Toast.LENGTH_SHORT).show()
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
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source)
            }
        }
    }
}
