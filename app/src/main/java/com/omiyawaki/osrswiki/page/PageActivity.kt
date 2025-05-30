package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.util.log.L

class PageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        pageTitleArg = intent.getStringExtra(EXTRA_PAGE_TITLE)
        pageIdArg = intent.getStringExtra(EXTRA_PAGE_ID)

        // Removed L.i("PageActivity onCreate - Retrieved from Intent: ...")

        supportActionBar?.title = pageTitleArg ?: getString(R.string.app_name)

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(pageId = pageIdArg, pageTitle = pageTitleArg)
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment)
                .commit()
            L.d("PageActivity fragment committed. Initial Title: $pageTitleArg, Initial ID: $pageIdArg")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"

        fun newIntent(context: Context, pageTitle: String?, pageId: String?): Intent {
            // Removed L.i("PageActivity.newIntent called with...")
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
            }
        }
    }
}
