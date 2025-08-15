package com.omiyawaki.osrswiki.readinglist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.activity.BaseActivity
import com.omiyawaki.osrswiki.database.AppDatabase
import com.omiyawaki.osrswiki.databinding.ActivitySavedPagesSearchBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.readinglist.adapter.SavedPagesAdapter
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModel
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModelFactory
import com.omiyawaki.osrswiki.util.SpeechRecognitionManager
import com.omiyawaki.osrswiki.util.createVoiceRecognitionManager
import com.omiyawaki.osrswiki.util.FontUtil
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch

class SavedPagesSearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySavedPagesSearchBinding
    
    private val viewModel: SavedPagesViewModel by viewModels {
        val readingListPageDao = AppDatabase.instance.readingListPageDao()
        val repository = SavedPagesRepository(readingListPageDao)
        SavedPagesViewModelFactory(repository)
    }
    
    private lateinit var searchAdapter: SavedPagesAdapter
    
    private lateinit var voiceRecognitionManager: SpeechRecognitionManager
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        voiceRecognitionManager.handleActivityResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedPagesSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFonts()
        setupSearchField()
        setupVoiceSearch()
        observeSearchResults()
        
        // Handle voice search query if provided
        val voiceQuery = intent.getStringExtra("query")
        if (!voiceQuery.isNullOrBlank()) {
            binding.searchEditText.setText(voiceQuery)
            binding.searchEditText.setSelection(voiceQuery.length)
        }
        
        // Set focus to the search field
        binding.searchEditText.requestFocus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.searchToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        binding.searchToolbar.setNavigationOnClickListener { finishAfterTransition() }
    }

    private fun setupRecyclerView() {
        searchAdapter = SavedPagesAdapter { readingListPage ->
            navigateToPage(readingListPage)
        }
        
        binding.searchRecyclerView.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(this@SavedPagesSearchActivity)
        }
    }

    private fun setupFonts() {
        L.d("SavedPagesSearchActivity: Setting up fonts...")
        
        // Search input field will use system font
        
        L.d("SavedPagesSearchActivity: Fonts applied to UI elements")
    }
    
    private fun setupSearchField() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                Log.d(TAG, "setupSearchField: Text changed to '$query'")
                if (query.isNotEmpty()) {
                    Log.d(TAG, "setupSearchField: Triggering search for '$query'")
                    viewModel.searchSavedPages(query)
                } else {
                    Log.d(TAG, "setupSearchField: Clearing search results")
                    viewModel.clearSearchResults()
                }
            }
        })
    }

    private fun setupVoiceSearch() {
        // Initialize voice recognition manager
        voiceRecognitionManager = createVoiceRecognitionManager(
            onResult = { query ->
                binding.searchEditText.setText(query)
                binding.searchEditText.setSelection(query.length)
            }
        )
        
        // Set up voice search button
        binding.voiceSearchButton.setOnClickListener {
            voiceRecognitionManager.startVoiceRecognition()
        }
    }

    private fun observeSearchResults() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults.collect { results ->
                        Log.d(TAG, "observeSearchResults: Received ${results.size} search results")
                        results.forEachIndexed { index, page ->
                            Log.d(TAG, "observeSearchResults: Result $index - '${page.displayTitle}'")
                        }
                        searchAdapter.submitList(results)
                        updateEmptyState(results)
                    }
                }
                
                launch {
                    viewModel.isSearching.collect { isSearching ->
                        Log.d(TAG, "observeSearchResults: Search loading state = $isSearching")
                        binding.progressBar.visibility = if (isSearching) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updateEmptyState(results: List<ReadingListPage>) {
        val query = binding.searchEditText.text?.toString()?.trim() ?: ""
        
        when {
            query.isEmpty() -> {
                binding.emptyStateContainer.visibility = View.VISIBLE
                binding.emptyStateText.text = getString(R.string.search_hint_saved_pages)
                binding.searchRecyclerView.visibility = View.GONE
            }
            results.isEmpty() -> {
                binding.emptyStateContainer.visibility = View.VISIBLE
                binding.emptyStateText.text = getString(R.string.search_no_results)
                binding.searchRecyclerView.visibility = View.GONE
            }
            else -> {
                binding.emptyStateContainer.visibility = View.GONE
                binding.searchRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun navigateToPage(savedPage: ReadingListPage) {
        val pageTitle = savedPage.apiTitle
        val pageId = savedPage.mediaWikiPageId?.toString()

        val intent = PageActivity.newIntent(
            context = this,
            pageTitle = pageTitle,
            pageId = pageId,
            source = HistoryEntry.SOURCE_SAVED_PAGE,
            snippet = savedPage.description,
            thumbnailUrl = savedPage.thumbUrl
        )
        
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::voiceRecognitionManager.isInitialized) {
            voiceRecognitionManager.handlePermissionResult(requestCode, grantResults)
        }
    }

    companion object {
        private const val TAG = "SavedPagesSearchActivity"
        
        fun newIntent(context: Context): Intent {
            return Intent(context, SavedPagesSearchActivity::class.java)
        }
    }
}