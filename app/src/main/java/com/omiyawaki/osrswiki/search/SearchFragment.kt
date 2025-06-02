package com.omiyawaki.osrswiki.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
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
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration
import com.omiyawaki.osrswiki.ui.main.ScrollableContent
import com.omiyawaki.osrswiki.ui.common.FragmentToolbarPolicyProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine // <<< ADDED IMPORT
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L
import java.io.IOException

class SearchFragment : Fragment(),
    ScreenConfiguration,
    SearchAdapter.OnItemClickListener, // Assuming OfflineSearchAdapter uses this too
    ScrollableContent,
    FragmentToolbarPolicyProvider {

    private val viewModel: SearchViewModel by viewModels {
        val application = requireActivity().application as OSRSWikiApp
        SearchViewModelFactory(application, application.currentNetworkStatus)
    }
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var onlineSearchAdapter: SearchAdapter // Your PagingDataAdapter
    private lateinit var offlineSearchAdapter: OfflineSearchAdapter // <<< Use the new ListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViewAdapters()
        setupSearchView()
        observeViewModel()
        setupOnBackPressed()
    }

    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        return getString(R.string.title_search)
    }

    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.NONE
    }

    override fun hasCustomOptionsMenu(): Boolean {
        return false
    }

    private fun setupRecyclerViewAdapters() {
        onlineSearchAdapter = SearchAdapter(this)
        offlineSearchAdapter = OfflineSearchAdapter(this) // <<< Instantiate new adapter

        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(context)
        L.i("RecyclerView adapters initialized.")
    }

    private fun setupSearchView() {
        binding.searchView.isIconified = false
        binding.searchView.queryHint = getString(R.string.search_hint_text)

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.performSearch(query?.trim() ?: "")
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.performSearch(newText ?: "")
                return true
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Main UI mode controller based on network status and query
                launch {
                    viewModel.isOnline.combine(viewModel.currentQuery) { isOnline, query ->
                        Pair(isOnline, query?.trim())
                    }.collectLatest { (isOnline, trimmedQuery) ->
                        // Set offline indicator text and visibility
                        if (!isOnline && !trimmedQuery.isNullOrBlank()) {
                            binding.textViewOfflineIndicator.text = getString(R.string.offline_search_active_message)
                            binding.textViewOfflineIndicator.isVisible = true
                        } else if (!isOnline && trimmedQuery.isNullOrBlank()) {
                            // Optionally, show a persistent "You are offline" message even with no query
                            binding.textViewOfflineIndicator.text = getString(R.string.offline_indicator_generic) // New string: "You are currently offline."
                            binding.textViewOfflineIndicator.isVisible = true
                        }
                        else {
                            binding.textViewOfflineIndicator.isVisible = false
                        }

                        if (isOnline) {
                            if (binding.recyclerViewSearchResults.adapter != onlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = onlineSearchAdapter
                                L.d("Switched to OnlineSearchAdapter.")
                            }
                            // UI updates for online mode will be primarily driven by onlineSearchAdapter.loadStateFlow
                            // and the onlineSearchResultsFlow collection below.
                            // If query is blank, PagingAdapter will show empty.
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            }
                        } else { // Offline mode
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                                L.d("Switched to OfflineSearchAdapter.")
                            }
                            // UI for offline will be driven by combinedOfflineResultsList collection.
                            // If query is blank, the combinedOfflineResultsList should be empty.
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            } else {
                                // If there's a query, visibility will be handled by combinedOfflineResultsList collector
                                // ensure progress bar is hidden for offline mode search.
                                binding.progressBarSearch.isVisible = false
                            }
                        }
                    }
                }

                // Observe Online Paged Search Results
                launch {
                    viewModel.onlineSearchResultsFlow.collectLatest { pagingData ->
                        if (binding.recyclerViewSearchResults.adapter == onlineSearchAdapter) {
                            L.d("Submitting Online PagingData")
                            onlineSearchAdapter.submitData(pagingData)
                            // Visibility of recycler/no results for online is handled by loadStateFlow
                        }
                    }
                }

                // Observe Combined Offline Results
                launch {
                    viewModel.combinedOfflineResultsList.collectLatest { combinedOfflineList ->
                        if (binding.recyclerViewSearchResults.adapter == offlineSearchAdapter) {
                            L.d("Submitting Combined Offline List: ${combinedOfflineList.size} items")
                            offlineSearchAdapter.submitList(combinedOfflineList)

                            val currentQuery = viewModel.currentQuery.value?.trim()
                            if (!currentQuery.isNullOrBlank()) {
                                binding.recyclerViewSearchResults.isVisible = combinedOfflineList.isNotEmpty()
                                binding.textViewNoResults.isVisible = combinedOfflineList.isEmpty()
                                if (combinedOfflineList.isEmpty()) {
                                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                                }
                            } else {
                                updateUiForBlankQuery() // Handles blank query state for offline too
                            }
                            binding.progressBarSearch.isVisible = false // Ensure progress bar is hidden
                            binding.textViewSearchError.isVisible = false // No specific error state for offline list from ViewModel
                        }
                    }
                }

                // Observe Load States for Paging (Online Search)
                launch {
                    onlineSearchAdapter.loadStateFlow.collectLatest { loadStates ->
                        if (binding.recyclerViewSearchResults.adapter != onlineSearchAdapter) return@collectLatest

                        val isLoading = loadStates.refresh is LoadState.Loading
                        val isError = loadStates.refresh is LoadState.Error
                        val error = if (isError) (loadStates.refresh as LoadState.Error).error else null
                        val currentQuery = viewModel.currentQuery.value?.trim()

                        binding.progressBarSearch.isVisible = isLoading
                        // Default visibility states
                        binding.recyclerViewSearchResults.isVisible = false
                        binding.textViewNoResults.isVisible = false
                        binding.textViewSearchError.isVisible = false

                        if (isLoading) {
                            // Progress bar is already visible
                        } else if (isError) {
                            val errorMessage = when (error) {
                                is IOException -> getString(R.string.search_error_network)
                                else -> error?.localizedMessage ?: getString(R.string.search_error_generic)
                            }
                            binding.textViewSearchError.text = errorMessage
                            binding.textViewSearchError.isVisible = true
                        } else { // Not loading, no error
                            if (onlineSearchAdapter.itemCount > 0) {
                                binding.recyclerViewSearchResults.isVisible = true
                            } else {
                                // No results for a non-blank query
                                if (!currentQuery.isNullOrBlank()) {
                                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                                    binding.textViewNoResults.isVisible = true
                                } else {
                                    // Query is blank, handled by updateUiForBlankQuery or initial state
                                    updateUiForBlankQuery()
                                }
                            }
                        }
                    }
                }
                // Note: viewModel.screenUiState can still be used for one-off messages or if the
                // "enter query" prompt needs to be shown explicitly outside of the blank query logic.
                // The current logic in updateUiForBlankQuery covers the "enter query" prompt.
            }
        }
    }

    // New helper function specifically for blank query state
    private fun updateUiForBlankQuery() {
        binding.progressBarSearch.isVisible = false
        binding.recyclerViewSearchResults.isVisible = false
        binding.textViewSearchError.isVisible = false
        binding.textViewNoResults.text = getString(R.string.search_enter_query_prompt)
        binding.textViewNoResults.isVisible = true
        // The offline indicator visibility when query is blank is handled in the main combine block.
    }


    private fun updateUiForNoQuery(query: String?, isOnline: Boolean) {
        if (query.isNullOrBlank()) {
            binding.progressBarSearch.isVisible = false
            binding.recyclerViewSearchResults.isVisible = false
            binding.textViewSearchError.isVisible = false
            binding.textViewNoResults.isVisible = true
            binding.textViewNoResults.text = getString(R.string.search_enter_query_prompt)
            // Offline indicator for blank query is handled by the main combine block
            if (!isOnline) { // Ensure offline indicator shows if query is blank AND offline
                binding.textViewOfflineIndicator.isVisible = true
                binding.textViewOfflineIndicator.text = getString(R.string.offline_search_active_message)
            } else {
                binding.textViewOfflineIndicator.isVisible = false
            }
        }
    }

    private fun updateUiForOfflineResults(isOfflineListEmpty: Boolean, currentQueryFromViewModel: String?) {
        // This is called when offlineSearchAdapter is active AND its list updates
        val query = currentQueryFromViewModel?.trim()

        binding.progressBarSearch.isVisible = false
        binding.recyclerViewSearchResults.isVisible = !isOfflineListEmpty
        binding.textViewSearchError.isVisible = false

        if (isOfflineListEmpty) {
            if (!query.isNullOrBlank()) {
                binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, query)
                binding.textViewNoResults.isVisible = true
            } else {
                binding.textViewNoResults.text = getString(R.string.search_enter_query_prompt)
                binding.textViewNoResults.isVisible = true
            }
        } else {
            binding.textViewNoResults.isVisible = false
        }
    }

    private fun setupOnBackPressed() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchView.query.isNotEmpty()) {
                    binding.searchView.setQuery("", false)
                    viewModel.performSearch("")
                } else {
                    isEnabled = false
                    try {
                        if (isResumed && parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else if (isResumed) {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    } finally {
                        if (isAdded && !isEnabled) { isEnabled = true }
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        L.d("SearchFragment onItemClick: Item Title='${item.title}', Item ID='${item.id}', IsFTS=${item.isFtsResult}")
        val intent = PageActivity.newIntent(
            context = requireContext(),
            pageTitle = item.title,
            pageId = item.id
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewSearchResults.adapter = null
        _binding = null
    }

    override fun getScrollableView(): View? {
        return _binding?.recyclerViewSearchResults
    }

    override fun getToolbarPolicy(): ToolbarPolicy {
        return ToolbarPolicy.HIDDEN
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}