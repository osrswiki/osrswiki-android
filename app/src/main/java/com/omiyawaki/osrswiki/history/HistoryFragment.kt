package com.omiyawaki.osrswiki.history

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentHistoryBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.page.PageActivity

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private var actionMode: ActionMode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { historyEntry ->
                navigateToPage(historyEntry)
            },
            onItemDelete = { historyEntry ->
                viewModel.deleteHistoryItem(historyEntry)
            },
            onSearchClick = {
                startSearchMode()
            },
            onClearHistoryClick = {
                viewModel.clearHistory()
            }
        )
        
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.historyItems.observe(viewLifecycleOwner) { historyList ->
            adapter.submitList(historyList)
            updateEmptyState(historyList)
        }
    }

    private fun updateEmptyState(historyList: List<HistoryItem>) {
        // Show empty state only if there are no entries (excluding search card)
        val hasEntries = historyList.any { it is HistoryItem.EntryItem }
        binding.historyRecyclerView.visibility = if (hasEntries) View.VISIBLE else View.GONE
        binding.emptyStateContainer.visibility = if (hasEntries) View.GONE else View.VISIBLE
    }
    
    private fun startSearchMode() {
        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity)
                .startSupportActionMode(createSearchActionModeCallback())
        }
    }
    
    private fun createSearchActionModeCallback(): SearchActionModeCallback {
        return SearchActionModeCallback(
            context = requireContext(),
            onQueryChange = { query ->
                viewModel.searchHistory(query)
            },
            onActionModeFinish = {
                actionMode = null
                viewModel.searchHistory("") // Reset to show all items
            }
        )
    }

    private fun navigateToPage(historyEntry: HistoryEntry) {
        val intent = Intent(requireContext(), PageActivity::class.java).apply {
            putExtra(PageActivity.EXTRA_PAGE_TITLE, historyEntry.pageTitle)
            putExtra(PageActivity.EXTRA_PAGE_SOURCE, HistoryEntry.SOURCE_HISTORY)
        }
        startActivity(intent)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HistoryFragment()
    }
}