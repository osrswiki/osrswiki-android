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
import com.omiyawaki.osrswiki.settings.SettingsCategoriesActivity
import com.omiyawaki.osrswiki.util.log.L

class MoreFragment : Fragment() {

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
                titleRes = R.string.menu_title_settings,
                iconRes = R.drawable.ic_settings_24,
                action = MoreAction.SETTINGS
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
            MoreAction.SETTINGS -> {
                val intent = SettingsCategoriesActivity.newIntent(requireContext())
                startActivity(intent)
            }
            MoreAction.DONATE -> {
                val intent = DonateActivity.newIntent(requireContext())
                startActivity(intent)
            }
            MoreAction.ABOUT -> {
                val intent = AboutActivity.newIntent(requireContext())
                startActivity(intent)
            }
            MoreAction.FEEDBACK -> {
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
}