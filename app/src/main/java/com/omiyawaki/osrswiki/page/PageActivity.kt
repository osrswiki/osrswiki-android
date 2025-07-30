package com.omiyawaki.osrswiki.page

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.ActionMode
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textview.MaterialTextView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.ActivityPageBinding
import com.omiyawaki.osrswiki.dataclient.WikiSite
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.views.ObservableWebView
import com.omiyawaki.osrswiki.views.ModernToolbarController
import com.omiyawaki.osrswiki.views.GpuAcceleratedToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageActivity : BaseActivity(), PageFragment.Callback {

    internal lateinit var binding: ActivityPageBinding
    private var pageTitleArg: String? = null
    private var pageIdArg: String? = null
    private var navigationSourceArg: Int = HistoryEntry.SOURCE_INTERNAL_LINK
    private var snippetArg: String? = null
    private var thumbnailUrlArg: String? = null
    private var currentActionMode: ActionMode? = null

    private lateinit var modernToolbarController: ModernToolbarController
    private lateinit var pageActionBarManager: PageActionBarManager
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceRecognitionManager.handleActivityResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.pageToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize the modern GPU-accelerated toolbar system
        modernToolbarController = ModernToolbarController(
            toolbarContainer = binding.pageToolbarContainer,
            contentView = binding.pageFragmentContainer,
            shadowView = null // GpuAcceleratedToolbar handles its own shadows
        )

        pageTitleArg = intent.getStringExtra(EXTRA_PAGE_TITLE)
        pageIdArg = intent.getStringExtra(EXTRA_PAGE_ID)
        navigationSourceArg = intent.getIntExtra(EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_INTERNAL_LINK)
        snippetArg = intent.getStringExtra(EXTRA_PAGE_SNIPPET)
        thumbnailUrlArg = intent.getStringExtra(EXTRA_PAGE_THUMBNAIL)

        if (savedInstanceState == null) {
            val fragment = PageFragment.newInstance(
                pageId = pageIdArg, 
                pageTitle = pageTitleArg, 
                source = navigationSourceArg,
                snippet = snippetArg,
                thumbnailUrl = thumbnailUrlArg
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.page_fragment_container, fragment, FRAGMENT_TAG)
                .commit()
        }
        setupToolbarListeners()
        checkAndShowOfflineBanner()
    }

    override fun onWebViewReady(webView: ObservableWebView) {
        modernToolbarController.attachToScrollView(webView)
    }

    override fun onPageSwipe(gravity: Int) {
        val direction = if (gravity == Gravity.START) "START (back)" else if (gravity == Gravity.END) "END (ToC)" else "UNKNOWN($gravity)"
        L.d("PageActivity: Received swipe, direction=$direction")
        
        if (gravity == Gravity.END) {
            // A swipe from right-to-left opens the ToC drawer.
            L.d("PageActivity: Opening ToC drawer")
            binding.pageDrawerLayout.openDrawer(GravityCompat.END)
        } else if (gravity == Gravity.START) {
            // A swipe from left-to-right triggers the back action.
            L.d("PageActivity: Triggering back action")
            onBackPressedDispatcher.onBackPressed()
        }
    }

    fun showContents() {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as? PageFragment
        fragment?.showContents()
    }

    private fun setupToolbarListeners() {
        // Initialize voice recognition manager
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                // Open search activity with the voice query
                val intent = Intent(this, SearchActivity::class.java).apply {
                    putExtra("query", query)
                }
                startActivity(intent)
            }
        )
        
        val searchContainer = binding.pageToolbar.findViewById<MaterialTextView>(R.id.toolbar_search_container)
        searchContainer.setOnClickListener {
            val searchActivityIntent = Intent(this, SearchActivity::class.java)
            startActivity(searchActivityIntent)
        }
        
        // Set up voice search button
        binding.pageToolbar.findViewById<ImageView>(R.id.toolbar_voice_search_button)?.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition(voiceSearchLauncher)
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
        modernToolbarController.expandToolbar(animated = true)
        currentActionMode = startActionMode(callback)
    }

    override fun onPageStopActionMode() {
        currentActionMode = null
    }

    override fun onPageFinishActionMode() {
        currentActionMode?.finish()
    }

    override fun getPageToolbarContainer(): View = binding.pageToolbarContainer

    override fun getPageActionBarManager(): PageActionBarManager {
        if (!::pageActionBarManager.isInitialized) {
            pageActionBarManager = PageActionBarManager(binding)
        }
        return pageActionBarManager
    }

    private fun checkAndShowOfflineBanner() {
        val pageTitle = pageTitleArg
        if (pageTitle.isNullOrBlank()) {
            return
        }

        lifecycleScope.launch {
            val isOfflineMode = withContext(Dispatchers.IO) {
                // Check if we have no network connection
                val hasNetwork = hasNetworkConnection()
                if (hasNetwork) {
                    false // We have network, not in offline mode
                } else {
                    // No network, check if this page is saved offline
                    isPageSavedOffline(pageTitle)
                }
            }

            // Show offline banner if in offline mode
            binding.pageOfflineBanner.visibility = if (isOfflineMode) View.VISIBLE else View.GONE
        }
    }

    private fun hasNetworkConnection(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun isPageSavedOffline(pageTitle: String): Boolean {
        return try {
            val readingListPageDao = AppDatabase.instance.readingListPageDao()
            val wikiSite = WikiSite.OSRS_WIKI
            val namespace = Namespace.MAIN
            
            val savedPage = readingListPageDao.findPageInAnyList(
                wiki = wikiSite,
                lang = wikiSite.languageCode,
                ns = namespace,
                apiTitle = pageTitle
            )
            
            savedPage?.offline == true && savedPage.status == ReadingListPage.STATUS_SAVED
        } catch (e: Exception) {
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults, voiceSearchLauncher)
        }
    }

    companion object {
        const val EXTRA_PAGE_TITLE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_TITLE"
        const val EXTRA_PAGE_ID = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_ID"
        const val EXTRA_PAGE_SOURCE = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SOURCE"
        const val EXTRA_PAGE_SNIPPET = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_SNIPPET"
        const val EXTRA_PAGE_THUMBNAIL = "com.omiyawaki.osrswiki.page.EXTRA_PAGE_THUMBNAIL"
        const val FRAGMENT_TAG = "PageFragmentTag"

        fun newIntent(context: Context, updateItem: com.omiyawaki.osrswiki.news.model.UpdateItem, source: Int): Intent {
            val canonicalTitle = getPageTitleFromUrl(updateItem.articleUrl)
            return newIntent(
                context = context,
                pageTitle = canonicalTitle, 
                pageId = null, 
                source = source,
                snippet = updateItem.snippet,
                thumbnailUrl = updateItem.imageUrl
            )
        }

        private fun getPageTitleFromUrl(url: String): String {
            val pathSegment = url.substringAfterLast('/')
            val withSpaces = pathSegment.replace('_', ' ')
            return java.net.URLDecoder.decode(withSpaces, "UTF-8")
        }

        fun newIntent(
            context: Context, 
            pageTitle: String?, 
            pageId: String?, 
            source: Int,
            snippet: String? = null,
            thumbnailUrl: String? = null
        ): Intent {
            return Intent(context, PageActivity::class.java).apply {
                putExtra(EXTRA_PAGE_TITLE, pageTitle)
                putExtra(EXTRA_PAGE_ID, pageId)
                putExtra(EXTRA_PAGE_SOURCE, source)
                putExtra(EXTRA_PAGE_SNIPPET, snippet)
                putExtra(EXTRA_PAGE_THUMBNAIL, thumbnailUrl)
            }
        }
    }
}
