package com.omiyawaki.osrswiki.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMoreBinding
import com.omiyawaki.osrswiki.settings.SettingsActivity
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
                val intent = SettingsActivity.newIntent(requireContext())
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