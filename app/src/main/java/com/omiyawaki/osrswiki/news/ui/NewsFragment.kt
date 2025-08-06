package com.omiyawaki.osrswiki.news.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.news.viewmodel.NewsViewModel
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.random.RandomPageRepository
import com.omiyawaki.osrswiki.search.SearchActivity
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.VoiceSearchAnimationHelper
import com.omiyawaki.osrswiki.util.createVoiceSearchAnimationHelper
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyRubikUIHint
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * A fragment to display a feed of recent news and updates from the OSRS Wiki.
 * This UI is modeled after the Wikipedia app's "Explore" feed, presenting
 * different types of content in a card-based RecyclerView.
 */
class NewsFragment : Fragment(), ThemeAware {

    private val viewModel: NewsViewModel by viewModels()
    private lateinit var newsFeedAdapter: NewsFeedAdapter
    private val wikiBaseUrl = "https://oldschool.runescape.wiki"
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private lateinit var voiceAnimationHelper: VoiceSearchAnimationHelper
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceRecognitionManager.handleActivityResult(result.resultCode, result.data)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // This function should only inflate and return the view.
        return inflater.inflate(R.layout.fragment_news, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader(view)
        setupSearch(view)
        setupFonts(view)
        setupRecyclerView(view)
        observeViewModel()

        // Diagnostic logging for header position
        view.findViewById<TextView>(R.id.page_title)?.doOnLayout {
            val location = IntArray(2)
            view.findViewById<TextView>(R.id.page_title)?.getLocationOnScreen(location)
            L.d("HeaderPosition: News title Y-coordinate: ${location[1]}")
        }

        // Initial data fetch
        if (savedInstanceState == null) {
            viewModel.fetchNews()
        }
    }

