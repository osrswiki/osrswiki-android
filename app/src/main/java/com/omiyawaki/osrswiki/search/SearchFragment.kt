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
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L
import java.io.IOException

class SearchFragment : Fragment(), ScreenConfiguration, SearchAdapter.OnItemClickListener {

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

        (activity as? MainActivity)?.updateToolbar(this)
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

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(this)
        binding.recyclerViewSearchResults.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(context)
        }
        L.i("SearchAdapter and recyclerViewSearchResults setup complete.")
    }

    private fun setupSearchView() {
        // Ensure the SearchView is expanded and set the hint
        binding.searchView.isIconified = false // <<< ADDED: Force expanded state
        binding.searchView.queryHint = getString(R.string.search_hint_text) // Re-affirm hint

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

                launch {
                    viewModel.offlineSearchResults.collect { offlineResults ->
                        L.d("Offline search results count: ${offlineResults.size}")
                        // TODO: Decide how to display offline results.
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
                            L.d("LoadState: Refresh is Loading - Showing ProgressBar")
                            binding.progressBarSearch.visibility = View.VISIBLE
                        } else if (isError) {
                            L.d("LoadState: Refresh is Error - Showing Error Message. Error: ${error?.message}")
                            val errorMessage = when (error) {
                                is IOException -> getString(R.string.search_error_network)
                                else -> error?.localizedMessage ?: getString(R.string.search_error_generic)
                            }
                            binding.textViewSearchError.text = errorMessage
                            binding.textViewSearchError.visibility = View.VISIBLE
                        } else if (hasResults) {
                            L.d("LoadState: NotLoading, Has Results - Showing RecyclerView")
                            binding.recyclerViewSearchResults.visibility = View.VISIBLE
                        } else { 
                            val currentSearchQuery = viewModel.screenUiState.value.currentQuery
                            if (!currentSearchQuery.isNullOrBlank()) {
                                L.d("LoadState: NotLoading, No Results for query: $currentSearchQuery - Showing 'No results'")
                                binding.textViewNoResults.text = getString(R.string.search_no_results)
                                binding.textViewNoResults.visibility = View.VISIBLE
                            } else {
                                L.d("LoadState: NotLoading, No Results, Blank Query - UI is blank (no prompt).")
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
                        if (isAdded) {
                           isEnabled = true
                        }
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        L.d("Search item clicked: Title='${item.title}', ID='${item.id}'")
        (activity as? MainActivity)?.getRouter()?.navigateToArticle(
            articleId = item.id,
            articleTitle = item.title
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewSearchResults.adapter = null
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
