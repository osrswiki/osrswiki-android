package com.omiyawaki.osrswiki.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.util.log.L

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
        setupBottomNavigation()
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
        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position < binding.bottomNavView.menu.size()) {
                    binding.bottomNavView.menu.getItem(position).isChecked = true
                }
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavView.setOnItemSelectedListener { item ->
            val previousItemIndex = binding.mainViewPager.currentItem
            val newPosition = when (item.itemId) {
                R.id.nav_saved_bottom -> SAVED_PAGES_FRAGMENT_INDEX
                R.id.nav_search_bottom -> SEARCH_FRAGMENT_INDEX
                R.id.nav_more_bottom -> {
                    showMorePopupMenu(binding.bottomNavView.findViewById(R.id.nav_more_bottom))
                    -1
                }
                else -> -1
            }

            if (newPosition != -1) {
                if (binding.mainViewPager.currentItem != newPosition) {
                    binding.mainViewPager.setCurrentItem(newPosition, false)
                }
                true
            } else {
                if (item.itemId == R.id.nav_more_bottom && previousItemIndex < binding.bottomNavView.menu.size()) {
                    if (previousItemIndex >= 0) {
                        binding.bottomNavView.menu.getItem(previousItemIndex).isChecked = true
                    }
                }
                false
            }
        }
        if (binding.mainViewPager.adapter?.itemCount ?: 0 > 0) {
            val currentVPItem = binding.mainViewPager.currentItem
            if (currentVPItem < binding.bottomNavView.menu.size()) {
                binding.bottomNavView.menu.getItem(currentVPItem).isChecked = true
            }
        }
    }

    private fun showMorePopupMenu(anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_main_more_popup, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_more_settings -> { L.i("Settings clicked"); true }
                R.id.menu_more_donate -> { L.i("Donate clicked"); true }
                else -> false
            }
        }
        popup.show()
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
