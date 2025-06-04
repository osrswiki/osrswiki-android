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
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
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
            binding.mainViewPager.setCurrentItem(1, false)
            L.d("MainFragment: Set initial ViewPager item to 1 (SearchFragment)")
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
        binding.mainViewPager.isUserInputEnabled = false
        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position < binding.bottomNavView.menu.size()) {
                    binding.bottomNavView.menu.getItem(position).isChecked = true
                }
                notifyActivityOfToolbarState()
                requireActivity().invalidateOptionsMenu()
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
                if (item.itemId == R.id.nav_more_bottom && previousItem < binding.bottomNavView.menu.size()) {
                    binding.bottomNavView.menu.getItem(previousItem).isChecked = true
                }
                false
            }
        }
        if (binding.mainViewPager.adapter?.itemCount ?: 0 > 0) {
            val currentVPItem = binding.mainViewPager.currentItem
            if (currentVPItem < binding.bottomNavView.menu.size()) {
                binding.bottomNavView.menu.getItem(currentVPItem).isChecked = true
            }
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
        // Determine scrollable view from the current child fragment within MainFragment's ViewPager
        val currentAdapter = binding.mainViewPager.adapter as? ViewPagerAdapter
        val currentPosition = binding.mainViewPager.currentItem
        val currentChildFragment = if (currentPosition < (currentAdapter?.itemCount ?: 0)) {
            currentAdapter?.getFragmentAt(currentPosition, childFragmentManager)
        } else { null }
        val scrollableViewForChildren = if (currentChildFragment is ScrollableContent) currentChildFragment.getScrollableView() else null

        // Policy for MainActivity's toolbar when MainFragment is active:
        // We want MainActivity's toolbar to be visible to show TabCountsView.
        // Child fragments (e.g., SearchFragment) can still manage their *own* internal toolbars independently.
        // This policy specifically applies to MainActivity's main toolbar.
        val policyForMainActivityToolbar = ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT // Or ToolbarPolicy.VISIBLE or a new non-collapsing policy if preferred
        L.d("MainFragment: notifyActivityOfToolbarState: Setting MainActivity toolbar policy to: $policyForMainActivityToolbar. HostFragment passed to MainActivity will be MainFragment itself.")

        // Call MainActivity's updateToolbarState.
        // The hostFragment is MainFragment (this).
        // The scrollableView is derived from MainFragment's currently active child page,
        // allowing MainActivity's toolbar to react to scrolling within that child.
        mainViewProvider?.updateToolbarState(this, scrollableViewForChildren, policyForMainActivityToolbar)
    }

    override fun onResume() {
        super.onResume()
        view?.post { notifyActivityOfToolbarState() }
    }

    override fun onDetach() {
        super.onDetach()
        // When MainFragment is detached, MainActivity might revert to a default policy or be updated by another fragment.
        // For now, setting a generic default. This might need refinement based on overall app navigation.
        mainViewProvider?.updateToolbarState(null, null, ToolbarPolicy.COLLAPSIBLE_WITH_CONTENT)
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
            L.e("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem is NULL. Check R.id.menu_tabs and menu_main_toolbar.xml inflation.")
            return 
        } else {
            L.d("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem found (ID: ${tabsMenuItem.itemId}).")
        }

        if (currentTabCount > 0) {
            L.d("MainFragment: MenuProvider: onPrepareMenu: currentTabCount > 0. Preparing TabCountsView.")
            tabsMenuItem.isVisible = true
            L.d("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem.isVisible = true")
            if (tabCountsViewInstance == null) {
                L.d("MainFragment: MenuProvider: onPrepareMenu: Instantiating TabCountsView...")
                tabCountsViewInstance = TabCountsView(requireContext()).apply {
                    setOnClickListener {
                        Toast.makeText(requireContext(), "Tab switcher clicked! ($currentTabCount tabs)", Toast.LENGTH_SHORT).show()
                        L.d("TabCountsView clicked. Current tabs: $currentTabCount")
                        // TODO: Launch actual Tab Switcher UI
                    }
                }
                L.d("MainFragment: MenuProvider: onPrepareMenu: TabCountsView instantiated.")
            } else {
                L.d("MainFragment: MenuProvider: onPrepareMenu: Reusing existing TabCountsView instance.")
            }
            
            tabCountsViewInstance?.let {
                L.d("MainFragment: MenuProvider: onPrepareMenu: Calling updateTabCount(${OSRSWikiApp.instance.tabList.size}, $showTabCountsAnimation) on TabCountsView instance.")
                it.updateTabCount(showTabCountsAnimation) 
            }

            tabsMenuItem.actionView = tabCountsViewInstance
            L.d("MainFragment: MenuProvider: onPrepareMenu: tabCountsViewInstance set as actionView for menu_tabs.")
            showTabCountsAnimation = false
        } else {
            L.d("MainFragment: MenuProvider: onPrepareMenu: currentTabCount is 0 or less.")
            tabsMenuItem.isVisible = false
            L.d("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem.isVisible = false")
            tabsMenuItem.actionView = null
            L.d("MainFragment: MenuProvider: onPrepareMenu: tabsMenuItem.actionView = null")
            tabCountsViewInstance = null 
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false 
    }

    private class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()
        private val numTabs = 2
        override fun getItemCount(): Int = numTabs
        override fun createFragment(position: Int): Fragment {
            fragments[position]?.let { return it }
            val newFragment = when (position) {
                0 -> SavedPagesFragment()
                1 -> SearchFragment.newInstance()
                else -> throw IllegalStateException("Invalid position $position")
            }
            fragments[position] = newFragment
            return newFragment
        }
        fun getFragmentAt(position: Int, fragmentManager: FragmentManager): Fragment? = fragments[position]
    }

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
        override fun getToolbarPolicy(): ToolbarPolicy = ToolbarPolicy.HIDDEN
        companion object {
            private const val ARG_TEXT = "placeholder_text"
            fun newInstance(text: String): PlaceholderFragment {
                return PlaceholderFragment().apply { arguments = Bundle().apply { putString(ARG_TEXT, text) } }
            }
        }
    }

    companion object {
        fun newInstance() = MainFragment()
    }
}
