package com.omiyawaki.osrswiki.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentSettingsCategoriesBinding
import com.omiyawaki.osrswiki.util.log.L

class SettingsCategoriesFragment : Fragment() {

    private var _binding: FragmentSettingsCategoriesBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: SettingsCategoriesAdapter

    companion object {
        fun newInstance() = SettingsCategoriesFragment()
        const val TAG = "SettingsCategoriesFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("SettingsCategoriesFragment: onCreateView called.")
        _binding = FragmentSettingsCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("SettingsCategoriesFragment: onViewCreated called.")
        
        setupRecyclerView()
    }
    
    private fun setupRecyclerView() {
        val categories = listOf(
            SettingsCategory(
                titleRes = R.string.settings_category_appearance,
                descriptionRes = R.string.settings_category_appearance_description,
                iconRes = R.drawable.ic_appearance_24,
                action = SettingsCategoryAction.APPEARANCE
            ),
            SettingsCategory(
                titleRes = R.string.settings_category_offline_storage,
                descriptionRes = R.string.settings_category_offline_storage_description,
                iconRes = R.drawable.ic_storage_24,
                action = SettingsCategoryAction.OFFLINE_STORAGE
            )
        )
        
        adapter = SettingsCategoriesAdapter(
            categories = categories,
            onCategoryClick = { action ->
                handleCategoryClick(action)
            }
        )
        
        binding.recyclerViewSettingsCategories.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SettingsCategoriesFragment.adapter
        }
    }
    
    private fun handleCategoryClick(action: SettingsCategoryAction) {
        L.d("SettingsCategoriesFragment: Category clicked: $action")
        when (action) {
            SettingsCategoryAction.APPEARANCE -> {
                val intent = AppearanceSettingsActivity.newIntent(requireContext())
                startActivity(intent)
            }
            SettingsCategoryAction.OFFLINE_STORAGE -> {
                val intent = OfflineSettingsActivity.newIntent(requireContext())
                startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        L.d("SettingsCategoriesFragment: onDestroyView called.")
        _binding = null
        super.onDestroyView()
    }
}