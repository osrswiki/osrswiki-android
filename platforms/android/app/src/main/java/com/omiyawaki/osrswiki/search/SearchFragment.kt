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
    private var recentSearchesFragment: RecentSearchesFragment? = null
    private var searchResultsFragment: SearchResultsFragment? = null

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
        
        setupSearchInput()
        setupOnBackPressed()
        showKeyboard()

        // Initialize child fragments when they become available
        initializeChildFragments()
    }
    
    private fun initializeChildFragments() {
        // Use a view tree observer to wait for fragments to be properly inflated
        view?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                
                // Try to get the child fragments
                recentSearchesFragment = childFragmentManager.findFragmentById(R.id.recent_searches_container) as? RecentSearchesFragment
                searchResultsFragment = childFragmentManager.findFragmentById(R.id.search_results_container) as? SearchResultsFragment
                
                if (recentSearchesFragment != null && searchResultsFragment != null) {
                    // Initial state: show recent searches, hide results
                    showPanel(isResultsPanel = false)
                } else {
                    // If fragments are still not ready, try again after a short delay
                    view?.postDelayed({ 
                        initializeChildFragments()
                    }, 100)
                }
            }
        })
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
            searchResultsFragment?.search(query)
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
        val recentFrag = recentSearchesFragment
        val resultsFrag = searchResultsFragment
        
        if (recentFrag != null && resultsFrag != null) {
            childFragmentManager.commit {
                if (isResultsPanel) {
                    show(resultsFrag)
                    hide(recentFrag)
                } else {
                    show(recentFrag)
                    hide(resultsFrag)
                }
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
