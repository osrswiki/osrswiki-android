package com.omiyawaki.osrswiki.search

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException

class SearchFragment : Fragment(),
    SearchAdapter.OnItemClickListener {

    private val viewModel: SearchViewModel by viewModels {
        val application = requireActivity().application as OSRSWikiApp
        SearchViewModelFactory(application, application.currentNetworkStatus)
    }
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var onlineSearchAdapter: SearchAdapter
    private lateinit var offlineSearchAdapter: OfflineSearchAdapter
    private lateinit var searchView: SearchView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchView = view.findViewById(R.id.search_view)

        binding.searchFragmentToolbar.setNavigationOnClickListener {
            hideSoftKeyboard()
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        setupRecyclerViewAdapters()
        setupSearchToolbar()
        observeViewModel()
        setupOnBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            searchView.post {
                if (isAdded) {
                    searchView.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchView.findViewById(androidx.appcompat.R.id.search_src_text), InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private fun hideSoftKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun setupSearchToolbar() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                hideSoftKeyboard()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.performSearch(newText.orEmpty())
                return true
            }
        })

        // Style the internal EditText of the SearchView to use theme attributes for color
        val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        val typedValue = TypedValue()
        
        // Use colorOnSurface for the main text color for good contrast
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        searchEditText.setTextColor(typedValue.data)
        
        // Use the standard hint text color for the hint
        requireContext().theme.resolveAttribute(android.R.attr.textColorHint, typedValue, true)
        searchEditText.setHintTextColor(typedValue.data)
    }

    private fun setupRecyclerViewAdapters() {
        onlineSearchAdapter = SearchAdapter(this)
        offlineSearchAdapter = OfflineSearchAdapter(this)
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(context)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOnline.combine(viewModel.currentQuery) { isOnline, query ->
                        Pair(isOnline, query?.trim())
                    }.collectLatest { (isOnline, trimmedQuery) ->
                        if (!isOnline && !trimmedQuery.isNullOrBlank()) {
                            binding.textViewOfflineIndicator.text = getString(R.string.offline_search_active_message)
                            binding.textViewOfflineIndicator.isVisible = true
                        } else if (!isOnline && trimmedQuery.isNullOrBlank()) {
                            binding.textViewOfflineIndicator.text = getString(R.string.offline_indicator_generic)
                            binding.textViewOfflineIndicator.isVisible = true
                        } else {
                            binding.textViewOfflineIndicator.isVisible = false
                        }

                        if (isOnline) {
                            if (binding.recyclerViewSearchResults.adapter != onlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = onlineSearchAdapter
                            }
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            }
                        } else { // Offline
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                            }
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            } else {
                                binding.progressBarSearch.isVisible = false
                            }
                        }
                    }
                }

                launch {
                    viewModel.onlineSearchResultsFlow.collectLatest { pagingData ->
                        if (binding.recyclerViewSearchResults.adapter == onlineSearchAdapter) {
                            onlineSearchAdapter.submitData(pagingData)
                        }
                    }
                }

                launch {
                    viewModel.combinedOfflineResultsList.collectLatest { combinedOfflineList ->
                        if (binding.recyclerViewSearchResults.adapter == offlineSearchAdapter) {
                            offlineSearchAdapter.submitList(combinedOfflineList)
                            val currentQuery = viewModel.currentQuery.value?.trim()
                            if (!currentQuery.isNullOrBlank()) {
                                binding.recyclerViewSearchResults.isVisible = combinedOfflineList.isNotEmpty()
                                binding.textViewNoResults.isVisible = combinedOfflineList.isEmpty()
                                if (combinedOfflineList.isEmpty()) {
                                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                                }
                            } else { // Blank query, offline
                                updateUiForBlankQuery()
                            }
                            binding.progressBarSearch.isVisible = false
                            binding.textViewSearchError.isVisible = false
                        }
                    }
                }

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
                        } else if (isError) {
                            val errorMessage = when (error) {
                                is IOException -> getString(R.string.search_error_network)
                                else -> error?.localizedMessage ?: getString(R.string.search_error_generic)
                            }
                            binding.textViewSearchError.text = errorMessage
                            binding.textViewSearchError.isVisible = true
                        } else {
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
                if (searchView.query.isNotEmpty()) {
                    searchView.setQuery("", false)
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
        hideSoftKeyboard()
        val intent = PageActivity.newIntent(
            context = requireContext(),
            pageTitle = item.title,
            pageId = item.id,
            source = HistoryEntry.SOURCE_SEARCH
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchView.setOnQueryTextListener(null)
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
