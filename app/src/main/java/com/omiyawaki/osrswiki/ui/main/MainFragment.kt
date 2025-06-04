package com.omiyawaki.osrswiki.ui.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.omiyawaki.osrswiki.MainActivity
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding
import com.omiyawaki.osrswiki.readinglist.ui.SavedPagesFragment
import com.omiyawaki.osrswiki.search.SearchFragment
import com.omiyawaki.osrswiki.ui.common.FragmentToolbarPolicyProvider
import com.omiyawaki.osrswiki.ui.common.ToolbarPolicy
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.views.TabCountsView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface MainScrollableViewProvider {
    fun updateToolbarState(hostFragment: Fragment?, scrollableView: View?, policy: ToolbarPolicy)
}

interface ScrollableContent {
    fun getScrollableView(): View?
}

class MainFragment : Fragment(), MenuProvider {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    private var mainViewProvider: MainScrollableViewProvider? = null
    private var tabCountsViewInstance: TabCountsView? = null
    private var showTabCountsAnimation = false

    // ViewPager page indices
    companion object { // Removed 'private' modifier here
        const val SAVED_PAGES_FRAGMENT_INDEX = 0
        const val SEARCH_FRAGMENT_INDEX = 1
        const val MORE_FRAGMENT_PLACEHOLDER_INDEX = 2 // Or handle 'More' differently if not a ViewPager page

        fun newInstance() = MainFragment()
    }

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
        L.d("MainFragment: MenuProvider: onViewCreated: Adding MenuProvider.")
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        L.d("MainFragment: MenuProvider: onViewCreated: MenuProvider added.")
        setupViewPager()
        if (savedInstanceState == null) {
            // Default to Search tab
            binding.mainViewPager.setCurrentItem(SEARCH_FRAGMENT_INDEX, false)
            L.d("MainFragment: Set initial ViewPager item to $SEARCH_FRAGMENT_INDEX (SearchFragment)")
        }
        setupBottomNavigation()

