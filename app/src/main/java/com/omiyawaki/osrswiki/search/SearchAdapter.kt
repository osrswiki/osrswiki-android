package com.omiyawaki.osrswiki.search

import android.text.Html // <<< ADDED IMPORT
import android.view.LayoutInflater
import android.view.View // <<< ADDED IMPORT
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding

class SearchAdapter(
    private val onItemClickListener: OnItemClickListener
) : PagingDataAdapter<CleanedSearchResultItem, SearchAdapter.SearchResultViewHolder>(
    SEARCH_RESULT_COMPARATOR
) {

    interface OnItemClickListener {
        fun onItemClick(item: CleanedSearchResultItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding, onItemClickListener)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bind(item)
        }
    }

    class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CleanedSearchResultItem) {
            // Clean title before setting
            binding.searchItemTitle.text = Html.fromHtml(item.title, Html.FROM_HTML_MODE_LEGACY).toString()

            // Check if snippet is empty and set visibility accordingly
            if (item.snippet.isNotBlank()) {
                binding.searchItemSnippet.text = Html.fromHtml(item.snippet, Html.FROM_HTML_MODE_LEGACY).toString()
                binding.searchItemSnippet.visibility = View.VISIBLE
            } else {
                binding.searchItemSnippet.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                listener.onItemClick(item)
            }
        }
    }

    companion object {
        // Visibility changed to internal in a previous step
        internal val SEARCH_RESULT_COMPARATOR = object : DiffUtil.ItemCallback<CleanedSearchResultItem>() {
            override fun areItemsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean =
                oldItem == newItem
        }
    }
}