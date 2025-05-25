package com.omiyawaki.osrswiki.ui.search
import kotlinx.coroutines.flow.combine

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

   @Suppress("unused")
    private val viewModel: SearchViewModel by viewModels { SearchViewModelFactory(requireActivity().application) }
    private lateinit var searchResultAdapter: SearchResultAdapter // For online Paging results
    private lateinit var offlineResultAdapter: OfflineResultAdapter     // For offline search results

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOnlineSearchRecyclerView()
        setupOfflineResultsRecyclerView() // New: Setup Offline Results RecyclerView
        setupSearchInput()
        observeCombinedSearchStates() // New combined observer for UI states
        observeOnlineSearchResults()
        // observeOnlineLoadStates() - Logic moved to observeCombinedSearchStates()
        // observeOfflineSearchResults() - Logic moved to observeCombinedSearchStates() // New: Observe Offline Results
        // observeScreenMessages() - Logic moved to observeCombinedSearchStates()
    }

    private fun handleSearchResultItemClick(cleanedSearchResultItem: CleanedSearchResultItem) {
        Log.d("SearchFragment", "Clicked item ID: ${cleanedSearchResultItem.id}, Title: ${cleanedSearchResultItem.title}")
        // Ensure snippet (which might contain HTML) doesn't break navigation if passed.
        // Here, only ID is passed, which is safe.
        val action = SearchFragmentDirections.actionSearchFragmentToArticleFragment(cleanedSearchResultItem.id)
        findNavController().navigate(action)
    }

    private fun setupOnlineSearchRecyclerView() {
        searchResultAdapter = SearchResultAdapter { cleanedSearchResultItem ->
            handleSearchResultItemClick(cleanedSearchResultItem)
        }
        binding.searchResultsRecyclerview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchResultAdapter
        }
    }

    private fun setupOfflineResultsRecyclerView() {
        offlineResultAdapter = OfflineResultAdapter { cleanedSearchResultItem ->
            handleSearchResultItemClick(cleanedSearchResultItem)
        }
        binding.offlineResultsRecyclerview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = offlineResultAdapter
            // Consider if nested scrolling should be disabled if parent is scrollable:
            // isNestedScrollingEnabled = false
        }
    }

    private fun setupSearchInput() {
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun performSearch() {
        val query = binding.searchEditText.text.toString().trim()
        // This one call in ViewModel should trigger both offline metadata search and online searches via its internal logic
        viewModel.performSearch(query)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun observeOfflineSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.offlineSearchResults.collectLatest { offlineResults ->
                    val query = viewModel.screenUiState.value.currentQuery
                    val hasQuery = !query.isNullOrBlank()
                    val hasOfflineResults = offlineResults.isNotEmpty()

                    Log.d("SearchFragment", "Offline search results observed. Query: '$query', Count: ${offlineResults.size}")

                    // Show offline search results section if there's a query and results are found
                    binding.offlineResultsTitleTextview.isVisible = hasQuery && hasOfflineResults
                    binding.offlineResultsRecyclerview.isVisible = hasQuery && hasOfflineResults
                    offlineResultAdapter.submitList(if (hasQuery) offlineResults else emptyList())

                    // If offline search results are shown, potentially hide general "no results" message for online search,
                    // especially if online search hasn't completed or also yielded no results.
                    // This coordination might need refinement based on desired UX.
                    // For now, observeLoadStates will handle messages for the online part.
                    // If offline search has results, the screen won't look completely empty.
                }
            }
        }
    }

    private fun observeOnlineSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResultsFlow.collectLatest { pagingData ->
                    Log.d("SearchFragment", "Submitting new PagingData to online search adapter.")
                    searchResultAdapter.submitData(pagingData)
                }
            }
        }
    }

    private fun observeOnlineLoadStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchResultAdapter.loadStateFlow.collectLatest { loadStates ->
                    val refreshState = loadStates.refresh
                    Log.d("SearchFragment", "Online LoadState: $refreshState, Item count: ${searchResultAdapter.itemCount}")

                    // Only manage progress bar and online-specific messages if offline search results are not already filling the screen
                    // or if we want to show online loading state regardless.
                    // For now, let offline search results visibility be independent. Online section shows its state.

                    binding.searchProgressBar.isVisible = refreshState is LoadState.Loading

                    when (refreshState) {
                        is LoadState.Loading -> {
                            binding.searchMessageTextview.isVisible = false
                            binding.searchResultsRecyclerview.isVisible = false // Hide online RV while loading it
                        }
                        is LoadState.NotLoading -> {
                            val currentQuery = viewModel.screenUiState.value.currentQuery
                            val onlineResultsEmpty = searchResultAdapter.itemCount == 0
                            val hasQuery = !currentQuery.isNullOrBlank()

                            if (onlineResultsEmpty && hasQuery) {
                                // Online search attempted for a query, but no online results.
                                // Offline search might have results. If offline search also has no results, this message is appropriate.
                                // If offline search *has* results, this message might be confusing.
                                // Let's show it if offline search results are also empty for this query.
                                if (binding.offlineResultsRecyclerview.isVisible.not()) {
                                    binding.searchMessageTextview.text = getString(R.string.search_no_results)
                                    binding.searchMessageTextview.isVisible = true
                                } else {
                                    binding.searchMessageTextview.isVisible = false // Offline search has results, hide "no results"
                                }
                                binding.searchResultsRecyclerview.isVisible = false
                            } else if (!onlineResultsEmpty) {
                                // Online results found
                                binding.searchMessageTextview.isVisible = false
                                binding.searchResultsRecyclerview.isVisible = true
                            } else {
                                // No query or initial state for online part
                                // Let observeScreenMessages handle initial prompt if offline search results are also not visible.
                                if (binding.offlineResultsRecyclerview.isVisible.not()) {
                                     // This state will be typically caught by observeScreenMessages for initial prompt
                                     // binding.searchMessageTextview.isVisible = true; // (handled by observeScreenMessages)
                                } else {
                                     binding.searchMessageTextview.isVisible = false
                                }
                                binding.searchResultsRecyclerview.isVisible = false
                            }
                        }
                        is LoadState.Error -> {
                            Log.e("SearchFragment", "Online LoadState Error: ${refreshState.error.localizedMessage}", refreshState.error)
                            if (binding.offlineResultsRecyclerview.isVisible.not()) { // Show error only if no offline search results
                                binding.searchMessageTextview.text = getString(R.string.search_network_error)
                                binding.searchMessageTextview.isVisible = true
                            } else {
                                binding.searchMessageTextview.isVisible = false // Offline search has results, hide network error
                            }
                            binding.searchResultsRecyclerview.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun observeScreenMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine screenUiState and loadStateFlow to react to changes in either
                viewModel.screenUiState.combine(searchResultAdapter.loadStateFlow) { screenState, loadStates ->
                    Pair(screenState, loadStates) // Emit a pair of the latest values
                }.collectLatest { (screenState, loadStates) -> // Destructure the pair

                    val offlineResultsVisible = binding.offlineResultsRecyclerview.isVisible
                    // Determine online loading state directly from the collected loadStates
                    val actualOnlineLoading = loadStates.refresh is LoadState.Loading
                    val onlineResultsVisible = binding.searchResultsRecyclerview.isVisible

                    // Determine if a specific message (error/no results) from online search should be active
                    val isOnlineError = loadStates.refresh is LoadState.Error
                    val isOnlineNotLoadingAndEmptyAfterQuery = loadStates.refresh is LoadState.NotLoading &&
                            searchResultAdapter.itemCount == 0 && // itemCount is okay to check on adapter
                            !screenState.currentQuery.isNullOrBlank() // Use collected screenState for currentQuery

                    val shouldOnlineSpecificMessageBeActive =
                        (isOnlineError && !binding.offlineResultsRecyclerview.isVisible) ||
                        (isOnlineNotLoadingAndEmptyAfterQuery && !binding.offlineResultsRecyclerview.isVisible)

                    // Logic to display the initial "Enter search query" prompt
                    if (screenState.messageResId != null && screenState.currentQuery.isNullOrBlank()) {
                        // Show initial prompt only if no other content or specific message is active
                        if (!offlineResultsVisible && !onlineResultsVisible && !actualOnlineLoading && !shouldOnlineSpecificMessageBeActive) {
                            binding.searchMessageTextview.text = getString(screenState.messageResId)
                            binding.searchMessageTextview.isVisible = true
                            binding.searchResultsRecyclerview.isVisible = false
                            binding.offlineResultsRecyclerview.isVisible = false
                            binding.offlineResultsTitleTextview.isVisible = false
                        }
                    } else if (offlineResultsVisible || onlineResultsVisible || actualOnlineLoading || shouldOnlineSpecificMessageBeActive) {
                        // If other content is visible/active, ensure the generic initial prompt (if it was showing) is hidden,
                        // unless a specific online message should be active (which observeOnlineLoadStates will handle).
                        if (screenState.messageResId != null &&
                            binding.searchMessageTextview.text == getString(screenState.messageResId) &&
                            binding.searchMessageTextview.isVisible
                            ) {
                            binding.searchMessageTextview.isVisible = false
                        }
                    } else {
                        // Default case: No initial prompt to show (e.g., query exists or no messageResId),
                        // and no other content/loading/specific messages are active. Hide the message text view.
                          binding.searchMessageTextview.isVisible = false
                    }
                }
            }
        }
    }


    private fun observeCombinedSearchStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.screenUiState, // Provides currentQuery, initial messageResId
                    viewModel.offlineSearchResults, // Provides List<CleanedSearchResultItem>
                    searchResultAdapter.loadStateFlow // Provides load states for online search
                ) { screenState, offlineResults, onlineLoadStates ->
                    // Using a Triple to hold these for processing
                    Triple(screenState, offlineResults, onlineLoadStates)
                }.collectLatest { (screenState, offlineResults, onlineLoadStates) ->
                    val query = screenState.currentQuery
                    val isQueryBlank = query.isNullOrBlank()

                    val onlineRefreshState = onlineLoadStates.refresh
                    val onlineItemCount = searchResultAdapter.itemCount

                    // 1. Offline Results Visibility and Data Submission
                    val hasOfflineResults = offlineResults.isNotEmpty() && !isQueryBlank
                    binding.offlineResultsTitleTextview.isVisible = hasOfflineResults
                    binding.offlineResultsRecyclerview.isVisible = hasOfflineResults
                    offlineResultAdapter.submitList(if (hasOfflineResults) offlineResults else emptyList())
                    if (hasOfflineResults) {
                        Log.d("SearchFragment", "Displaying ${offlineResults.size} offline results for query: '$query'")
                    }


                    // 2. Online Results Visibility
                    // Show online results if not loading, query is not blank, and there are items.
                    val hasOnlineResults = onlineRefreshState is LoadState.NotLoading && onlineItemCount > 0 && !isQueryBlank
                    binding.searchResultsRecyclerview.isVisible = hasOnlineResults
                    if (hasOnlineResults) {
                        Log.d("SearchFragment", "Displaying $onlineItemCount online results for query: '$query'")
                    }


                    // 3. Progress Bar Visibility (for online search)
                    val isOnlineLoading = onlineRefreshState is LoadState.Loading && !isQueryBlank
                    binding.searchProgressBar.isVisible = isOnlineLoading
                    if (isOnlineLoading) {
                        Log.d("SearchFragment", "Online search is loading for query: '$query'")
                    }

                    // 4. Message TextView Logic
                    val searchMessageTextView = binding.searchMessageTextview
                    when {
                        isQueryBlank && screenState.messageResId != null -> {
                            // Initial prompt state
                            searchMessageTextView.text = getString(screenState.messageResId)
                            searchMessageTextView.isVisible = true
                            // Ensure other result views are hidden for blank query prompt
                            binding.searchResultsRecyclerview.isVisible = false
                            // Offline results visibility is already handled above, but ensure consistency
                            Log.d("SearchFragment", "Displaying initial prompt: ${searchMessageTextView.text}")
                        }
                        !isQueryBlank && onlineRefreshState is LoadState.NotLoading && onlineItemCount == 0 && !hasOfflineResults -> {
                            // No results found for the query (both online and offline are empty after trying, and not currently loading online)
                            searchMessageTextView.text = getString(R.string.search_no_results)
                            searchMessageTextView.isVisible = true
                            Log.d("SearchFragment", "Displaying 'No results' for query: '$query'")
                        }
                        !isQueryBlank && onlineRefreshState is LoadState.Error && !hasOfflineResults -> {
                            // Online search error, no offline results to show instead, and not currently loading online
                            searchMessageTextView.text = getString(R.string.search_network_error)
                            searchMessageTextView.isVisible = true
                            Log.e("SearchFragment", "Displaying network error for query: '$query'", onlineRefreshState.error)
                        }
                        else -> {
                            // All other cases (results found in at least one list, or online still loading but offline might be visible, etc.)
                            searchMessageTextView.isVisible = false
                            Log.d("SearchFragment", "Hiding message TextView. Query: '$query', OnlineLoading: $isOnlineLoading, HasOnline: $hasOnlineResults, HasOffline: $hasOfflineResults")
                        }
                    }
                }
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        binding.searchResultsRecyclerview.adapter = null // Clear adapter for online results
        binding.offlineResultsRecyclerview.adapter = null    // Clear adapter for offline search results
        _binding = null
    }
}
