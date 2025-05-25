package com.omiyawaki.osrswiki.ui.search

// Added imports for custom navigation
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.navigation.NavigationIconType
import com.omiyawaki.osrswiki.navigation.Router
import com.omiyawaki.osrswiki.navigation.ScreenConfiguration

// Original and necessary Android/Kotlin imports
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
import androidx.paging.LoadState // Retained for use in observeCombinedSearchStates
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R // For R.string.app_name and other resource IDs
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine // Retained for use in observeCombinedSearchStates
import kotlinx.coroutines.launch

// Assuming CleanedSearchResultItem is defined elsewhere and used by adapters.
// Example: data class CleanedSearchResultItem(val id: Long, val title: String, /* other fields */)

class SearchFragment : Fragment(), ScreenConfiguration {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!! // Non-null assertion for guaranteed lifecycle

    // ViewModel setup using a factory
    @Suppress("unused") // viewModel is used by its delegates and observers
    private val viewModel: SearchViewModel by viewModels { SearchViewModelFactory(requireActivity().application) }

    // Adapters for RecyclerViews
    private lateinit var searchResultAdapter: SearchResultAdapter // For online Paging results
    private lateinit var offlineResultAdapter: OfflineResultAdapter   // For offline search results

    // Router instance for navigation
    private lateinit var router: Router

    // ScreenConfiguration implementation
    override val screenTitle: String
        get() = resources.getString(R.string.app_name) // Toolbar title

    override val navigationIconType: NavigationIconType
        get() = NavigationIconType.NONE // No navigation icon (e.g., back arrow) for search screen

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Obtain the Router instance from MainActivity, which hosts it.
        if (context is MainActivity) {
            router = context.getRouter()
        } else {
            throw IllegalStateException("The hosting activity must be MainActivity to provide a Router instance.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        // Add any general view setup that doesn't depend on onViewCreated specifics here.
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup UI components
        setupOnlineSearchRecyclerView()
        setupOfflineResultsRecyclerView()
        setupSearchInput()

        // Start observing LiveData/Flows for UI updates
        observeCombinedSearchStates() // Handles most UI state logic
        observeOnlineSearchResults()  // Specifically for submitting PagingData to the online adapter
    }

    private fun handleSearchResultItemClick(cleanedSearchResultItem: CleanedSearchResultItem) {
        Log.d(TAG, "Clicked item ID: ${cleanedSearchResultItem.id}, Title: ${cleanedSearchResultItem.title}")
        // Use the router to navigate to the article details screen
        router.navigateToArticle(
            articlePageId = cleanedSearchResultItem.id.toString(), // ID converted to String as required by Router
            articleTitle = cleanedSearchResultItem.title           // Pass title for potential use in ArticleFragment
        )
    }

    private fun setupOnlineSearchRecyclerView() {
        searchResultAdapter = SearchResultAdapter { article ->
            handleSearchResultItemClick(article)
        }
        binding.searchResultsRecyclerview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchResultAdapter
            // Consider adding ItemDecorators or other configurations if needed
        }
    }

