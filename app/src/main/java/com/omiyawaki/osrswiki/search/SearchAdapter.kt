package com.omiyawaki.osrswiki.search

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline

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
                // Phase 2: Render search term highlights using HtmlCompat with theme-aware colors
                val context = binding.root.context
                val highlightColor = androidx.core.content.ContextCompat.getColor(
                    context, 
                    com.omiyawaki.osrswiki.R.color.search_highlight_light
                )
                val highlightColorHex = String.format("#%06X", (0xFFFFFF and highlightColor))
                
                // DEBUG: Log HTML conversion process
                val hasSearchMatch = item.snippet.contains("searchmatch")
                Log.d("SearchAdapter", "=== HTML CONVERSION DEBUG ===")
                Log.d("SearchAdapter", "Title: ${item.title}")
                Log.d("SearchAdapter", "Original snippet (${item.snippet.length} chars): ${item.snippet}")
                Log.d("SearchAdapter", "Contains searchmatch tags: $hasSearchMatch")
                Log.d("SearchAdapter", "Highlight color resolved: $highlightColorHex")
                
                val styledSnippet = item.snippet
                    .replace("<span class=\"searchmatch\">", "<b><font color='$highlightColorHex'>")
                    .replace("</span>", "</font></b>")
                
                Log.d("SearchAdapter", "Styled snippet: $styledSnippet")
                
                val htmlSpanned = HtmlCompat.fromHtml(
                    styledSnippet, 
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
                
                Log.d("SearchAdapter", "HtmlCompat result type: ${htmlSpanned.javaClass.simpleName}")
                Log.d("SearchAdapter", "Final text: '$htmlSpanned'")
                Log.d("SearchAdapter", "=== END DEBUG ===")
                
                binding.searchItemSnippet.text = htmlSpanned
                binding.searchItemSnippet.visibility = View.VISIBLE
            } else {
                binding.searchItemSnippet.text = null
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
