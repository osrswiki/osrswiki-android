package com.omiyawaki.osrswiki.ui.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu // Added for PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.ui.common.FragmentToolbarPolicyProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy
import com.omiyawaki.osrswiki.util.log.L // Assuming L is your logger

// Interface for MainActivity to implement
interface MainScrollableViewProvider {
    fun updateToolbarState(hostFragment: Fragment?, scrollableView: View?, policy: ToolbarPolicy)
}

// Interface for child fragments of ViewPager2 to implement (for scrollable view)
interface ScrollableContent {
    fun getScrollableView(): View?
}

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private var mainViewProvider: MainScrollableViewProvider? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainScrollableViewProvider) {
            mainViewProvider = context
        } else {
            throw RuntimeException("$context must implement MainScrollableViewProvider")
        }
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
        // --- MODIFICATION: Set initial ViewPager item to Search (position 1) ---
        if (savedInstanceState == null) { // Only set on initial creation
            binding.mainViewPager.setCurrentItem(1, false) // 1 is SearchFragment
            L.d("MainFragment: Set initial ViewPager item to 1 (SearchFragment)")
        }
        // --- END OF MODIFICATION ---
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        binding.mainViewPager.adapter = ViewPagerAdapter(this)
        binding.mainViewPager.isUserInputEnabled = false // Swiping disabled

        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Sync BottomNavigationView when ViewPager page changes
                if (position < binding.bottomNavView.menu.size()) { // Ensure position is valid
                    binding.bottomNavView.menu.getItem(position).isChecked = true
                }
                notifyActivityOfToolbarState()
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavView.setOnItemSelectedListener { item ->
            val previousItem = binding.mainViewPager.currentItem
            val newPosition = when (item.itemId) {
                R.id.nav_saved_bottom -> 0
                R.id.nav_search_bottom -> 1
                R.id.nav_more_bottom -> {
                    showMorePopupMenu(binding.bottomNavView.findViewById(R.id.nav_more_bottom))
                    -1 // Indicate no ViewPager change for "More"
                }
                else -> -1 // Should not happen
            }

            if (newPosition != -1) {
                if (binding.mainViewPager.currentItem != newPosition) {
                    binding.mainViewPager.setCurrentItem(newPosition, false)
                }
                true // Consume event, item selected
            } else {
                // "More" was clicked, or unknown item.
                // Re-check the previously selected item in BottomNavView if "More" was clicked.
                // This prevents "More" from staying visually selected.
                if (item.itemId == R.id.nav_more_bottom && previousItem < binding.bottomNavView.menu.size()) {
                    binding.bottomNavView.menu.getItem(previousItem).isChecked = true
                }
                false // Do not consume event for "More" to keep previous tab visually selected
            }
        }

        // Initial sync: Set BottomNav based on ViewPager's current item (which we set to 1 above)
        // This ensures the correct tab is highlighted on initial load.
        if (binding.mainViewPager.adapter?.itemCount ?: 0 > 0) {
            val currentVPItem = binding.mainViewPager.currentItem
            if (currentVPItem < binding.bottomNavView.menu.size()) {
                 binding.bottomNavView.menu.getItem(currentVPItem).isChecked = true
            }
            // Post the toolbar update to ensure layout is complete
            view?.post { notifyActivityOfToolbarState() }
        }
    }

    private fun showMorePopupMenu(anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.menu_main_more_popup, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_more_settings -> {
                    L.i("Settings clicked")
                    // TODO: Navigate to SettingsActivity
                    true
                }
                R.id.menu_more_donate -> {
                    L.i("Donate clicked")
                    // TODO: Show Donate dialog/flow
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun notifyActivityOfToolbarState() {
        val currentAdapter = binding.mainViewPager.adapter as? ViewPagerAdapter
        val currentPosition = binding.mainViewPager.currentItem
        val currentChildFragment = if (currentPosition < (currentAdapter?.itemCount ?: 0)) {
            currentAdapter?.getFragmentAt(currentPosition, childFragmentManager)
        } else {
            null
        }

        val scrollableView = if (currentChildFragment is ScrollableContent) {
            currentChildFragment.getScrollableView()
        } else {
            null
        }

        val policy = if (currentChildFragment is FragmentToolbarPolicyProvider) {
            currentChildFragment.getToolbarPolicy()
        } else {
            // Default policy if fragment doesn't provide one, or if fragment is null
            // (e.g. after "More" is clicked, currentChildFragment might not be what we expect for policy)
            // The policy should ideally come from the fragment truly displayed in the ViewPager.
            // Defaulting to HIDDEN for Saved/Search which are primary tabs if undetermined.
            if (currentPosition == 0 || currentPosition == 1) ToolbarPolicy.HIDDEN else ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT
        }
        mainViewProvider?.updateToolbarState(currentChildFragment, scrollableView, policy)
    }

    override fun onResume() {
        super.onResume()
        // It's good to update toolbar state on resume as well,
        // especially if returning from another activity.
        view?.post { notifyActivityOfToolbarState() }
    }

    override fun onDetach() {
        super.onDetach()
        mainViewProvider?.updateToolbarState(null, null, ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT)
        mainViewProvider = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()
        private val numTabs = 2 // Position 0: Saved, Position 1: Search

        override fun getItemCount(): Int = numTabs

        override fun createFragment(position: Int): Fragment {
            fragments[position]?.let { return it }

            val newFragment = when (position) {
                0 -> PlaceholderFragment.newInstance("Saved Tab")
                1 -> SearchFragment.newInstance()
                else -> throw IllegalStateException("Invalid position $position for ViewPager2. Max items: $numTabs")
            }
            fragments[position] = newFragment
            return newFragment
        }

        fun getFragmentAt(position: Int, fragmentManager: FragmentManager): Fragment? {
            // Attempt to find by tag first if FragmentManager has retained it.
            // This is more robust than relying solely on the 'fragments' map after process death.
            // However, for ViewPager2, direct access to created fragments is usually via the adapter's internal cache.
            // The 'fragments' map is for this adapter instance's lifetime.
            return fragments[position]
        }
    }

    // PlaceholderFragment for "Saved"
    class PlaceholderFragment : Fragment(), ScrollableContent, FragmentToolbarPolicyProvider {
        private var scrollableView: androidx.core.widget.NestedScrollView? = null
        private var placeholderText: String = "Placeholder"

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            placeholderText = arguments?.getString(ARG_TEXT) ?: "Placeholder"
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val context = requireContext()
            val scrollView = androidx.core.widget.NestedScrollView(context).also { this.scrollableView = it }
            val linearLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }

            for (i in 1..50) {
                val itemTextView = android.widget.TextView(context).apply {
                    text = "Item $i for $placeholderText"
                    textSize = 20f
                    setPadding(16,16,16,16)
                }
                linearLayout.addView(itemTextView)
            }
            scrollView.addView(linearLayout)
            return scrollView
        }

        override fun getScrollableView(): View? = scrollableView
        override fun getToolbarPolicy(): ToolbarPolicy = ToolbarPolicy.HIDDEN // Example policy

        companion object {
            private const val ARG_TEXT = "placeholder_text"
            fun newInstance(text: String): PlaceholderFragment {
                return PlaceholderFragment().apply {
                    arguments = Bundle().apply {
                        putString(ARG_TEXT, text)
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance() = MainFragment()
    }
}
