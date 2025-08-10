package com.omiyawaki.osrswiki.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSettingCategoryBinding
import com.omiyawaki.osrswiki.databinding.ItemSettingListBinding
import com.omiyawaki.osrswiki.databinding.ItemSettingSwitchBinding
import com.omiyawaki.osrswiki.databinding.ItemSettingInlineThemeBinding
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * RecyclerView adapter for custom settings screen.
 * Handles multiple ViewHolder types based on SettingItem sealed class.
 */
class SettingsAdapter(
    private val onSwitchToggle: (String, Boolean) -> Unit,
    private val onListClick: (String) -> Unit,
    private val onThemeSelected: (String) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope
) : ListAdapter<SettingItem, RecyclerView.ViewHolder>(SettingItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_SWITCH = 1
        private const val VIEW_TYPE_LIST = 2
        private const val VIEW_TYPE_INLINE_THEME = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SettingItem.CategoryHeader -> VIEW_TYPE_CATEGORY
            is SettingItem.SwitchSetting -> VIEW_TYPE_SWITCH
            is SettingItem.ListSetting -> VIEW_TYPE_LIST
            is SettingItem.InlineThemeSelection -> VIEW_TYPE_INLINE_THEME
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_CATEGORY -> {
                val binding = ItemSettingCategoryBinding.inflate(inflater, parent, false)
                CategoryViewHolder(binding)
            }
            VIEW_TYPE_SWITCH -> {
                val binding = ItemSettingSwitchBinding.inflate(inflater, parent, false)
                SwitchViewHolder(binding, onSwitchToggle)
            }
            VIEW_TYPE_LIST -> {
                val binding = ItemSettingListBinding.inflate(inflater, parent, false)
                ListViewHolder(binding, onListClick)
            }
            VIEW_TYPE_INLINE_THEME -> {
                val binding = ItemSettingInlineThemeBinding.inflate(inflater, parent, false)
                InlineThemeViewHolder(binding, onThemeSelected, lifecycleScope)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingItem.CategoryHeader -> (holder as CategoryViewHolder).bind(item)
            is SettingItem.SwitchSetting -> (holder as SwitchViewHolder).bind(item)
            is SettingItem.ListSetting -> (holder as ListViewHolder).bind(item)
            is SettingItem.InlineThemeSelection -> (holder as InlineThemeViewHolder).bind(item)
        }
    }

    class CategoryViewHolder(private val binding: ItemSettingCategoryBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: SettingItem.CategoryHeader) {
            binding.categoryTitle.text = item.title
        }
    }

    class SwitchViewHolder(
        private val binding: ItemSettingSwitchBinding,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: SettingItem.SwitchSetting) {
            binding.title.text = item.title
            binding.summary.text = item.summary
            binding.switchWidget.isChecked = item.isChecked
            
            // Handle clicks on the entire row
            binding.root.setOnClickListener {
                val newCheckedState = !binding.switchWidget.isChecked
                binding.switchWidget.isChecked = newCheckedState
                onToggle(item.key, newCheckedState)
            }
        }
    }

    class ListViewHolder(
        private val binding: ItemSettingListBinding,
        private val onClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: SettingItem.ListSetting) {
            binding.title.text = item.title
            binding.summary.text = item.displayValue
            
            binding.root.setOnClickListener {
                onClick(item.key)
            }
        }
    }

    class InlineThemeViewHolder(
        private val binding: ItemSettingInlineThemeBinding,
        private val onThemeSelected: (String) -> Unit,
        private val lifecycleScope: LifecycleCoroutineScope
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var themeAdapter: InlineThemeSelectionAdapter? = null
        
        fun bind(item: SettingItem.InlineThemeSelection) {
            binding.textTitle.text = item.title
            
            // Set up responsive RecyclerView for theme previews
            binding.themePreviewRecyclerView.apply {
                themeAdapter = InlineThemeSelectionAdapter(
                    themes = item.themes,
                    currentSelection = item.currentSelection,
                    onThemeSelected = onThemeSelected,
                    lifecycleScope = lifecycleScope
                )
                adapter = themeAdapter
                
                // Use responsive layout manager that switches between vertical and horizontal layouts
                layoutManager = ResponsiveThemeLayoutManager(context) { isHorizontal ->
                    // Update adapter's layout mode when layout manager changes
                    themeAdapter?.updateLayoutMode(isHorizontal, this)
                }
                
                // Accessibility improvements for RecyclerView
                accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = "android.widget.RadioGroup"
                        info.contentDescription = "Choose app theme. ${item.themes.size} options available."
                    }
                }
            }
        }
    }

    class SettingItemDiffCallback : DiffUtil.ItemCallback<SettingItem>() {
        override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return when {
                oldItem is SettingItem.CategoryHeader && newItem is SettingItem.CategoryHeader ->
                    oldItem.title == newItem.title
                oldItem is SettingItem.SwitchSetting && newItem is SettingItem.SwitchSetting ->
                    oldItem.key == newItem.key
                oldItem is SettingItem.ListSetting && newItem is SettingItem.ListSetting ->
                    oldItem.key == newItem.key
                oldItem is SettingItem.InlineThemeSelection && newItem is SettingItem.InlineThemeSelection ->
                    oldItem.key == newItem.key
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return oldItem == newItem
        }
    }
}