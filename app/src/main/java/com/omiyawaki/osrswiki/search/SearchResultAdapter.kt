package com.omiyawaki.osrswiki.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter // Import PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding // ViewBinding class
import com.omiyawaki.osrswiki.util.applyAlegreyaTitle
import com.omiyawaki.osrswiki.util.applyIBMPlexSansBody

// Ensure CleanedSearchResultItem is accessible (e.g., imported if in its own file)
// import com.omiyawaki.osrswiki.search.CleanedSearchResultItem

class SearchResultAdapter(
    private val onItemClicked: (CleanedSearchResultItem) -> Unit
) : PagingDataAdapter<CleanedSearchResultItem, SearchResultAdapter.SearchResultViewHolder>(
    SearchResultDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val item = getItem(position)
        item?.let {
            holder.bind(it)
        }
    }

    class SearchResultViewHolder(
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
            binding.searchItemTitle.applyAlegreyaTitle()
            if (item.snippet.isEmpty()) {
                binding.searchItemSnippet.visibility = View.GONE // Corrected ID
            } else {
                binding.searchItemSnippet.text = item.snippet // Corrected ID
                binding.searchItemSnippet.visibility = View.VISIBLE // Corrected ID
                binding.searchItemSnippet.applyIBMPlexSansBody()
            }
        }
    }
}

class SearchResultDiffCallback : DiffUtil.ItemCallback<CleanedSearchResultItem>() {
    override fun areItemsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        return oldItem == newItem
    }
}
