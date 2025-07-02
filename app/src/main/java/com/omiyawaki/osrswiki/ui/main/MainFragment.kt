package com.omiyawaki.osrswiki.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.search.SearchActivity

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
        setupBottomNav()
    }

    private fun setupRecyclerView() {
        binding.mainFeedRecycler.adapter = MainFeedAdapter(this)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val message = when (item.itemId) {
                R.id.nav_news -> "News clicked"
                R.id.nav_map -> "Map clicked"
                R.id.nav_saved -> "Saved clicked"
                R.id.nav_search -> "Search clicked"
                R.id.nav_more -> "More clicked"
                else -> "Unknown item clicked"
            }
            // Placeholder action. In the future, this will navigate to different fragments.
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            true // Return true to display the item as the selected item
        }
    }

    override fun onSearchRequested(view: View) {
        val intent = SearchActivity.newIntent(requireActivity())
        val options = android.app.ActivityOptions.makeSceneTransitionAnimation(requireActivity(), view, getString(R.string.transition_search_bar))
        startActivity(intent, options.toBundle())
    }

    override fun onVoiceSearchRequested() {
        // Placeholder action to confirm the voice search button is wired up correctly.
        Toast.makeText(requireContext(), "Voice search clicked (not implemented)", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding.mainFeedRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