    private fun setupHeader(view: View) {
        // Set the page title to "Home"
        view.findViewById<TextView>(R.id.page_title)?.text = getString(R.string.nav_news)
        
        // Show and setup the random page button
        val randomPageButton = view.findViewById<ImageButton>(R.id.random_page_button)
        randomPageButton?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                handleRandomPageClick()
            }
        }
    }
    
    private fun setupFonts(view: View) {
        L.d("NewsFragment: Setting up fonts...")
        
        // Apply fonts to header elements
        view.findViewById<TextView>(R.id.page_title)?.applyAlegreyaHeadline()
        
        L.d("NewsFragment: Fonts applied to header elements")
    }

    private fun setupSearch(view: View) {
        val voiceSearchButton = view.findViewById<ImageView>(R.id.voice_search_button)
        
        // Initialize voice animation helper
        if (voiceSearchButton != null) {
            voiceAnimationHelper = voiceSearchButton.createVoiceSearchAnimationHelper()
        }
        
        // Initialize voice recognition manager with fallback support
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                // Open search activity with the voice query
                val intent = Intent(requireContext(), SearchActivity::class.java).apply {
                    putExtra("query", query)
                }
                startActivity(intent)
            },
            onPartialResult = { partialQuery ->
                // Could show a temporary overlay or hint with partial results
                // For now, we'll just log it since we navigate to SearchActivity on final result
                L.d("NewsFragment: Partial result: $partialQuery")
            },
            onStateChanged = { state ->
                // Update UI based on speech recognition state
                if (::voiceAnimationHelper.isInitialized) {
                    when (state) {
                        SpeechRecognitionManager.SpeechState.IDLE -> {
                            voiceAnimationHelper.setIdleState()
                        }
                        SpeechRecognitionManager.SpeechState.LISTENING -> {
                            voiceAnimationHelper.setListeningState()
                        }
                        SpeechRecognitionManager.SpeechState.PROCESSING -> {
                            voiceAnimationHelper.setProcessingState()
                        }
                        SpeechRecognitionManager.SpeechState.ERROR -> {
                            voiceAnimationHelper.setErrorState()
                        }
                    }
                }
            },
            fallbackLauncher = voiceSearchLauncher
        )
        
        // Set a click listener on the search bar view to launch the search activity.
        view.findViewById<View>(R.id.search_container).setOnClickListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }
        
        // Set up voice search button
        voiceSearchButton?.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition()
        }
    }

    private fun handleLinkClick(url: String) {
        L.d("Link clicked. Original URL: $url")

        // Defensively handle relative URLs by prepending the base URL if needed.
        val absoluteUrl = if (url.startsWith("/")) {
            "$wikiBaseUrl$url"
        } else {
            url
        }

        if (absoluteUrl.startsWith(wikiBaseUrl)) {
            // Internal link, open in PageActivity
            try {
                val title = getPageTitleFromUrl(absoluteUrl)
                startActivity(
                    PageActivity.newIntent(requireContext(), title, null, HistoryEntry.SOURCE_NEWS)
                )
            } catch (e: Exception) {
                L.e("Could not parse page title from internal URL: $absoluteUrl", e)
                Toast.makeText(requireContext(), "Error opening internal link.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // External link, open in browser
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(absoluteUrl))
                startActivity(intent)
            } catch (e: Exception) {
                L.e("Failed to open external link: $absoluteUrl", e)
                Toast.makeText(requireContext(), "Could not open external link.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getPageTitleFromUrl(url: String): String {
        val pathSegment = url.substringAfterLast('/')
        val withSpaces = pathSegment.replace('_', ' ')
        return URLDecoder.decode(withSpaces, "UTF-8")
    }

    private fun setupRecyclerView(view: View) {
        newsFeedAdapter = NewsFeedAdapter(
            onUpdateItemClicked = { updateItem ->
                L.d("NewsFragment: UpdateItem clicked!")
                L.d("  updateItem.title: '${updateItem.title}'")
                L.d("  updateItem.articleUrl: '${updateItem.articleUrl}'")
                L.d("  updateItem.snippet: '${updateItem.snippet}'")
                L.d("  updateItem.imageUrl: '${updateItem.imageUrl}'")
                L.d("NewsFragment: About to call PageActivity.newIntent()")
                
                try {
                    val intent = PageActivity.newIntent(requireContext(), updateItem, HistoryEntry.SOURCE_NEWS)
                    L.d("NewsFragment: PageActivity intent created successfully")
                    L.d("NewsFragment: Starting PageActivity...")
                    startActivity(intent)
                    L.d("NewsFragment: PageActivity started successfully")
                } catch (e: Exception) {
                    L.e("NewsFragment: Exception when starting PageActivity", e)
                }
            },
            onLinkClicked = { url ->
                handleLinkClick(url)
            }
        )

        view.findViewById<RecyclerView>(R.id.recyclerViewNews).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsFeedAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.feedItems.observe(viewLifecycleOwner) { feedItems ->
            // Pass the fetched data to the adapter.
            newsFeedAdapter.setItems(feedItems)
        }
    }

    private fun handleRandomPageClick() {
        L.d("NewsFragment: Random page button clicked")
        
        lifecycleScope.launch {
            try {
                val result = RandomPageRepository.getRandomPage()
                
                result.onSuccess { pageTitle ->
                    L.d("NewsFragment: Random page fetched successfully: $pageTitle")
                    
                    val intent = PageActivity.newIntent(
                        context = requireContext(),
                        pageTitle = pageTitle,
                        pageId = null,
                        source = HistoryEntry.SOURCE_RANDOM
                    )
                    startActivity(intent)
                }.onFailure { exception ->
                    L.e("NewsFragment: Failed to fetch random page", exception)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.random_page_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                L.e("NewsFragment: Exception in handleRandomPageClick", e)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.random_page_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.destroy()
        }
        if (::voiceAnimationHelper.isInitialized) {
            voiceAnimationHelper.cleanup()
        }
    }

    override fun onThemeChanged() {
        L.d("NewsFragment: onThemeChanged called")
        // Re-apply theme attributes to views that use theme attributes
        refreshThemeAttributes()
    }

    private fun refreshThemeAttributes() {
        view?.let { rootView ->
            // Get the current theme's paper_color attribute
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme
            theme.resolveAttribute(com.omiyawaki.osrswiki.R.attr.paper_color, typedValue, true)
            
            // Apply the new background color to the AppBarLayout
            val appBarLayout = rootView.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.news_app_bar)
            appBarLayout?.setBackgroundColor(typedValue.data)
            
            L.d("NewsFragment: Theme attributes refreshed")
        }
    }
}
