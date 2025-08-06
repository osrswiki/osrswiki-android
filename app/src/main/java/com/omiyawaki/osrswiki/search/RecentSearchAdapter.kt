package com.omiyawaki.osrswiki.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemRecentSearchBinding
import com.omiyawaki.osrswiki.search.db.RecentSearch
import com.omiyawaki.osrswiki.util.applyRubikUILabel

class RecentSearchAdapter(
    private val onItemClicked: (RecentSearch) -> Unit
) : ListAdapter<RecentSearch, RecentSearchAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemClicked)
    }

    class ViewHolder(private val binding: ItemRecentSearchBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecentSearch, onItemClicked: (RecentSearch) -> Unit) {
            binding.textViewRecentSearchQuery.text = item.query
            binding.root.setOnClickListener { onItemClicked(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecentSearch>() {
        override fun areItemsTheSame(oldItem: RecentSearch, newItem: RecentSearch): Boolean {
            return oldItem.query == newItem.query
        }

        override fun areContentsTheSame(oldItem: RecentSearch, newItem: RecentSearch): Boolean {
            return oldItem == newItem
        }
    }
}
