package com.omiyawaki.osrswiki.ui.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMainBinding // Updated binding class name
// TODO: Import actual fragment classes for ViewPager2 pages when they are ready
// e.g., import com.omiyawaki.osrswiki.search.SearchFragment
// import com.omiyawaki.osrswiki.saved.SavedFragment
// import com.omiyawaki.osrswiki.history.HistoryFragment

// Interface for MainActivity to implement to receive the scrollable view
interface MainScrollableViewProvider {
    fun updateScrollViewForToolbarHandler(scrollableView: View?)
}

// Interface for child fragments of ViewPager2 to implement
interface ScrollableContent {
    fun getScrollableView(): View?
}

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null // Updated binding class name
    private val binding get() = _binding!!

    private var mainActivityScrollableViewProvider: MainScrollableViewProvider? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainScrollableViewProvider) {
            mainActivityScrollableViewProvider = context
        } else {
            throw RuntimeException("$context must implement MainScrollableViewProvider")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false) // Updated binding class name
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupViewPager() {
        binding.mainViewPager.adapter = ViewPagerAdapter(this)
        binding.mainViewPager.isUserInputEnabled = false // Controlled by BottomNavigationView

        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.bottomNavView.menu.getItem(position).isChecked = true
                notifyMainActivityOfScrollableView()
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavView.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_search_bottom -> 0
                R.id.nav_saved_bottom -> 1
                R.id.nav_history_bottom -> 2
                else -> -1
            }
            if (position != -1) {
                binding.mainViewPager.setCurrentItem(position, false)
                true
            } else {
                false
            }
        }
        // Ensure initial sync if adapter has items
        if (binding.mainViewPager.adapter?.itemCount ?: 0 > 0) {
             binding.bottomNavView.menu.getItem(binding.mainViewPager.currentItem).isChecked = true
             // Initial notification for the first fragment
             // Post this to ensure child fragment's view is created
             view?.post { notifyMainActivityOfScrollableView() }
        }
    }

    // Call this when the ViewPager2 page changes or when this fragment resumes
    fun notifyMainActivityOfScrollableView() {
        val currentAdapter = binding.mainViewPager.adapter as? ViewPagerAdapter
        val currentChildFragment = currentAdapter?.getFragmentAt(binding.mainViewPager.currentItem, childFragmentManager)

        val scrollableView = if (currentChildFragment is ScrollableContent) {
            currentChildFragment.getScrollableView()
        } else {
            null
        }
        mainActivityScrollableViewProvider?.updateScrollViewForToolbarHandler(scrollableView)
    }

    override fun onResume() {
        super.onResume()
        // It's important to notify after child fragments have potentially created their views.
        // Post to allow child fragment's lifecycle methods to complete.
        view?.post { notifyMainActivityOfScrollableView() }
    }

    override fun onDetach() {
        super.onDetach()
        mainActivityScrollableViewProvider?.updateScrollViewForToolbarHandler(null) // Clear scroll view when detaching
        mainActivityScrollableViewProvider = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private val fragments = mutableMapOf<Int, Fragment>()

        // TODO: Update this count based on the number of actual main sections/tabs (should match menu items)
        private val numTabs = 3

        override fun getItemCount(): Int = numTabs

        override fun createFragment(position: Int): Fragment {
            val existingFragment = fragments[position]
            if (existingFragment != null) {
                return existingFragment
            }
            val newFragment = when (position) {
                // TODO: Replace PlaceholderFragment with your actual fragment classes
                // Make sure these fragments implement the ScrollableContent interface
                0 -> PlaceholderFragment.newInstance("Search Tab") // e.g., SearchFragment.newInstance()
                1 -> PlaceholderFragment.newInstance("Saved Tab")   // e.g., SavedFragment.newInstance()
                2 -> PlaceholderFragment.newInstance("History Tab") // e.g., HistoryFragment.newInstance()
                else -> throw IllegalStateException("Invalid position $position for ViewPager2")
            }
            fragments[position] = newFragment
            return newFragment
        }

        fun getFragmentAt(position: Int, fragmentManager: androidx.fragment.app.FragmentManager): Fragment? {
            // A more reliable way to get the currently active fragment in ViewPager2
            // is to use the FragmentManager and the tag ViewPager2 assigns.
            // However, for the callback, it's better if child fragments themselves provide their scroll view.
            // This implementation tries to return a cached or newly created fragment.
            // For simply getting the scrollable view, child fragments should call up to parent.
            // For now, we will rely on the child fragments implementing ScrollableContent
            // and MainFragment querying the current one after a page change.
            return fragments[position] ?: createFragment(position) // This might re-create if not cached.
                                                                   // This getFragmentAt is mostly for reference;
                                                                   // the primary way to get scrollable view should be through
                                                                   // direct calls from child fragments or a shared ViewModel.
                                                                   // The current notifyMainActivityOfScrollableView logic
                                                                   // directly uses this.
        }
    }

    // Placeholder Fragment - Replace with your actual fragments for each tab.
    // Your actual fragments (SearchFragment, etc.) should implement the ScrollableContent interface.
    class PlaceholderFragment : Fragment(), ScrollableContent {
        private var scrollableView: androidx.core.widget.NestedScrollView? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val context = requireContext()
            val scrollView = androidx.core.widget.NestedScrollView(context)
            this.scrollableView = scrollView // Store reference

            val linearLayout = android.widget.LinearLayout(context)
            linearLayout.orientation = android.widget.LinearLayout.VERTICAL
            val placeholderText = arguments?.getString("placeholder_text") ?: "Placeholder"

            for (i in 1..50) {
                val itemTextView = android.widget.TextView(context)
                itemTextView.text = "Item $i for $placeholderText"
                itemTextView.textSize = 20f
                itemTextView.setPadding(16,16,16,16)
                linearLayout.addView(itemTextView)
            }
            scrollView.addView(linearLayout)
            return scrollView
        }

        override fun getScrollableView(): View? {
            return scrollableView
        }

        companion object {
            fun newInstance(text: String): PlaceholderFragment {
                val fragment = PlaceholderFragment()
                val args = Bundle()
                args.putString("placeholder_text", text)
                fragment.arguments = args
                return fragment
            }
        }
    }

    companion object {
        fun newInstance() = MainFragment()
    }
}
