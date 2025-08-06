package com.omiyawaki.osrswiki.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSettingsCategoryBinding
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyInterBody

class SettingsCategoriesAdapter(
    private val categories: List<SettingsCategory>,
    private val onCategoryClick: (SettingsCategoryAction) -> Unit
) : RecyclerView.Adapter<SettingsCategoriesAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemSettingsCategoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(
        private val binding: ItemSettingsCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: SettingsCategory) {
            binding.iconSettingsCategory.setImageResource(category.iconRes)
            binding.textSettingsCategoryTitle.setText(category.titleRes)
            binding.textSettingsCategoryTitle.applyAlegreyaHeadline()
            binding.textSettingsCategoryDescription.setText(category.descriptionRes)
            
            binding.root.setOnClickListener {
                onCategoryClick(category.action)
            }
        }
    }
}