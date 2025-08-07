package com.omiyawaki.osrswiki.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.about.AboutActivity
import com.omiyawaki.osrswiki.databinding.FragmentMoreBinding
import com.omiyawaki.osrswiki.donate.DonateActivity
import com.omiyawaki.osrswiki.feedback.FeedbackActivity
import com.omiyawaki.osrswiki.settings.AppearanceSettingsActivity
import com.omiyawaki.osrswiki.theme.ThemeAware
import com.omiyawaki.osrswiki.util.log.L

class MoreFragment : Fragment(), ThemeAware {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: MoreAdapter

    companion object {
        fun newInstance() = MoreFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("MoreFragment: onCreateView called.")
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("MoreFragment: onViewCreated called.")
        
        setupRecyclerView()
    }
    
    private fun setupRecyclerView() {
        val moreItems = listOf(
            MoreItem(
                titleRes = R.string.settings_category_appearance,
                iconRes = R.drawable.ic_page_action_theme,
                action = MoreAction.APPEARANCE
            ),
            MoreItem(
                titleRes = R.string.menu_title_donate,
                iconRes = R.drawable.ic_donate_24,
                action = MoreAction.DONATE
            ),
            MoreItem(
                titleRes = R.string.menu_title_about,
                iconRes = R.drawable.ic_about_24,
                action = MoreAction.ABOUT
            ),
            MoreItem(
                titleRes = R.string.menu_title_feedback,
                iconRes = R.drawable.ic_feedback_24,
                action = MoreAction.FEEDBACK
            )
        )
        
        adapter = MoreAdapter(
            items = moreItems,
            onItemClick = { action ->
                handleMoreItemClick(action)
            }
        )
        
        binding.recyclerViewMore.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MoreFragment.adapter
        }
    }
    
    private fun handleMoreItemClick(action: MoreAction) {
        L.d("MoreFragment: More item clicked: $action")
        when (action) {
            MoreAction.APPEARANCE -> {
                // Notify MainActivity that we're launching an external activity
                // This helps prevent incorrect back navigation when returning
                (activity as? com.omiyawaki.osrswiki.MainActivity)?.setReturningFromExternalActivity()
                val intent = AppearanceSettingsActivity.newIntent(requireContext())
                startActivity(intent)
            }
            MoreAction.DONATE -> {
                // Notify MainActivity that we're launching an external activity
                (activity as? com.omiyawaki.osrswiki.MainActivity)?.setReturningFromExternalActivity()
                val intent = DonateActivity.newIntent(requireContext())
                startActivity(intent)
            }
            MoreAction.ABOUT -> {
                // Notify MainActivity that we're launching an external activity
                (activity as? com.omiyawaki.osrswiki.MainActivity)?.setReturningFromExternalActivity()
                val intent = AboutActivity.newIntent(requireContext())
                startActivity(intent)
            }
            MoreAction.FEEDBACK -> {
                // Notify MainActivity that we're launching an external activity
                (activity as? com.omiyawaki.osrswiki.MainActivity)?.setReturningFromExternalActivity()
                val intent = FeedbackActivity.newIntent(requireContext())
                startActivity(intent)
            }
            else -> {
                L.d("MoreFragment: Action not yet implemented: $action")
            }
        }
    }

    override fun onDestroyView() {
        L.d("MoreFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }

    override fun onThemeChanged() {
        L.d("MoreFragment: onThemeChanged called")
        // Re-apply theme attributes to views that use theme attributes
        refreshThemeAttributes()
        // Refresh RecyclerView adapter to apply new theme colors to items
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    private fun refreshThemeAttributes() {
        if (_binding != null) {
            // Get the current theme's paper_color attribute
            val typedValue = android.util.TypedValue()
            val theme = requireContext().theme
            theme.resolveAttribute(com.omiyawaki.osrswiki.R.attr.paper_color, typedValue, true)
            
            // Apply the new background color to the root layout
            binding.root.setBackgroundColor(typedValue.data)
            
            L.d("MoreFragment: Theme attributes refreshed")
        }
    }
}