    private fun setupOfflineResultsRecyclerView() {
        offlineResultAdapter = OfflineResultAdapter { article ->
            handleSearchResultItemClick(article)
        }
        binding.offlineResultsRecyclerview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = offlineResultAdapter
            // Consider adding ItemDecorators or other configurations if needed
        }
    }

    private fun setupSearchInput() {
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                return@setOnEditorActionListener true // Consume the event
            }
            false // Do not consume the event
        }
        // Optionally, could add a TextWatcher here for live search suggestions if desired in future
    }

    private fun performSearch() {
        val query = binding.searchEditText.text.toString().trim()
        // ViewModel handles the logic for both online and offline search execution
        viewModel.performSearch(query)
        hideKeyboard() // Hide keyboard after search is initiated
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    // Observes the flow of online search results (PagingData) and submits it to the adapter.
    private fun observeOnlineSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResultsFlow.collectLatest { pagingData ->
                    Log.d(TAG, "Submitting new PagingData to online search adapter.")
                    searchResultAdapter.submitData(pagingData)
                }
            }
        }
    }

    // Central observer for combining various UI states (query, offline results, online load states)
    // to manage the visibility of different UI elements like lists, progress bars, and messages.
    private fun observeCombinedSearchStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.screenUiState,        // For current query and initial messages
                    viewModel.offlineSearchResults, // For locally stored search results
                    searchResultAdapter.loadStateFlow // For online search PagingData load states
                ) { screenState, offlineResults, onlineLoadStates ->
                    // Package the latest values from each flow into a Triple for easier handling
                    Triple(screenState, offlineResults, onlineLoadStates)
                }.collectLatest { (screenState, offlineResults, onlineLoadStates) ->
                    val query = screenState.currentQuery
                    val isQueryBlank = query.isNullOrBlank()

                    val onlineRefreshState = onlineLoadStates.refresh
                    // It's generally safe to check itemCount on the adapter after data has been submitted or load state changes.
                    val onlineItemCount = searchResultAdapter.itemCount

                    // 1. Offline Results Visibility and Data Submission
                    val hasOfflineResults = offlineResults.isNotEmpty() && !isQueryBlank
                    binding.offlineResultsTitleTextview.isVisible = hasOfflineResults
                    binding.offlineResultsRecyclerview.isVisible = hasOfflineResults
                    offlineResultAdapter.submitList(if (hasOfflineResults) offlineResults else emptyList())
                    if (hasOfflineResults) {
                        Log.d(TAG, "Displaying ${offlineResults.size} offline results for query: '$query'")
                    }

                    // 2. Online Results Visibility
                    // Show online results if loading has finished, items exist, and there's an active query.
                    val hasOnlineResults = onlineRefreshState is LoadState.NotLoading && onlineItemCount > 0 && !isQueryBlank
                    binding.searchResultsRecyclerview.isVisible = hasOnlineResults
                    if (hasOnlineResults) {
                        Log.d(TAG, "Displaying $onlineItemCount online results for query: '$query'")
                    }

                    // 3. Progress Bar Visibility (for online search)
                    val isOnlineLoading = onlineRefreshState is LoadState.Loading && !isQueryBlank
                    binding.searchProgressBar.isVisible = isOnlineLoading
                    if (isOnlineLoading) {
                        Log.d(TAG, "Online search is loading for query: '$query'")
                        // If online results RV was visible but is now loading new data (empty), hide it to prevent showing old items.
                        if (binding.searchResultsRecyclerview.isVisible && onlineItemCount == 0) {
                             binding.searchResultsRecyclerview.isVisible = false
                        }
                    }

                    // 4. Message TextView Logic (handles initial prompts, no results, errors)
                    val searchMessageTextView = binding.searchMessageTextview
                    when {
                        // Case: No query entered, show initial prompt from ViewModel.
                        isQueryBlank && screenState.messageResId != null -> {
                            searchMessageTextView.text = getString(screenState.messageResId)
                            searchMessageTextView.isVisible = true
                            binding.searchResultsRecyclerview.isVisible = false // Ensure online results are hidden
                            // Offline results visibility is determined by 'hasOfflineResults' which will be false if query is blank.
                            Log.d(TAG, "Displaying initial prompt: ${searchMessageTextView.text}")
                        }
                        // Case: Query entered, online search finished, no online results, and no offline results.
                        !isQueryBlank && onlineRefreshState is LoadState.NotLoading && onlineItemCount == 0 && !hasOfflineResults -> {
                            searchMessageTextView.text = getString(R.string.search_no_results)
                            searchMessageTextView.isVisible = true
                            Log.d(TAG, "Displaying 'No results' for query: '$query'")
                        }
                        // Case: Query entered, online search resulted in an error, and no offline results to show.
                        !isQueryBlank && onlineRefreshState is LoadState.Error && !hasOfflineResults -> {
                            val error = (onlineRefreshState as LoadState.Error).error
                            Log.e(TAG, "Online LoadState Error for query '$query': ${error.localizedMessage}", error)
                            searchMessageTextView.text = getString(R.string.search_network_error)
                            searchMessageTextView.isVisible = true
                        }
                        // Case: All other scenarios (results are shown, online is loading but offline might be visible, etc.)
                        else -> {
                            searchMessageTextView.isVisible = false
                            // Verbose log, uncomment if needed for debugging specific visibility states.
                            // Log.d(TAG, "Hiding message TextView. Query: '$query', OnlineLoading: $isOnlineLoading, HasOnline: $hasOnlineResults, HasOffline: $hasOfflineResults")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear references to binding and adapters to prevent memory leaks.
        // RecyclerViews can hold onto adapters if not explicitly cleared.
        binding.searchResultsRecyclerview.adapter = null
        binding.offlineResultsRecyclerview.adapter = null
        _binding = null // Critical to avoid memory leaks with ViewBinding in Fragments
    }

    companion object {
        private const val TAG = "SearchFragment" // Tag for logging

        // Factory method for creating instances of this fragment.
        // Useful if arguments need to be passed in the future.
        @JvmStatic
        fun newInstance(): SearchFragment {
            return SearchFragment()
        }
    }
}
