package com.omiyawaki.osrswiki.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
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

    private lateinit var searchEditText: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchEditText = (requireActivity() as SearchActivity).binding.searchEditText

        setupRecyclerViewAdapters()
        setupSearchInput()
        observeViewModel()
        setupOnBackPressed()
        showKeyboard()
    }

    private fun showKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSoftKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun setupSearchInput() {
        searchEditText.doOnTextChanged { text, _, _, _ ->
            viewModel.performSearch(text?.toString().orEmpty())
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSoftKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun setupRecyclerViewAdapters() {
        onlineSearchAdapter = SearchAdapter(this)
        offlineSearchAdapter = OfflineSearchAdapter(this)
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(context)

        // Use a more reliable listener to trigger the preemptive load.
        onlineSearchAdapter.addLoadStateListener { loadStates ->
            val refreshState = loadStates.refresh
            val currentQuery = viewModel.currentQuery.value?.trim()

            binding.progressBarSearch.isVisible = refreshState is LoadState.Loading
            binding.textViewSearchError.isVisible = refreshState is LoadState.Error

            if (refreshState is LoadState.NotLoading) {
                val hasResults = onlineSearchAdapter.itemCount > 0
                binding.recyclerViewSearchResults.isVisible = hasResults
                binding.textViewNoResults.isVisible = !hasResults

                if (hasResults) {
                    // Trigger for preemptive load
                    onlineSearchAdapter.peek(0)?.let { topResult ->
                        viewModel.preemptivelyLoadTopResult(topResult)
                    }
                } else if (!currentQuery.isNullOrBlank()) {
                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                } else {
                    updateUiForBlankQuery()
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
                        // Simplified logic to handle adapter switching
                        if (isOnline) {
                            if (binding.recyclerViewSearchResults.adapter != onlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = onlineSearchAdapter
                            }
                        } else {
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                            }
                        }
                        
                        // Handle offline indicator visibility
                        binding.textViewOfflineIndicator.isVisible = !isOnline
                        if(!isOnline) {
                            binding.textViewOfflineIndicator.text = if (!trimmedQuery.isNullOrBlank()) {
                                getString(R.string.offline_search_active_message)
                            } else {
                                getString(R.string.offline_indicator_generic)
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
                                    binding.textViewNoResults.text =
                                        getString(R.string.search_no_results_for_query, currentQuery)
                                }
                            } else {
                                updateUiForBlankQuery()
                            }
                            binding.progressBarSearch.isVisible = false
                            binding.textViewSearchError.isVisible = false
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
                if (!searchEditText.text.isNullOrEmpty()) {
                    searchEditText.setText("")
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
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
