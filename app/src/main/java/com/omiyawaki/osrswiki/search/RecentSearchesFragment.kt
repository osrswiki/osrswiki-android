package com.omiyawaki.osrswiki.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentRecentSearchesBinding
import kotlinx.coroutines.launch

class RecentSearchesFragment : Fragment() {

    interface Callback {
        fun onRecentSearchClicked(query: String)
    }

    private var _binding: FragmentRecentSearchesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecentSearchesViewModel by viewModels {
        RecentSearchesViewModelFactory()
    }

    private var callback: Callback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (parentFragment is Callback) {
            callback = parentFragment as Callback
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecentSearchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recentSearchAdapter = RecentSearchAdapter { recentSearch ->
            callback?.onRecentSearchClicked(recentSearch.query)
        }

        binding.recyclerViewRecentSearches.adapter = recentSearchAdapter

        binding.buttonClearAll.setOnClickListener {
            showClearHistoryConfirmationDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentSearches.collect { searches ->
                    recentSearchAdapter.submitList(searches)
                    val hasSearches = searches.isNotEmpty()
                    binding.textViewEmptyState.isVisible = !hasSearches
                    binding.headerLayout.isVisible = hasSearches
                    binding.recyclerViewRecentSearches.isVisible = hasSearches
                }
            }
        }
    }

    private fun showClearHistoryConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_recent_searches_dialog_title)
            .setMessage(R.string.clear_recent_searches_dialog_message)
            .setNegativeButton(R.string.dialog_option_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.dialog_option_clear) { _, _ ->
                viewModel.onClearAllClicked()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    companion object {
        fun newInstance() = RecentSearchesFragment()
    }
}
