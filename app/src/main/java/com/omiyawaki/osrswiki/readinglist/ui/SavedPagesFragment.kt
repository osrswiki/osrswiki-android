package com.omiyawaki.osrswiki.readinglist.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider // Added for manual ViewModel instantiation
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.database.AppDatabase // For accessing DAO
import com.omiyawaki.osrswiki.databinding.FragmentSavedPagesBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry // Added import
import com.omiyawaki.osrswiki.page.PageFragment
import com.omiyawaki.osrswiki.page.PageTitle
import com.omiyawaki.osrswiki.readinglist.adapter.SavedPagesAdapter
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.readinglist.repository.SavedPagesRepository // For manual Repository instantiation
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModel
import com.omiyawaki.osrswiki.readinglist.viewmodel.SavedPagesViewModelFactory // For ViewModel factory
// import dagger.hilt.android.AndroidEntryPoint // Removed
import kotlinx.coroutines.launch

// @AndroidEntryPoint // Removed
class SavedPagesFragment : Fragment() {

    private var _binding: FragmentSavedPagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavedPagesViewModel by viewModels {
        // Manually create the ViewModel using the factory
        val readingListPageDao = AppDatabase.instance.readingListPageDao()
        val repository = SavedPagesRepository(readingListPageDao)
        SavedPagesViewModelFactory(repository)
    }

    private lateinit var savedPagesAdapter: SavedPagesAdapter

    interface NavigationProvider {
        fun displayPageFragment(pageApiTitle: String?, pageNumericId: String?, source: Int) // Added source parameter
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedPagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeSavedPages()
    }

    private fun setupRecyclerView() {
        savedPagesAdapter = SavedPagesAdapter { readingListPage ->
            navigateToPage(readingListPage)
        }
        binding.savedPagesRecyclerView.apply {
            adapter = savedPagesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeSavedPages() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedPages.collect { pages ->
                    savedPagesAdapter.submitList(pages)
                    binding.emptyStateTextView.isVisible = pages.isEmpty()
                    binding.savedPagesRecyclerView.isVisible = pages.isNotEmpty()
                }
            }
        }
    }

    private fun navigateToPage(savedPage: ReadingListPage) {
        val pageApiTitleForFragmentArg = savedPage.apiTitle
        val pageNumericIdForFragmentArg: String? = null // Assuming pageId is not used or derived from apiTitle for saved pages

        Log.d("SavedPagesFragment", "Requesting navigation to PageFragment with apiTitle: '$pageApiTitleForFragmentArg', numericId: '$pageNumericIdForFragmentArg', source: SOURCE_SAVED_PAGE")

        val host = parentFragment as? NavigationProvider ?: activity as? NavigationProvider
        if (host != null) {
            // Pass the specific source for navigation from saved pages
            host.displayPageFragment(
                pageApiTitleForFragmentArg,
                pageNumericIdForFragmentArg,
                HistoryEntry.SOURCE_SAVED_PAGE
            )
        } else {
            Log.e("SavedPagesFragment", "Host (Activity or ParentFragment) must implement NavigationProvider.")
            Toast.makeText(requireContext(), "Navigation not set up. Could not open: $pageApiTitleForFragmentArg", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.savedPagesRecyclerView.adapter = null // Important to prevent memory leaks with RecyclerView adapter
        _binding = null
    }
}