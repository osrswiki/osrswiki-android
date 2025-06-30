package com.omiyawaki.osrswiki.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.search.SearchFragment

class MainFragment : Fragment(), MainFeedAdapter.Callback {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = MainFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        binding.mainFeedRecycler.adapter = MainFeedAdapter(this)
    }

    override fun onSearchRequested() {
        // Replace the current MainFragment with the SearchFragment.
        // This is the correct navigation pattern for this action.
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, SearchFragment.newInstance())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        binding.mainFeedRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}