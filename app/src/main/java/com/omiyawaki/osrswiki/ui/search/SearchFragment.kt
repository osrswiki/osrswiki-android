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
import com.omiyawaki.osrswiki.network.SearchResultItem
import com.omiyawaki.osrswiki.ui.common.NavigationIconType // Added import
import com.omiyawaki.osrswiki.ui.common.ScreenConfiguration // Added import
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SearchFragment : Fragment(), ScreenConfiguration, SearchAdapter.OnItemClickListener { // Implements ScreenConfiguration

    private val viewModel: SearchViewModel by viewModels()
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

        // Request MainActivity to update toolbar based on this fragment's configuration
        (activity as? MainActivity)?.updateToolbar(this)
    }

    // Implement ScreenConfiguration
    override fun getToolbarTitle(getString: (id: Int) -> String): String {
        return getString(R.string.title_search) // Standard title for search screen
    }

    override fun getNavigationIconType(): NavigationIconType {
        return NavigationIconType.NONE // Search screen might not have a nav icon, or use MENU if drawer exists
    }

    override fun hasCustomOptionsMenu(): Boolean {
        return false // SearchFragment might not add items to the main options menu
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(this)
        binding.recyclerViewSearchResults.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    viewModel.searchArticles(it)
                    binding.searchView.clearFocus() // Hide keyboard
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Optional: Implement live search or suggestions if desired
                return false
            }
        })
        // Optional: Set focus to SearchView when fragment opens
        // binding.searchView.isIconified = false // Makes the search bar active
        // binding.searchView.requestFocus()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarSearch.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.textViewSearchError.visibility = if (state.error != null) View.VISIBLE else View.GONE
                    binding.textViewSearchError.text = state.error
                    searchAdapter.submitList(state.results)
                    if (state.results.isEmpty() && !state.isLoading && state.error == null && state.hasSearched) {
                        binding.textViewNoResults.visibility = View.VISIBLE
                    } else {
                        binding.textViewNoResults.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onItemClick(item: SearchResultItem) {
        Timber.d("Search item clicked: Title='${item.title}', ID='${item.pageid}'")
        // Corrected call to navigateToArticle
        (activity as? MainActivity)?.getRouter()?.navigateToArticle(
            articleId = item.pageid.toString(), // Assuming pageid can serve as articleId
            articleTitle = item.title
        )
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
