package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry // Added import
import com.omiyawaki.osrswiki.util.log.L

class PageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK // Default, should be overwritten

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pageTitleArg = intent.getStringExtra(EXTRA_PAGE_TITLE)
        pageIdArg = intent.getStringExtra(EXTRA_PAGE_ID)
        // Retrieve the source, defaulting to SOURCE_INTERNAL_LINK if not provided.
        // Callers using newIntent should always provide a source.
        navigationSourceArg = intent.getIntExtra(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)

        // Removed L.i("PageActivity onCreate - Retrieved from Intent: ...")
        L.d("PageActivity onCreate - Retrieved from Intent: Title: $pageTitleArg, ID: $pageIdArg, Source: $navigationSourceArg")


        supportActionBar?.title = pageTitleArg ?: getString(R.string.app_name)

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(
                pageId = pageIdArg,
                pageTitle = pageTitleArg,
                source = navigationSourceArg // Pass the retrieved source
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment)
                .commit()
            L.d("PageActivity fragment committed. Initial Title: $pageTitleArg, Initial ID: $pageIdArg, Source: $navigationSourceArg")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE" // New extra key

        fun newIntent(context: Context, pageTitle: String?, pageId: String?, source: Int): Intent {
            // Removed L.i("PageActivity.newIntent called with...")
            L.d("PageActivity.newIntent called with Title: $pageTitle, ID: $pageId, Source: $source")
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source) // Add source to intent
            }
        }
    }
}