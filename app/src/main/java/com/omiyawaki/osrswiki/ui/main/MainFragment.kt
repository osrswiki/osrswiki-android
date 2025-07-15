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
 * This fragment implements a state-retaining hide/show navigation strategy
 * to prevent fragments from being destroyed and recreated on tab switches.
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    // Hold fragment instances to ensure they are not recreated.
    private val newsFragment: NewsFragment by lazy { NewsFragment() }
    private val savedPagesFragment: SavedPagesFragment by lazy { SavedPagesFragment() }
    private val mapFragment: MapFragment by lazy { MapFragment() }
    private lateinit var activeFragment: Fragment

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

        if (savedInstanceState == null) {
            // Initially load the NewsFragment.
            activeFragment = newsFragment
            childFragmentManager.beginTransaction()
                .add(R.id.main_fragment_container, newsFragment, NewsFragment::class.java.simpleName)
                .commit()
            binding.bottomNav.selectedItemId = R.id.nav_news
        }
        // FragmentManager will restore the state on configuration changes.
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_news -> newsFragment
                R.id.nav_saved -> savedPagesFragment
                R.id.nav_map -> mapFragment
                else -> null
            }

            if (selectedFragment != null && selectedFragment !== activeFragment) {
                switchFragment(selectedFragment)
                true
            } else {
                false
            }
        }
    }

    private fun switchFragment(fragment: Fragment) {
        val transaction = childFragmentManager.beginTransaction()
        // Hide the currently active fragment.
        transaction.hide(activeFragment)

        val fragmentTag = fragment::class.java.simpleName
        if (childFragmentManager.findFragmentByTag(fragmentTag) == null) {
            // If the fragment has not been added yet, add it.
            transaction.add(R.id.main_fragment_container, fragment, fragmentTag)
        } else {
            // If it already exists, simply show it.
            transaction.show(fragment)
        }

        activeFragment = fragment
        transaction.commit()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
