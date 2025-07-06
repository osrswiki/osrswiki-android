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
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.views.ObservableWebView
import com.omiyawaki.osrswiki.views.TabCountsView
import com.omiyawaki.osrswiki.views.ViewHideHandler

class PageActivity : BaseActivity(), PageFragment.Callback {

    internal lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK
    private var currentActionMode: ActionMode? = null

    private lateinit var toolbarHideHandler: ViewHideHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.title = "Loading..."
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Forcefully set the elevation and disable the StateListAnimator to
        // prevent the theme from overwriting the elevation value.
        binding.pageAppbarLayout.stateListAnimator = null
        val elevationInDp = 9.75f
        binding.pageAppbarLayout.elevation = elevationInDp * resources.displayMetrics.density

        // Initialize the ViewHideHandler, targeting the AppBarLayout
        toolbarHideHandler = ViewHideHandler(binding.pageAppbarLayout)

        pageTitleArg = intent.getStringExtra(EXTRA_PAGE_TITLE)
        pageIdArg = intent.getStringExtra(EXTRA_PAGE_ID)
        navigationSourceArg = intent.getIntExtra(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(pageId = pageIdArg, pageTitle = pageTitleArg, source = navigationSourceArg)
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG)
                .commit()
        }
        setupToolbarListeners()
    }

    override fun onWebViewReady(webView: ObservableWebView) {
        toolbarHideHandler.setScrollView(webView)
    }

    override fun onPageSwipe(gravity: Int) {
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
        val searchContainer = binding.pageToolbar.findViewById<MaterialTextView>(R.id.toolbar_search_container)
        searchContainer.setOnClickListener {
            val searchActivityIntent = Intent(this, SearchActivity::class.java)
            startActivity(searchActivityIntent)
        }

        binding.pageToolbar.findViewById<TabCountsView>(R.id.toolbar_tab_counts_view).setOnClickListener {
            Toast.makeText(this, "Tab switcher not yet implemented.", Toast.LENGTH_SHORT).show()
        }

        binding.pageToolbar.findViewById<View>(R.id.toolbar_overflow_menu_button).setOnClickListener { anchorView ->
            val currentFragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
            currentFragment?.showPageOverflowMenu(anchorView) ?: run {
                Toast.makeText(this, "Error: Could not show menu.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (currentActionMode != null) {
            currentActionMode?.finish()
            return true
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onPageStartActionMode(callback: ActionMode.Callback) {
        if (currentActionMode != null) { return }
        binding.pageAppbarLayout.setExpanded(true, true)
        currentActionMode = startActionMode(callback)
    }

    override fun onPageStopActionMode() {
        currentActionMode = null
    }

    override fun onPageFinishActionMode() {
        currentActionMode?.finish()
    }

    override fun getPageActionTabLayout(): PageActionTabLayout = binding.pageActionsTabLayout

    override fun getPageToolbarContainer(): View = binding.pageAppbarLayout

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        const val FRAGMENT_TAG = "PageFragmentTag"

        fun newIntent(context: Context, updateItem: com.omiyawaki.osrswiki.news.model.UpdateItem, source: Int): Intent {
            val canonicalTitle = getPageTitleFromUrl(updateItem.articleUrl)
            return newIntent(context, canonicalTitle, null, source)
        }

        private fun getPageTitleFromUrl(url: String): String {
            val pathSegment = url.substringAfterLast('/')
            val withSpaces = pathSegment.replace('_', ' ')
            return java.net.URLDecoder.decode(withSpaces, "UTF-8")
        }

        fun newIntent(context: Context, pageTitle: String?, pageId: String?, source: Int): Intent {
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source)
            }
        }
    }
}
