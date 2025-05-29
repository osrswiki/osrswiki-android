package com.omiyawaki.osrswiki.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
// import com.omiyawaki.osrswiki.MainActivity // Not directly used now for toolbar updates
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
import com.omiyawaki.osrswiki.ui.common.NavigationIconType // Keep if ScreenConfiguration is used elsewhere
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration // Keep if used elsewhere
import com.omiyawaki.osrswiki.ui.main.ScrollableContent
import com.omiyawaki.osrswiki.ui.common.FragmentToolbarPolicyProvider // Added import
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy // Added import
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L
import java.io.IOException

class SearchFragment : Fragment(),
    ScreenConfiguration, // Retain if other aspects of this interface are used by MainActivity
    SearchAdapter.OnItemClickListener,
    ScrollableContent,
    FragmentToolbarPolicyProvider { // Added FragmentToolbarPolicyProvider

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(requireActivity().application)
    }
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchView()
        observeViewModel()
        setupOnBackPressed()
        // Toolbar updates are now handled via FragmentToolbarPolicyProvider and MainScrollableViewProvider
    }

    // ScreenConfiguration methods - keep if MainActivity uses them for other purposes
    // If not, these can be removed if SearchFragment no longer directly configures MainActivity's toolbar
    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        return getString(R.string.title_search) // This might be irrelevant if toolbar is hidden
    }

    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.NONE // This might be irrelevant if toolbar is hidden
    }

    override fun hasCustomOptionsMenu(): Boolean {
        return false // This might be irrelevant if toolbar is hidden
    }
    // End ScreenConfiguration methods

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(this)
        binding.recyclerViewSearchResults.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(context)
        }
        L.i("SearchAdapter and recyclerViewSearchResults setup complete.")
    }

    private fun setupSearchView() {
        binding.searchView.isIconified = false
        binding.searchView.queryHint = getString(R.string.search_hint_text)

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    if (it.trim().isNotEmpty()) {
                        viewModel.performSearch(it.trim())
                    } else {
                        viewModel.performSearch("")
                    }
                    binding.searchView.clearFocus()
                } ?: run {
                    viewModel.performSearch("")
                    binding.searchView.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    viewModel.performSearch("")
                }
                return true
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResultsFlow.collectLatest { pagingData ->
                        L.d("Submitting PagingData to SearchAdapter")
                        searchAdapter.submitData(pagingData)
                    }
                }
                // ... other observer launches ...
                launch {
                    viewModel.offlineSearchResults.collect { offlineResults ->
                        L.d("Offline search results count: ${offlineResults.size}")
                    }
                }

                launch {
                    viewModel.screenUiState.collect { screenState ->
                        L.d("Screen UI State: MessageResId=${screenState.messageResId}, Query=${screenState.currentQuery}")
                    }
                }

                launch {
                    searchAdapter.loadStateFlow.collectLatest { loadStates ->
                        val isLoading = loadStates.refresh is LoadState.Loading
                        val isError = loadStates.refresh is LoadState.Error
                        val error = if (isError) (loadStates.refresh as LoadState.Error).error else null
                        val hasResults = searchAdapter.itemCount > 0

                        binding.progressBarSearch.visibility = View.GONE
                        binding.recyclerViewSearchResults.visibility = View.GONE
                        binding.textViewNoResults.visibility = View.GONE
                        binding.textViewSearchError.visibility = View.GONE

                        if (isLoading) {
                            binding.progressBarSearch.visibility = View.VISIBLE
                        } else if (isError) {
                            val errorMessage = when (error) {
                                is IOException -> getString(R.string.search_error_network)
                                else -> error?.localizedMessage ?: getString(R.string.search_error_generic)
                            }
                            binding.textViewSearchError.text = errorMessage
                            binding.textViewSearchError.visibility = View.VISIBLE
                        } else if (hasResults) {
                            binding.recyclerViewSearchResults.visibility = View.VISIBLE
                        } else {
                            val currentSearchQuery = viewModel.screenUiState.value.currentQuery
                            if (!currentSearchQuery.isNullOrBlank()) {
                                binding.textViewNoResults.text = getString(R.string.search_no_results)
                                binding.textViewNoResults.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupOnBackPressed() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchView.query.isNotEmpty()) {
                    binding.searchView.setQuery("", false)
                } else {
                    isEnabled = false
                    try {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    } finally {
                        if (isAdded) { isEnabled = true }
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        L.d("Search item clicked: Title='${item.title}', ID='${item.id}'")
        // Navigation logic will be handled by MainActivity via an interface
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewSearchResults.adapter = null
        _binding = null
    }

    // ScrollableContent implementation
    override fun getScrollableView(): View? {
        return _binding?.recyclerViewSearchResults
    }

    // FragmentToolbarPolicyProvider implementation
    override fun getToolbarPolicy(): ToolbarPolicy {
        return ToolbarPolicy.HIDDEN // SearchFragment wants the main toolbar hidden
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
