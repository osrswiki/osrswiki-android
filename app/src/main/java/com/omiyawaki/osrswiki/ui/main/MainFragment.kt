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
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        binding.mainViewPager.adapter = ViewPagerAdapter(this)
        // User input is disabled because navigation is primarily via BottomNavigationView,
        // except for the "More" button which won't change the ViewPager.
        binding.mainViewPager.isUserInputEnabled = false

        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Sync BottomNavigationView's selected item with ViewPager's current page
                binding.bottomNavView.menu.getItem(position).isChecked = true
                notifyActivityOfToolbarState()
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_saved_bottom -> {
                    binding.mainViewPager.setCurrentItem(0, false)
                    true // Consume the event, item is selected
                }
                R.id.nav_search_bottom -> {
                    binding.mainViewPager.setCurrentItem(1, false)
                    true // Consume the event, item is selected
                }
                R.id.nav_more_bottom -> {
                    // Show popup menu, do not change ViewPager, do not consume to keep previous tab selected
                    showMorePopupMenu(binding.bottomNavView.findViewById(R.id.nav_more_bottom))
                    false // Do not consume the event, so the "More" tab doesn't stay selected
                }
                else -> false
            }
        }
        // Initial sync if needed
        if (binding.mainViewPager.adapter?.itemCount ?: 0 > 0) {
             binding.bottomNavView.menu.getItem(binding.mainViewPager.currentItem).isChecked = true
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
        // Get fragment only if currentItem is valid for the adapter (0 or 1)
        val currentPosition = binding.mainViewPager.currentItem
        val currentChildFragment = if (currentPosition < (currentAdapter?.itemCount ?: 0)) {
            currentAdapter?.getFragmentAt(currentPosition, childFragmentManager)
        } else {
            null // Should not happen if ViewPager is limited to 2 items
        }


        val scrollableView = if (currentChildFragment is ScrollableContent) {
            currentChildFragment.getScrollableView()
        } else {
            null
        }

        val policy = if (currentChildFragment is FragmentToolbarPolicyProvider) {
            currentChildFragment.getToolbarPolicy()
        } else {
            // If currentChildFragment is null (e.g. after "More" click where ViewPager doesn't change to a new valid page for policy check)
            // or if fragment doesn't specify, what should be the policy?
            // It should ideally be based on the *actually displayed content fragment* (Saved or Search).
            // The current setup should ensure policy is from the visible content fragment.
            // Defaulting here, but notifyActivityOfToolbarState is called on page changes of ViewPager.
            ToolbarPolicy.HIDDEN // Default to HIDDEN for Saved/Search if somehow undetermined
        }
        mainViewProvider?.updateToolbarState(currentChildFragment, scrollableView, policy)
    }

    override fun onResume() {
        super.onResume()
        view?.post { notifyActivityOfToolbarState() }
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

    private class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()
        private val numTabs = 2 // Now only Saved and Search

        override fun getItemCount(): Int = numTabs

        override fun createFragment(position: Int): Fragment {
            fragments[position]?.let { return it }

            val newFragment = when (position) {
                0 -> PlaceholderFragment.newInstance("Saved Tab") // Position 0: Saved
                1 -> SearchFragment.newInstance()                // Position 1: Search
                // No case for position 2 (More)
                else -> throw IllegalStateException("Invalid position $position for ViewPager2. Max items: $numTabs")
            }
            fragments[position] = newFragment
            return newFragment
        }

        fun getFragmentAt(position: Int, fragmentManager: FragmentManager): Fragment? {
            return fragments[position] ?: if (position < numTabs) createFragment(position) else null
        }
    }

    // PlaceholderFragment for "Saved" (and "More" if it were a tab, but it's not now)
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

        override fun getToolbarPolicy(): ToolbarPolicy = ToolbarPolicy.HIDDEN

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
