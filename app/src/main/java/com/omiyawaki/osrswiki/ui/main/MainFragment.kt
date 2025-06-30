package com.omiyawaki.osrswiki.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchFragment

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val SAVED_PAGES_FRAGMENT_INDEX = 0
        const val SEARCH_FRAGMENT_INDEX = 1

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
        setupViewPager()
        if (savedInstanceState == null) {
            binding.mainViewPager.setCurrentItem(SEARCH_FRAGMENT_INDEX, false)
        }
    }

    fun navigateToSearchTab() {
        if (_binding != null) {
            if (binding.mainViewPager.currentItem != SEARCH_FRAGMENT_INDEX) {
                binding.mainViewPager.setCurrentItem(SEARCH_FRAGMENT_INDEX, false)
            }
        }
    }

    private fun setupViewPager() {
        binding.mainViewPager.adapter = ViewPagerAdapter(this)
        binding.mainViewPager.isUserInputEnabled = false
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()
        private val numTabs = 2

        override fun getItemCount(): Int = numTabs

        override fun createFragment(position: Int): Fragment {
            fragments[position]?.let { return it }
            val newFragment = when (position) {
                SAVED_PAGES_FRAGMENT_INDEX -> SavedPagesFragment()
                SEARCH_FRAGMENT_INDEX -> SearchFragment.newInstance()
                else -> throw IllegalStateException("Invalid position $position for ViewPager")
            }
            fragments[position] = newFragment
            return newFragment
        }
    }
}
