package com.omiyawaki.osrswiki.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
        L.d("SearchFragment: onCreateView.")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("SearchFragment: onViewCreated called.")

        // Setup this fragment's own toolbar's navigation
        binding.searchFragmentToolbar.setNavigationOnClickListener {
            L.d("SearchFragment: searchFragmentToolbar navigation icon clicked.")
            // This could pop the back stack or navigate to the previous ViewPager page in MainFragment
            // For now, relying on standard activity back press dispatcher behavior
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        setupRecyclerViewAdapters()
        setupSearchView()
        observeViewModel()
        setupOnBackPressed()

        L.d("SearchFragment: onViewCreated: searchFragmentToolbar.isVisible = ${binding.searchFragmentToolbar.isVisible}")
        L.d("SearchFragment: onViewCreated: searchView.isVisible = ${binding.searchView.isVisible}")
    }

    override fun onResume() {
        super.onResume()
        L.d("SearchFragment: onResume called.")
        if (_binding != null) {
            // Set this fragment's toolbar as the SupportActionBar
            val appCompatActivity = activity as? AppCompatActivity
            appCompatActivity?.setSupportActionBar(binding.searchFragmentToolbar)
            L.d("SearchFragment: Set searchFragmentToolbar as SupportActionBar.")

            // Set the title for this fragment's toolbar
            binding.searchFragmentToolbar.title = getString(R.string.title_search) // Use appropriate string resource
            L.d("SearchFragment: Toolbar title set to '${binding.searchFragmentToolbar.title}'.")


            // Request focus for the SearchView and show keyboard
            binding.searchView.post { // Post to ensure view is ready
                // Add a lifecycle check. During a theme-change-recreation, onResume can be
                // called, but the posted task may execute after onDestroyView, causing a crash.
                if (_binding != null && isAdded) {
                    val focusRequested = binding.searchView.requestFocus()
                    L.d("SearchFragment: onResume: searchView.requestFocus() returned: $focusRequested. searchView.isFocused(): ${binding.searchView.isFocused}")
                    // Consider explicitly showing the keyboard if requestFocus isn't enough
                    // val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    // imm.showSoftInput(binding.searchView.findFocus(), InputMethodManager.SHOW_IMPLICIT)
                }
            }

            L.d("SearchFragment: onResume: searchFragmentToolbar.isVisible = ${binding.searchFragmentToolbar.isVisible}")
            L.d("SearchFragment: onResume: searchView.isVisible = ${binding.searchView.isVisible}")
        }
    }

    // ScreenConfiguration interface methods
    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        // This method might not be actively used by MainActivity to set title for SearchFragment's toolbar anymore,
        // as SearchFragment now manages its own toolbar title directly in onResume.
        // However, keeping it for interface completeness or other potential uses.
        return getString(R.string.title_search)
    }

    override fun getNavigationIconType(): NavigationIconType {
        // This is also part of ScreenConfiguration. For searchFragmentToolbar,
        // navigation is handled by searchFragmentToolbar.setNavigationOnClickListener.
        // MainActivity won't be configuring this toolbar based on this type.
        return NavigationIconType.BACK // Or NONE if navigation icon is solely managed by searchFragmentToolbar
    }

    override fun hasCustomOptionsMenu(): Boolean {
        // SearchFragment's own toolbar does not inflate a separate menu resource.
        return false
    }

    private fun setupRecyclerViewAdapters() {
        onlineSearchAdapter = SearchAdapter(this)
        offlineSearchAdapter = OfflineSearchAdapter(this)
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(context)
        L.i("SearchFragment: RecyclerView adapters initialized.")
    }

    private fun setupSearchView() {
        L.d("SearchFragment: setupSearchView called.")
        binding.searchView.isIconified = false // Keep it expanded
        binding.searchView.queryHint = getString(R.string.search_hint_text)
        L.d("SearchFragment: setupSearchView: searchView.isIconified=${binding.searchView.isIconified}, queryHint set.")

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                L.d("SearchFragment: onQueryTextSubmit: $query")
                viewModel.performSearch(query?.trim() ?: "")
                binding.searchView.clearFocus() // Hide keyboard on submit
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
                                L.d("SearchFragment: Switched to OnlineSearchAdapter.")
                            }
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            }
                        } else { // Offline
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                                L.d("SearchFragment: Switched to OfflineSearchAdapter.")
                            }
                            if (trimmedQuery.isNullOrBlank()) {
                                updateUiForBlankQuery()
                            } else {
                                binding.progressBarSearch.isVisible = false // Offline search is quick
                            }
                        }
                    }
                }

                launch {
                    viewModel.onlineSearchResultsFlow.collectLatest { pagingData ->
                        if (binding.recyclerViewSearchResults.adapter == onlineSearchAdapter) {
                            L.d("SearchFragment: Submitting Online PagingData")
                            onlineSearchAdapter.submitData(pagingData)
                        }
                    }
                }

                launch {
                    viewModel.combinedOfflineResultsList.collectLatest { combinedOfflineList ->
                        if (binding.recyclerViewSearchResults.adapter == offlineSearchAdapter) {
                            L.d("SearchFragment: Submitting Combined Offline List: ${combinedOfflineList.size} items")
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
                        // Initial state assumption for recycler/noResults/error:
                        binding.recyclerViewSearchResults.isVisible = false
                        binding.textViewNoResults.isVisible = false
                        binding.textViewSearchError.isVisible = false

                        if (isLoading) {
                            // ProgressBar is visible, other views are hidden
                        } else if (isError) {
                            val errorMessage = when (error) {
                                is IOException -> getString(R.string.search_error_network)
                                else -> error?.localizedMessage ?: getString(R.string.search_error_generic)
                            }
                            binding.textViewSearchError.text = errorMessage
                            binding.textViewSearchError.isVisible = true
                        } else { // Not loading, not error -> check adapter item count
                            if (onlineSearchAdapter.itemCount > 0) {
                                binding.recyclerViewSearchResults.isVisible = true
                            } else { // No items from adapter
                                if (!currentQuery.isNullOrBlank()) { // And query was not blank
                                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                                    binding.textViewNoResults.isVisible = true
                                } else { // Query was blank
                                    updateUiForBlankQuery() // Show "enter query" prompt
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
                    binding.searchView.setQuery("", false) // Clear query first
                } else {
                    isEnabled = false // Disable this callback to allow default navigation
                    try {
                        // Standard way to trigger back press for fragment/activity
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    } finally {
                        // Re-enable only if still added to prevent issues if fragment is quickly re-added
                        if (isAdded) { isEnabled = true }
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        L.d("SearchFragment onItemClick: Item Title='${item.title}', Item ID='${item.id}', IsFts=${item.isFtsResult}, Source=SOURCE_SEARCH")
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
        L.d("SearchFragment: onDestroyView.")
        _binding = null
    }

    // ScrollableContent interface method
    override fun getScrollableView(): View? {
        return _binding?.recyclerViewSearchResults
    }

    // FragmentToolbarPolicyProvider interface method
    override fun getToolbarPolicy(): ToolbarPolicy {
        L.d("SearchFragment: getToolbarPolicy called, returning HIDDEN.")
        // This tells MainFragment to hide MainActivity's main_toolbar when SearchFragment is active
        return ToolbarPolicy.HIDDEN
    }

    companion object {
        @JvmStatic
        fun newInstance() = SearchFragment()
    }
}
