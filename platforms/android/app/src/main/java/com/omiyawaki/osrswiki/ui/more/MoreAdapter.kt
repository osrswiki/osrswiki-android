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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MoreViewHolder(private val binding: ItemMoreBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: MoreItem) {
            binding.iconMoreItem.setImageResource(item.iconRes)
            binding.textMoreItem.setText(item.titleRes)
            
            binding.root.setOnClickListener {
                onItemClick(item.action)
            }
        }
    }
}