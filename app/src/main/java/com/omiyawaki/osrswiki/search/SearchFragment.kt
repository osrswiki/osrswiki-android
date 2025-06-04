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
import com.omiyawaki.osrswiki.history.db.HistoryEntry // Added import
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration
import com.omiyawaki.osrswiki.ui.main.ScrollableContent
import com.omiyawaki.osrswiki.ui.common.FragmentToolbarPolicyProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L
import java.io.IOException

class SearchFragment : Fragment(),
    ScreenConfiguration,
    SearchAdapter.OnItemClickListener,
    ScrollableContent,
    FragmentToolbarPolicyProvider {

    private val viewModel: SearchViewModel by viewModels {
        val application = requireActivity().application as OSRSWikiApp
        SearchViewModelFactory(application, application.currentNetworkStatus)
    }
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var onlineSearchAdapter: SearchAdapter
    private lateinit var offlineSearchAdapter: OfflineSearchAdapter

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
        offlineSearchAdapter = OfflineSearchAdapter(this)

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
                        // Ensure R.string.offline_indicator_generic ("You are currently offline.")
                        // and R.string.offline_search_active_message ("Currently offline. Searching saved pages.")
                        // are defined in strings.xml.
                        if (!isOnline && !trimmedQuery.isNullOrBlank()) {
                            binding.textViewOfflineIndicator.text = getString(R.string.offline_search_active_message)
                            binding.textViewOfflineIndicator.isVisible = true
                        } else if (!isOnline && trimmedQuery.isNullOrBlank()) {
                            binding.textViewOfflineIndicator.text = getString(R.string.offline_indicator_generic)
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
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            }
                        } else { // Offline mode
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                                L.d("Switched to OfflineSearchAdapter.")
                            }
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            } else {
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
                                updateUiForBlankQuery()
                            }
                            binding.progressBarSearch.isVisible = false
                            binding.textViewSearchError.isVisible = false
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
                                if (!currentQuery.isNullOrBlank()) {
                                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                                    binding.textViewNoResults.isVisible = true
                                } else {
                                    updateUiForBlankQuery()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateUiForBlankQuery() {
        binding.progressBarSearch.isVisible = false
        binding.recyclerViewSearchResults.isVisible = false
        binding.textViewSearchError.isVisible = false
        binding.textViewNoResults.text = getString(R.string.search_enter_query_prompt)
        binding.textViewNoResults.isVisible = true
    }

    private fun setupOnBackPressed() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchView.query.isNotEmpty()) {
                    binding.searchView.setQuery("", false)
                    // viewModel.performSearch("") // This will be triggered by onQueryTextChange
                } else {
                    isEnabled = false
                    try {
                        if (isResumed && parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else if (isResumed) {
                            // Fallback to activity's default dispatcher if fragment cannot pop
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    } finally {
                        // Re-enable the callback only if it's still added to the dispatcher
                        // and was disabled by this specific handler. This check might be complex
                        // depending on how NavController handles its own dispatcher additions.
                        // A simpler approach is often to just let NavController handle it if possible,
                        // or ensure this callback is always active and checks conditions.
                        // For now, keeping the original re-enable logic.
                        if (isAdded && !isEnabled) { isEnabled = true }
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        L.d("SearchFragment onItemClick: Item Title='${item.title}', Item ID='${item.id}', IsFTS=${item.isFtsResult}, Source=SOURCE_SEARCH")
        val intent = PageActivity.newIntent(
            context = requireContext(),
            pageTitle = item.title,
            pageId = item.id,
            source = HistoryEntry.SOURCE_SEARCH // Added source
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