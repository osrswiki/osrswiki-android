package com.omiyawaki.osrswiki.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding

// CleanedSearchResultItem is assumed to be in the same package or imported correctly.
// import com.omiyawaki.osrswiki.search.CleanedSearchResultItem // Ensure this is accessible

class OfflineResultAdapter(
    private val onItemClicked: (CleanedSearchResultItem) -> Unit
) : ListAdapter<CleanedSearchResultItem, OfflineResultAdapter.OfflineResultViewHolder>(
    OfflineResultDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfflineResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OfflineResultViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: OfflineResultViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class OfflineResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onItemClicked: (CleanedSearchResultItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: CleanedSearchResultItem? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let {
                    onItemClicked(it)
                }
            }
        }

        fun bind(item: CleanedSearchResultItem) {
            currentItem = item
            binding.searchItemTitle.text = item.title // Corrected ID
            if (item.snippet.isEmpty()) {
                binding.searchItemSnippet.visibility = View.GONE // Corrected ID
            } else {
                binding.searchItemSnippet.text = item.snippet // Corrected ID
                binding.searchItemSnippet.visibility = View.VISIBLE // Corrected ID
            }
        }
    }
}

class OfflineResultDiffCallback : DiffUtil.ItemCallback<CleanedSearchResultItem>() {
    override fun areItemsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        return oldItem == newItem
    }
}
