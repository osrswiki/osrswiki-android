package com.omiyawaki.osrswiki.ui.more

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemMoreBinding

class MoreAdapter(
    private val items: List<MoreItem> = emptyList(),
    private val onItemClick: (MoreAction) -> Unit = {}
) : RecyclerView.Adapter<MoreAdapter.MoreViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoreViewHolder {
        val binding = ItemMoreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MoreViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoreViewHolder, position: Int) {
        val isLastItem = position == items.size - 1
        holder.bind(items[position], isLastItem)
    }

    override fun getItemCount(): Int = items.size

    inner class MoreViewHolder(private val binding: ItemMoreBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: MoreItem, isLastItem: Boolean = false) {
            binding.iconMoreItem.setImageResource(item.iconRes)
            binding.textMoreItem.setText(item.titleRes)
            
            // Hide divider for last item to match iOS design
            binding.dividerMoreItem.visibility = if (isLastItem) android.view.View.GONE else android.view.View.VISIBLE
            
            // Set click listener on the main content LinearLayout instead of root since root is now a vertical container
            val mainContentLayout = binding.root.getChildAt(0) as? android.view.ViewGroup
            mainContentLayout?.setOnClickListener {
                android.util.Log.d("MoreAdapter", "MainContent clicked for item: ${item.titleRes}")
                onItemClick(item.action)
            }
            
            // Also set on root as fallback
            binding.root.setOnClickListener {
                android.util.Log.d("MoreAdapter", "Root clicked for item: ${item.titleRes}")
                onItemClick(item.action)
            }
        }
    }
}