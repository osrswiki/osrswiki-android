package com.omiyawaki.osrswiki.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.textfield.TextInputEditText
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSearchBinding

class SearchFragment : Fragment(), RecentSearchesFragment.Callback {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchEditText: TextInputEditText
    private lateinit var recentSearchesFragment: RecentSearchesFragment
    private lateinit var searchResultsFragment: SearchResultsFragment

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
        recentSearchesFragment =
            childFragmentManager.findFragmentById(R.id.recent_searches_container) as RecentSearchesFragment
        searchResultsFragment =
            childFragmentManager.findFragmentById(R.id.search_results_container) as SearchResultsFragment

        setupSearchInput()
        setupOnBackPressed()
        showKeyboard()

        // Initial state: show recent searches, hide results
        showPanel(isResultsPanel = false)
    }

    private fun showKeyboard() {
        searchEditText.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSoftKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun setupSearchInput() {
        searchEditText.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            showPanel(isResultsPanel = query.isNotEmpty())
            searchResultsFragment.search(query)
        }

        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSoftKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun showPanel(isResultsPanel: Boolean) {
        childFragmentManager.commit {
            if (isResultsPanel) {
                show(searchResultsFragment)
                hide(recentSearchesFragment)
            } else {
                show(recentSearchesFragment)
                hide(searchResultsFragment)
            }
        }
    }

    private fun setupOnBackPressed() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (searchEditText.text.toString().isNotEmpty()) {
                    searchEditText.setText("")
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    override fun onRecentSearchClicked(query: String) {
        searchEditText.setText(query)
        searchEditText.setSelection(query.length)
        hideSoftKeyboard()
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