        // Observe tab count changes
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                OSRSWikiApp.instance.tabCountFlow.collectLatest { count ->
                    L.d("Tab count changed: $count, invalidating options menu.")
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setupViewPager() {
        binding.mainViewPager.adapter = ViewPagerAdapter(this)
        binding.mainViewPager.isUserInputEnabled = false // Typically false if driven by bottom nav
        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position < binding.bottomNavView.menu.size()) {
                    binding.bottomNavView.menu.getItem(position).isChecked = true
                }
                L.d("MainFragment: ViewPager page selected: $position. Notifying activity of toolbar state.")
                notifyActivityOfToolbarState()
                // Invalidate options menu to ensure the correct menu items are shown/hidden
                // and MainActivity's toolbar (if active) gets repopulated by this MenuProvider.
                L.d("MainFragment: ViewPager page selected: $position. Invalidating options menu.")
                requireActivity().invalidateOptionsMenu()
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
                    -1 // Indicate no ViewPager change for 'More'
                }
                else -> -1
            }

            if (newPosition != -1) {
                if (binding.mainViewPager.currentItem != newPosition) {
                    binding.mainViewPager.setCurrentItem(newPosition, false)
                }
                true
            } else {
                // Re-select the previous item if 'More' was clicked, to keep bottom nav visually consistent
                if (item.itemId == R.id.nav_more_bottom && previousItemIndex < binding.bottomNavView.menu.size()) {
                     // Check if previousItemIndex is valid before trying to access menu item
                    if (previousItemIndex >= 0) { // Ensure previousItemIndex is not negative
                        binding.bottomNavView.menu.getItem(previousItemIndex).isChecked = true
                    }
                }
                false
            }
        }
        // Ensure initial bottom nav state matches ViewPager's current item
        if (binding.mainViewPager.adapter?.itemCount ?: 0 > 0) {
            val currentVPItem = binding.mainViewPager.currentItem
            if (currentVPItem < binding.bottomNavView.menu.size()) {
                binding.bottomNavView.menu.getItem(currentVPItem).isChecked = true
            }
            // Initial toolbar state notification post layout
            view?.post { notifyActivityOfToolbarState() }
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

    fun notifyActivityOfToolbarState() {
        val currentAdapter = binding.mainViewPager.adapter as? ViewPagerAdapter
        val currentPosition = binding.mainViewPager.currentItem
        val currentChildFragment = if (currentPosition < (currentAdapter?.itemCount ?: 0)) {
            currentAdapter?.getFragmentAt(currentPosition, childFragmentManager)
        } else {
            null
        }
        val scrollableViewForChildren = (currentChildFragment as? ScrollableContent)?.getScrollableView()

        // Determine the toolbar policy based on the current child fragment
        val childFragmentPolicy = (currentChildFragment as? FragmentToolbarPolicyProvider)?.getToolbarPolicy()
            ?: ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT // Default policy if child doesn't specify

        L.d("MainFragment: notifyActivityOfToolbarState: Child is ${currentChildFragment?.javaClass?.simpleName}, requested policy: $childFragmentPolicy")

        val mainActivity = activity as? MainActivity
        val appCompatActivity = activity as? AppCompatActivity

        if (childFragmentPolicy == ToolbarPolicy.HIDDEN) {
            // If child (e.g., SearchFragment) wants MainActivity's toolbar hidden because it will use its own
            L.d("MainFragment: Child requests HIDDEN policy. Telling MainActivity to hide its toolbar container.")
            mainViewProvider?.updateToolbarState(this, scrollableViewForChildren, ToolbarPolicy.HIDDEN)
        } else {
            // If child wants MainActivity's toolbar to be visible (e.g., SavedPagesFragment)
            L.d("MainFragment: Child requests VISIBLE policy ($childFragmentPolicy). Ensuring MainActivity's toolbar is active SupportActionBar.")
            mainActivity?.mainBindingToolbar?.let {
                appCompatActivity?.setSupportActionBar(it)
                L.d("MainFragment: Set MainActivity's own toolbar as SupportActionBar.")
            }
            // Apply the child's requested policy (e.g., COLLAPSIBLE_WITH_CONTENT) to MainActivity's toolbar
            mainViewProvider?.updateToolbarState(this, scrollableViewForChildren, childFragmentPolicy)
            L.d("MainFragment: Told MainActivity to apply policy $childFragmentPolicy to its toolbar.")
        }
    }

    override fun onResume() {
        super.onResume()
        L.d("MainFragment: onResume. Notifying activity of toolbar state.")
        // Post to ensure views are measured, especially important for initial setup or after config change
        view?.post { notifyActivityOfToolbarState() }
        // Also invalidate options menu in case MainActivity's toolbar became active again
        requireActivity().invalidateOptionsMenu()
    }

    override fun onDetach() {
        super.onDetach()
        mainViewProvider?.updateToolbarState(null, null, ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT) // Default on detach
        mainViewProvider = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        L.d("MainFragment: MenuProvider: onCreateMenu called.")
        menuInflater.inflate(R.menu.menu_main_toolbar, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        L.d("MainFragment: MenuProvider: onPrepareMenu called.")
        val tabsMenuItem = menu.findItem(R.id.menu_tabs)
        val currentTabCount = OSRSWikiApp.instance.tabList.size
        L.d("MainFragment: MenuProvider: onPrepareMenu: currentTabCount = $currentTabCount")

        if (tabsMenuItem == null) {
            L.e("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem is NULL.")
            return
        } else {
            L.d("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem found (ID: ${tabsMenuItem.itemId}).")
        }

        if (currentTabCount > 0) {
            L.d("MainFragment: MenuProvider: onPrepareMenu: currentTabCount > 0.")
            tabsMenuItem.isVisible = true
            if (tabCountsViewInstance == null) {
                tabCountsViewInstance = TabCountsView(requireContext()).apply {
                    setOnClickListener {
                        Toast.makeText(requireContext(), "Tab switcher clicked! ($currentTabCount tabs)", Toast.LENGTH_SHORT).show()
                        L.d("TabCountsView clicked. Current tabs: $currentTabCount")
                        // TODO: Launch actual Tab Switcher UI
                    }
                }
            }
            tabCountsViewInstance?.updateTabCount(showTabCountsAnimation)
            tabsMenuItem.actionView = tabCountsViewInstance
            showTabCountsAnimation = false
        } else {
            L.d("MainFragment: MenuProvider: onPrepareMenu: currentTabCount is 0 or less.")
            tabsMenuItem.isVisible = false
            tabsMenuItem.actionView = null
            tabCountsViewInstance = null
        }

        // Hide search icon if current page is SearchFragment
        val searchMenuItem = menu.findItem(R.id.menu_search)
        if (binding.mainViewPager.currentItem == SEARCH_FRAGMENT_INDEX) {
            searchMenuItem?.isVisible = false
            L.d("MainFragment: MenuProvider: onPrepareMenu: Hiding search icon as SearchFragment is active.")
        } else {
            searchMenuItem?.isVisible = true
            L.d("MainFragment: MenuProvider: onPrepareMenu: Showing search icon.")
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        L.d("MainFragment: MenuProvider: onMenuItemSelected: ${menuItem.title}")
        return when (menuItem.itemId) {
            R.id.menu_search -> {
                L.d("MainFragment: Search icon selected. Navigating to SearchFragment.")
                binding.mainViewPager.setCurrentItem(SEARCH_FRAGMENT_INDEX, false)
                true // Item handled
            }
            // R.id.menu_tabs is handled by its actionView's OnClickListener
            else -> false // Item not handled by this fragment
        }
    }

    private class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()
        // Define number of tabs based on constants
        private val numTabs = 2 // Corresponds to SAVED_PAGES_FRAGMENT_INDEX and SEARCH_FRAGMENT_INDEX

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

        fun getFragmentAt(position: Int, fragmentManager: FragmentManager): Fragment? {
            // Standard way to get fragment from FragmentStateAdapter
            return fragmentManager.findFragmentByTag("f$position") ?: fragments[position]
        }
    }

    // PlaceholderFragment for 'More' or other tabs if they were ViewPager pages
    // Kept for reference but not directly used by bottom nav's 'More' currently
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
            val linearLayout = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
            for (i in 1..50) {
                val itemTextView = android.widget.TextView(context).apply {
                    text = "Item $i for $placeholderText"; textSize = 20f; setPadding(16,16,16,16)
                }
                linearLayout.addView(itemTextView)
            }
            scrollView.addView(linearLayout)
            return scrollView
        }
        override fun getScrollableView(): View? = scrollableView
        override fun getToolbarPolicy(): ToolbarPolicy = ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT // Example
        companion object {
            private const val ARG_TEXT = "placeholder_text"
            fun newInstance(text: String): PlaceholderFragment {
                return PlaceholderFragment().apply { arguments = Bundle().apply { putString(ARG_TEXT, text) } }
            }
        }
    }
}
