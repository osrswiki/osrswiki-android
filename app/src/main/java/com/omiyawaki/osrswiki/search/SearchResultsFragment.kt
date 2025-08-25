package com.omiyawaki.osrswiki.search

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchResultsBinding
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException
import com.omiyawaki.osrswiki.test.PixelColorTest
import android.os.Handler
import android.os.Looper

class SearchResultsFragment : Fragment(), SearchAdapter.OnItemClickListener {

    private val viewModel: SearchViewModel by viewModels {
        val application = requireActivity().application as OSRSWikiApp
        SearchViewModelFactory(application, application.currentNetworkStatus)
    }
    private var _binding: FragmentSearchResultsBinding? = null
    private val binding get() = _binding!!

    private lateinit var onlineSearchAdapter: SearchAdapter
    private lateinit var offlineSearchAdapter: OfflineSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViewAdapters()
        observeViewModel()
    }

    fun search(query: String) {
        viewModel.performSearch(query)
    }

    private fun setupRecyclerViewAdapters() {
        onlineSearchAdapter = SearchAdapter(this)
        offlineSearchAdapter = OfflineSearchAdapter(this)
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(context)
        
        // DEBUG: Test colors with sample data
        testSearchAdapterColors()

        onlineSearchAdapter.addLoadStateListener { loadStates ->
            val refreshState = loadStates.refresh
            val currentQuery = viewModel.currentQuery.value?.trim()

            binding.progressBarSearch.isVisible = refreshState is LoadState.Loading
            binding.textViewSearchError.isVisible = refreshState is LoadState.Error

            if (refreshState is LoadState.NotLoading) {
                val hasResults = onlineSearchAdapter.itemCount > 0
                binding.recyclerViewSearchResults.isVisible = hasResults
                binding.textViewNoResults.isVisible = !hasResults && !currentQuery.isNullOrBlank()

                if (hasResults) {
                    onlineSearchAdapter.peek(0)?.let { topResult ->
                        viewModel.preemptivelyLoadTopResult(topResult)
                    }
                } else if (!currentQuery.isNullOrBlank()) {
                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                }
            } else if (refreshState is LoadState.Error) {
                val error = refreshState.error
                val errorMessage = when (error) {
                    is IOException -> getString(R.string.search_error_network)
                    else -> error.localizedMessage ?: getString(R.string.search_error_generic)
                }
                binding.textViewSearchError.text = errorMessage
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOnline.combine(viewModel.currentQuery) { isOnline, query ->
                        Pair(isOnline, query?.trim())
                    }.collectLatest { (isOnline, trimmedQuery) ->
                        if (isOnline) {
                            if (binding.recyclerViewSearchResults.adapter != onlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = onlineSearchAdapter
                            }
                        } else {
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                            }
                        }
                        binding.textViewOfflineIndicator.isVisible = !isOnline
                    }
                }

                launch {
                    viewModel.onlineSearchResultsFlow.collectLatest { pagingData ->
                        if (binding.recyclerViewSearchResults.adapter == onlineSearchAdapter) {
                            // Update search query in adapter for title highlighting
                            onlineSearchAdapter.updateSearchQuery(viewModel.currentQuery.value)
                            // Force refresh PagingData adapter and reset position
                            onlineSearchAdapter.refresh()
                            onlineSearchAdapter.submitData(pagingData)
                            // Force RecyclerView to scroll to top after data submission
                            binding.recyclerViewSearchResults.post {
                                (binding.recyclerViewSearchResults.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                            }
                            Log.d("ScrollFix", "Online search: Force refresh and reset to position 0")
                        }
                    }
                }

                launch {
                    viewModel.combinedOfflineResultsList.collectLatest { combinedOfflineList ->
                        if (binding.recyclerViewSearchResults.adapter == offlineSearchAdapter) {
                            // Update search query in offline adapter for title highlighting
                            offlineSearchAdapter.updateSearchQuery(viewModel.currentQuery.value)
                            // Reset RecyclerView position before submitting new data
                            // This ensures new search results are displayed from the top
                            (binding.recyclerViewSearchResults.layoutManager as? LinearLayoutManager)?.scrollToPosition(0)
                            offlineSearchAdapter.submitList(combinedOfflineList)
                            Log.d("ScrollFix", "Offline search: Reset position to 0 before submitting new data")
                            val currentQuery = viewModel.currentQuery.value?.trim()
                            val hasResults = combinedOfflineList.isNotEmpty()
                            binding.recyclerViewSearchResults.isVisible = hasResults
                            binding.textViewNoResults.isVisible = !hasResults && !currentQuery.isNullOrBlank()
                            if (!hasResults && !currentQuery.isNullOrBlank()) {
                                binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                            }
                            binding.progressBarSearch.isVisible = false
                            binding.textViewSearchError.isVisible = false
                            
                            // Run pixel test after results are loaded
                            if (hasResults) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    Log.e("PixelTest", "Running pixel test for query: $currentQuery")
                                    PixelColorTest.runPixelTest(requireContext(), binding.recyclerViewSearchResults)
                                }, 1000)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        viewModel.saveCurrentQuery() // Save the query when an item is clicked.
        val intent = PageActivity.newIntent(
            context = requireContext(),
            pageTitle = item.title,
            pageId = item.id,
            source = HistoryEntry.SOURCE_SEARCH,
            snippet = item.snippet,
            thumbnailUrl = item.thumbnailUrl
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun testSearchAdapterColors() {
        // Create test data
        val testItem = CleanedSearchResultItem(
            id = "1",
            title = "Test Dragon Item Title",
            snippet = "This is a test snippet about dragons and items",
            thumbnailUrl = null
        )
        
        // Create a test binding
        val testBinding = ItemSearchResultBinding.inflate(layoutInflater)
        val viewHolder = SearchAdapter.SearchResultViewHolder(testBinding, this)
        
        // Test without search query
        Log.e("ColorTest", "=== TESTING WITHOUT QUERY ===")
        viewHolder.bind(testItem, null)
        val titleNoQuery = testBinding.searchItemTitle.currentTextColor
        val snippetNoQuery = testBinding.searchItemSnippet.currentTextColor
        Log.e("ColorTest", "No query - Title: #${Integer.toHexString(titleNoQuery)}")
        Log.e("ColorTest", "No query - Snippet: #${Integer.toHexString(snippetNoQuery)}")
        if (titleNoQuery != snippetNoQuery) {
            Log.e("ColorTest", "🔴 MISMATCH without query!")
        }
        
        // Test with search query
        Log.e("ColorTest", "\n=== TESTING WITH QUERY ===")
        viewHolder.bind(testItem, "dragon")
        val titleWithQuery = testBinding.searchItemTitle.currentTextColor
        val snippetWithQuery = testBinding.searchItemSnippet.currentTextColor
        Log.e("ColorTest", "With query - Title: #${Integer.toHexString(titleWithQuery)}")
        Log.e("ColorTest", "With query - Snippet: #${Integer.toHexString(snippetWithQuery)}")
        if (titleWithQuery != snippetWithQuery) {
            Log.e("ColorTest", "🔴 MISMATCH with query!")
        }
    }
    
    companion object {
        fun newInstance() = SearchResultsFragment()
    }
}
