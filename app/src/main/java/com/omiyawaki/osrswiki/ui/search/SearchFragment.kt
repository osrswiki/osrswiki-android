package com.omiyawaki.osrswiki.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
// Changed import to use CleanedSearchResultItem from the ui.search package
import com.omiyawaki.osrswiki.ui.search.CleanedSearchResultItem
import com.omiyawaki.osrswiki.ui.common.NavigationIconType
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.omiyawaki.osrswiki.util.log.L

class SearchFragment : Fragment(), ScreenConfiguration, SearchAdapter.OnItemClickListener {

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(requireActivity().application)
    }
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter // Now SearchAdapter will be defined

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
        searchAdapter = SearchAdapter(this) // Initialize SearchAdapter
        binding.recyclerViewSearchResults.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(context)
        }
        L.i("SearchAdapter and recyclerViewSearchResults setup complete.")
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    viewModel.performSearch(it.trim())
                    binding.searchView.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // viewModel.performSearch(newText.orEmpty().trim()) // For live search
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
                        // TODO: Decide how to display offline results.
                        // For now, just log them. They could be a separate list or merged.
                        L.d("Offline search results count: ${offlineResults.size}")
                        if (offlineResults.isNotEmpty()) {
                             // Potentially show these if online results are empty or in a different section
                        }
                    }
                }
                launch {
                    viewModel.screenUiState.collect { screenState ->
                        L.d("Screen UI State: MessageResId=${screenState.messageResId}, Query=${screenState.currentQuery}")
                        // Handle general screen messages, e.g., initial prompt or errors not tied to PagingData load states
                        //binding.progressBarSearch.visibility = View.GONE // Assuming PagingData handles its own loading state via adapter
                        
                        // Visibility of "no results" or "enter query" can be complex with PagingData
                        // PagingDataAdapter's itemCount can be observed, along with LoadState.
                        // For simplicity, we might rely on SearchViewModel to manage a "no results for query X" state if needed.
                        screenState.messageResId?.let {
                            binding.textViewNoResults.text = getString(it)
                            binding.textViewNoResults.visibility = View.VISIBLE
                        } ?: run {
                            // Hide if PagingData will show items or its own empty/error state
                            // This logic needs refinement based on PagingData load states.
                            // binding.textViewNoResults.visibility = View.GONE 
                        }
                        // TODO: Add proper handling for PagingData load states (loading, error, empty)
                        // using searchAdapter.loadStateFlow
                    }
                }
            }
        }
    }

    // Parameter type changed to CleanedSearchResultItem
    override fun onItemClick(item: CleanedSearchResultItem) {
        L.d("Search item clicked: Title='${item.title}', ID='${item.id}'")
        (activity as? MainActivity)?.getRouter()?.navigateToArticle(
            articleId = item.id,
            articleTitle = item.title
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewSearchResults.adapter = null // Clear adapter
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
