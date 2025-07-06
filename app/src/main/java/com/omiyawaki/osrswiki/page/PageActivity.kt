package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.core.view.GravityCompat
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.views.NavMenuTriggerLayout
import com.omiyawaki.osrswiki.views.TabCountsView

class PageActivity : BaseActivity(), NavMenuTriggerLayout.Callback, PageFragment.Callback {

    lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK
    private var currentActionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.title = "Loading..."
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Set this activity as the callback for swipe gestures.
        binding.navMenuTriggerLayout.callback = this

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
        }
        setupToolbarListeners()
    }

    override fun onNavMenuSwipeRequest(gravity: Int) {
        if (gravity == Gravity.END) {
            // A swipe from right-to-left opens the ToC drawer.
            binding.pageDrawerLayout.openDrawer(GravityCompat.END)
        } else if (gravity == Gravity.START) {
            // A swipe from left-to-right triggers the back action.
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun showContents() {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
        fragment?.showContents()
    }

    private fun setupToolbarListeners() {
        val searchContainer = findViewById<MaterialTextView>(R.id.toolbar_search_container)
        searchContainer.setOnClickListener {
            L.d("Search 'button' clicked in PageActivity toolbar.")
            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_NAVIGATE_TO_SEARCH
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainActivityIntent)
        }

        findViewById<TabCountsView>(R.id.toolbar_tab_counts_view).setOnClickListener {
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
        // Let the action mode handle the back press if it's active.
        if (currentActionMode != null) {
            currentActionMode?.finish()
            return true
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onPageStartActionMode(callback: ActionMode.Callback) {
        if (currentActionMode != null) {
            return
        }
        // Per Layout Inspector results, the AppBarLayout's elevation should remain constant
        // so that its shadow is visible behind the ActionMode bar.
        // binding.appBarLayout.elevation = 0f
        currentActionMode = startActionMode(callback)
    }

    override fun onPageStopActionMode() {
        // Per Layout Inspector results, the AppBarLayout's elevation should remain constant.
        // binding.appBarLayout.elevation = 4f * resources.displayMetrics.density
        currentActionMode = null
    }

    override fun onPageFinishActionMode() {
        currentActionMode?.finish()
    }

    companion object {
        fun newIntent(context: Context, updateItem: com.omiyawaki.osrswiki.news.model.UpdateItem, source: Int): Intent {
            val canonicalTitle = getPageTitleFromUrl(updateItem.articleUrl)
            return newIntent(context, canonicalTitle, null, source)
        }

        private fun getPageTitleFromUrl(url: String): String {
            val pathSegment = url.substringAfterLast('/')
            val withSpaces = pathSegment.replace('_', ' ')
            return java.net.URLDecoder.decode(withSpaces, "UTF-8")
        }
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        const val FRAGMENT_TAG = "PageFragmentTag"

        fun newIntent(context: Context, pageTitle: String?, pageId: String?, source: Int): Intent {
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source)
            }
        }
    }
}
