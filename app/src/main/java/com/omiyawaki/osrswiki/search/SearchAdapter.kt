package com.omiyawaki.osrswiki.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import com.omiyawaki.osrswiki.util.applyVollkornBody

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
            binding.searchItemTitle.text = item.title
            binding.searchItemTitle.applyAlegreyaHeadline()

            if (item.snippet.isNotBlank()) {
                binding.searchItemSnippet.text = item.snippet
                binding.searchItemSnippet.applyVollkornBody()
                binding.searchItemSnippet.visibility = View.VISIBLE
            } else {
                binding.searchItemSnippet.visibility = View.GONE
            }

            // Load the thumbnail if the URL exists, otherwise hide the image view.
            if (item.thumbnailUrl != null) {
                binding.searchItemThumbnail.visibility = View.VISIBLE
                Glide.with(binding.root.context)
                    .load(item.thumbnailUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(binding.searchItemThumbnail)
            } else {
                // It's important to clear the view and set visibility to GONE
                // to handle view recycling correctly.
                Glide.with(binding.root.context).clear(binding.searchItemThumbnail)
                binding.searchItemThumbnail.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                listener.onItemClick(item)
            }
        }
    }

    companion object {
        internal val SEARCH_RESULT_COMPARATOR = object : DiffUtil.ItemCallback<CleanedSearchResultItem>() {
            override fun areItemsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean =
                oldItem == newItem
        }
    }
}
