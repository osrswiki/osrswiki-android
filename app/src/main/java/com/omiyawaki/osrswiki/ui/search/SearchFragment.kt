package com.omiyawaki.osrswiki.ui.search

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
import androidx.paging.LoadState // Import LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R // For string resources
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding
import kotlinx.coroutines.flow.collectLatest // Import collectLatest
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels { SearchViewModelFactory(requireActivity().application) }
    private lateinit var searchResultAdapter: SearchResultAdapter

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
        setupSearchInput()
        observeSearchResults() // New: Observe PagingData
        observeLoadStates()    // New: Observe PagingAdapter LoadStates
        observeScreenMessages()// New: Observe general screen messages from ViewModel
    }

    private fun setupRecyclerView() {
        searchResultAdapter = SearchResultAdapter { cleanedSearchResultItem ->
            // Handle item click - Navigate to ArticleFragment
            Log.d("SearchFragment", "Clicked item ID: ${cleanedSearchResultItem.id}, Title: ${cleanedSearchResultItem.title}")
            val action = SearchFragmentDirections.actionSearchFragmentToArticleFragment(cleanedSearchResultItem.id)
            findNavController().navigate(action)
        }
        binding.searchResultsRecyclerview.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchResultAdapter
            // Optional: Add item decorations or animations here
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
        // Consider adding a text changed listener for live search with debounce if desired,
        // but EditorInfo.IME_ACTION_SEARCH is fine for MVP.
        // viewModel.performSearch is already debounced in ViewModel.
    }

    private fun performSearch() {
        val query = binding.searchEditText.text.toString().trim()
        viewModel.performSearch(query) // This updates the query in ViewModel, triggering the flow
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
    }

    private fun observeSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResultsFlow.collectLatest { pagingData ->
                    Log.d("SearchFragment", "Submitting new PagingData to adapter.")
                    searchResultAdapter.submitData(pagingData)
                }
            }
        }
    }

    private fun observeLoadStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchResultAdapter.loadStateFlow.collectLatest { loadStates ->
                    // Handle refresh state (initial load or swipe-to-refresh)
                    when (val refreshState = loadStates.refresh) {
                        is LoadState.Loading -> {
                            Log.d("SearchFragment", "LoadState: Refresh Loading")
                            binding.root.post {
                                if (_binding != null) {
                                    binding.searchProgressBar.isVisible = true
                                    binding.searchMessageTextview.isVisible = false
                                    binding.searchResultsRecyclerview.isVisible = false
                                }
                            }
                        }
                        is LoadState.NotLoading -> {
                            Log.d("SearchFragment", "LoadState: Refresh NotLoading. Item count: ${searchResultAdapter.itemCount}")
                            binding.root.post {
                                if (_binding != null) {
                                    binding.searchProgressBar.isVisible = false
                                    val currentQuery = viewModel.screenUiState.value.currentQuery
                                    if (searchResultAdapter.itemCount == 0 && !currentQuery.isNullOrBlank()) {
                                        // Active query, but no results
                                        binding.searchMessageTextview.text = getString(R.string.search_no_results)
                                        binding.searchMessageTextview.isVisible = true
                                        binding.searchResultsRecyclerview.isVisible = false
                                    } else if (searchResultAdapter.itemCount > 0) {
                                        // Results found
                                        binding.searchMessageTextview.isVisible = false
                                        binding.searchResultsRecyclerview.isVisible = true
                                    } else {
                                        // No items, and query is blank (implicitly, due to previous conditions)
                                        // Let observeScreenMessages handle the "enter query" prompt for searchMessageTextview.
                                        // Ensure RecyclerView is hidden.
                                        binding.searchResultsRecyclerview.isVisible = false
                                        // searchMessageTextview visibility for blank query state will be set by observeScreenMessages
                                    }
                                }
                            }
                        }
                        is LoadState.Error -> {
                            Log.e("SearchFragment", "LoadState: Refresh Error - ${refreshState.error.localizedMessage}", refreshState.error)
                            binding.root.post {
                                if (_binding != null) {
                                    binding.searchProgressBar.isVisible = false
                                    binding.searchMessageTextview.text = getString(R.string.search_network_error) // Or more specific from refreshState.error
                                    binding.searchMessageTextview.isVisible = true
                                    binding.searchResultsRecyclerview.isVisible = false
                                    // TODO: Optionally, add a retry button that calls adapter.retry()
                                }
                            }
                        }
                    }

                    // Optionally, handle append/prepend states for loading more items indicators
                    // binding.appendProgressBar.isVisible = loadStates.append is LoadState.Loading
                    // if (loadStates.append is LoadState.Error) { /* show retry for append */ }
                }
            }
        }
    }

    private fun observeScreenMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.screenUiState.collectLatest { screenState ->
                    if (screenState.messageResId != null) {
                        // This message (e.g., "Enter query") should typically be shown when the list is not trying to load or show its own state.
                        // The LoadState handling might override this if it also wants to use searchMessageTextview.
                        // Let LoadState handle "no results" or "error" if a search was attempted.
                        // This is mainly for the initial "enter query" prompt when query is blank.
                        if (screenState.currentQuery.isNullOrBlank()) {
                             binding.searchMessageTextview.text = getString(screenState.messageResId)
                             binding.searchMessageTextview.isVisible = true
                             binding.searchResultsRecyclerview.isVisible = false
                             binding.searchProgressBar.isVisible = false // Ensure progress bar is hidden for initial prompt
                        }
                    } else {
                        // If no specific screen message, and LoadState isn't showing one, hide it.
                        // This case might be covered by LoadState.NotLoading with items > 0.
                        if (searchResultAdapter.itemCount > 0) { // Only hide if we have results
                           binding.searchMessageTextview.isVisible = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.searchResultsRecyclerview.adapter = null // Important to clear adapter to prevent memory leaks
        _binding = null
    }
}
