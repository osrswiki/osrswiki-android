package com.omiyawaki.osrswiki.search

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchResultsBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.page.preemptive.VisibleArticlePrewarmBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException

class SearchResultsFragment : Fragment(), SearchAdapter.OnItemClickListener {

    private val viewModel: SearchViewModel by viewModels {
        val application = requireActivity().application as OSRSWikiApp
        SearchViewModelFactory(application, application.currentNetworkStatus)
    }
    private var _binding: FragmentSearchResultsBinding? = null
    private val binding get() = _binding!!

    private lateinit var onlineSearchAdapter: SearchAdapter
    private lateinit var offlineSearchAdapter: OfflineSearchAdapter
    private var articlePrewarmBinder: VisibleArticlePrewarmBinder? = null
    private var pendingScrollToTopQuery: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViewAdapters()
        setupArticlePrewarm()
        observeViewModel()
    }

    fun search(query: String) {
        articlePrewarmBinder?.clear()
        pendingScrollToTopQuery = query.trim()
        viewModel.performSearch(query)
    }

    private fun setupRecyclerViewAdapters() {
        onlineSearchAdapter = SearchAdapter(this)
        offlineSearchAdapter = OfflineSearchAdapter(this)
        onlineSearchAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        offlineSearchAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewSearchResults.itemAnimator = null

        onlineSearchAdapter.addLoadStateListener { loadStates ->
            val refreshState = loadStates.refresh
            val currentQuery = viewModel.currentQuery.value?.trim()

            binding.textViewSearchError.isVisible = refreshState is LoadState.Error

            if (refreshState is LoadState.NotLoading) {
                val hasResults = onlineSearchAdapter.itemCount > 0
                binding.recyclerViewSearchResults.isVisible = hasResults
                binding.textViewNoResults.isVisible = !hasResults && !currentQuery.isNullOrBlank()

                if (!hasResults && !currentQuery.isNullOrBlank()) {
                    binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                }
                maybeAnchorSearchResultsToTop(currentQuery)
            } else if (refreshState is LoadState.Error) {
                val error = refreshState.error
                Log.e("SearchResults", "Search failed", error)
                val errorMessage = when (error) {
                    is IOException -> getString(R.string.search_error_network)
                    else -> getString(R.string.search_error_generic)
                }
                binding.textViewSearchError.text = errorMessage
            }
        }
    }

    private fun setupArticlePrewarm() {
        val app = requireActivity().application as OSRSWikiApp
        articlePrewarmBinder = VisibleArticlePrewarmBinder(
            recyclerView = binding.recyclerViewSearchResults,
            lifecycleOwner = viewLifecycleOwner,
            scope = viewLifecycleOwner.lifecycleScope,
            candidatesAt = { position, _ ->
                val item = when (binding.recyclerViewSearchResults.adapter) {
                    onlineSearchAdapter -> onlineSearchAdapter.peek(position)
                    offlineSearchAdapter -> offlineSearchAdapter.currentList.getOrNull(position)
                    else -> null
                }
                setOfNotNull(item?.let {
                    ArticlePrewarmRequest(pageId = it.id.toIntOrNull(), title = it.title)
                })
            },
            onDwell = app.pageAssetDownloader::prewarmArticle,
            observeEnvironmentChanges = app.pageAssetDownloader::addPrewarmEnvironmentListener
        )
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOnline.combine(viewModel.currentQuery) { isOnline, query ->
                        Pair(isOnline, query?.trim())
                    }.collectLatest { (isOnline, trimmedQuery) ->
                        if (isOnline) {
                            if (binding.recyclerViewSearchResults.adapter != onlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = onlineSearchAdapter
                                articlePrewarmBinder?.refresh()
                            }
                        } else {
                            if (binding.recyclerViewSearchResults.adapter != offlineSearchAdapter) {
                                binding.recyclerViewSearchResults.adapter = offlineSearchAdapter
                                articlePrewarmBinder?.refresh()
                            }
                        }
                        binding.textViewOfflineIndicator.isVisible = !isOnline
                    }
                }

                launch {
                    viewModel.onlineSearchResultsFlow.collectLatest { pagingData ->
                        if (binding.recyclerViewSearchResults.adapter == onlineSearchAdapter) {
                            // Update search query in adapter for title highlighting
                            onlineSearchAdapter.updateSearchQuery(viewModel.currentQuery.value)
                            onlineSearchAdapter.submitData(pagingData)
                            binding.recyclerViewSearchResults.post {
                                maybeAnchorSearchResultsToTop(viewModel.currentQuery.value?.trim())
                            }
                        }
                    }
                }

                launch {
                    viewModel.combinedOfflineResultsList.collectLatest { combinedOfflineList ->
                        if (binding.recyclerViewSearchResults.adapter == offlineSearchAdapter) {
                            // Update search query in offline adapter for title highlighting
                            offlineSearchAdapter.updateSearchQuery(viewModel.currentQuery.value)
                            // Reset RecyclerView position before submitting new data
                            // This ensures new search results are displayed from the top
                            (binding.recyclerViewSearchResults.layoutManager as? LinearLayoutManager)?.scrollToPosition(0)
                            offlineSearchAdapter.submitList(combinedOfflineList)
                            Log.d("ScrollFix", "Offline search: Reset position to 0 before submitting new data")
                            val currentQuery = viewModel.currentQuery.value?.trim()
                            val hasResults = combinedOfflineList.isNotEmpty()
                            binding.recyclerViewSearchResults.isVisible = hasResults
                            binding.textViewNoResults.isVisible = !hasResults && !currentQuery.isNullOrBlank()
                            if (!hasResults && !currentQuery.isNullOrBlank()) {
                                binding.textViewNoResults.text = getString(R.string.search_no_results_for_query, currentQuery)
                            }
                            binding.textViewSearchError.isVisible = false
                        }
                    }
                }
            }
        }
    }

    private fun maybeAnchorSearchResultsToTop(currentQuery: String?) {
        val expected = pendingScrollToTopQuery ?: return
        if (currentQuery != expected) return
        val layoutManager = binding.recyclerViewSearchResults.layoutManager as? LinearLayoutManager
            ?: return
        layoutManager.scrollToPositionWithOffset(0, 0)
        binding.recyclerViewSearchResults.scrollToPosition(0)
        pendingScrollToTopQuery = null
    }

    override fun onItemClick(item: CleanedSearchResultItem) {
        viewModel.saveCurrentQuery() // Save the query when an item is clicked.
        val intent = PageActivity.newIntent(
            context = requireContext(),
            pageTitle = item.title,
            pageId = item.id.toIntOrNull()?.toString(),
            source = HistoryEntry.SOURCE_SEARCH,
            snippet = item.snippet,
            thumbnailUrl = item.thumbnailUrl
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        articlePrewarmBinder?.dispose()
        articlePrewarmBinder = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SearchResultsFragment()
    }
}
