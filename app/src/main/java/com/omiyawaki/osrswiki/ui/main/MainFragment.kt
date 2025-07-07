package com.omiyawaki.osrswiki.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.news.ui.NewsFragment
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.ui.map.MapFragment

/**
 * The main fragment that hosts the BottomNavigationView and acts as a container
 * for the primary destination fragments like News, Saved Pages, etc.
 */
class MainFragment : Fragment() {

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
        setupBottomNav()

        // Load the default fragment if this is the first creation
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_news
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_news -> NewsFragment()
                R.id.nav_saved -> SavedPagesFragment()
                R.id.nav_map -> MapFragment()
                // TODO: Add cases for Search and More
                else -> null // Or a default fragment
            }
            if (fragment != null) {
                loadFragment(fragment)
                true
            } else {
                false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
