package com.omiyawaki.osrswiki.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.omiyawaki.osrswiki.settings.SettingsActivity
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.views.TabCountsView
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
    private lateinit var searchEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        searchEditText = view.findViewById(R.id.toolbar_search_edit_text)

        binding.searchFragmentToolbar.setNavigationOnClickListener {
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
            searchEditText.post {
                if (isAdded) {
                    searchEditText.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private fun setupSearchToolbar() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.performSearch(s.toString())
            }
        })

        view?.findViewById<TabCountsView>(R.id.toolbar_tab_counts_view)?.setOnClickListener {
            Toast.makeText(requireContext(), "Tab switcher clicked!", Toast.LENGTH_SHORT).show()
        }
        
        view?.findViewById<ImageView>(R.id.toolbar_overflow_menu_button)?.setOnClickListener { anchorView ->
            val popup = PopupMenu(requireContext(), anchorView)
            popup.menu.add(Menu.NONE, R.id.action_main_settings, Menu.NONE, getString(R.string.menu_title_settings))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_main_settings -> {
                        startActivity(SettingsActivity.newIntent(requireContext()))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
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
                if (searchEditText.text.isNotEmpty()) {
                    searchEditText.setText("")
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
