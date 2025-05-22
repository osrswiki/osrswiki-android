package com.omiyawaki.osrswiki.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter // Import PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding // ViewBinding class

// CleanedSearchResultItem is expected to be accessible from this package
// (it was defined in SearchViewModel.kt in the same package).
// If you move CleanedSearchResultItem to its own file or a different package, adjust imports if necessary.

class SearchResultAdapter(
    private val onItemClicked: (CleanedSearchResultItem) -> Unit // Updated to CleanedSearchResultItem
) : PagingDataAdapter<CleanedSearchResultItem, SearchResultAdapter.SearchResultViewHolder>(SearchResultDiffCallback()) { // Updated base class and item type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val item = getItem(position) // getItem() from PagingDataAdapter can return null
        // Bind item only if it's not null (PagingData might have placeholders or be empty)
        item?.let {
            holder.bind(it)
        }
    }

    class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onItemClicked: (CleanedSearchResultItem) -> Unit // Updated to CleanedSearchResultItem
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: CleanedSearchResultItem? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let {
                    onItemClicked(it)
                }
            }
        }

        fun bind(item: CleanedSearchResultItem) { // Parameter is now CleanedSearchResultItem (non-null here due to the let block above)
            currentItem = item
            binding.itemSearchTitleTextview.text = item.title
            if (item.snippet.isEmpty()) { // Check isEmpty since snippet in CleanedSearchResultItem is non-null but can be empty
                binding.itemSearchSnippetTextview.visibility = View.GONE
            } else {
                binding.itemSearchSnippetTextview.text = item.snippet
                binding.itemSearchSnippetTextview.visibility = View.VISIBLE
            }
        }
    }
}

class SearchResultDiffCallback : DiffUtil.ItemCallback<CleanedSearchResultItem>() { // Updated to CleanedSearchResultItem
    override fun areItemsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        // 'id' in CleanedSearchResultItem is the unique identifier (pageid as String)
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        // Check if the content of the items is the same
        return oldItem == newItem
    }
}
