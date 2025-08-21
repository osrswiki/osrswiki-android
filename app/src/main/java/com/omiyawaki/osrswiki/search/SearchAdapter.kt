package com.omiyawaki.osrswiki.search

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline
import java.util.Locale

class SearchAdapter(
    private val onItemClickListener: OnItemClickListener
) : PagingDataAdapter<CleanedSearchResultItem, SearchAdapter.SearchResultViewHolder>(
    SEARCH_RESULT_COMPARATOR
) {

    private var currentSearchQuery: String? = null

    fun updateSearchQuery(query: String?) {
        currentSearchQuery = query
        notifyDataSetChanged()
    }

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
            holder.bind(item, currentSearchQuery)
        }
    }

    class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CleanedSearchResultItem, searchQuery: String?) {
            val context = binding.root.context
            
            // Get theme-appropriate highlight colors
            val highlightColors = getSearchHighlightColors(context)
            val titleHighlightColorHex = String.format("#%06X", (0xFFFFFF and highlightColors.first))
            val snippetHighlightColorHex = String.format("#%06X", (0xFFFFFF and highlightColors.second))
            
            // Title highlighting - implement title search term highlighting like iOS
            if (!searchQuery.isNullOrBlank()) {
                val highlightedTitle = highlightMatches(
                    text = item.title,
                    query = searchQuery,
                    highlightColorHex = titleHighlightColorHex
                )
                binding.searchItemTitle.text = highlightedTitle
            } else {
                binding.searchItemTitle.text = item.title
            }
            binding.searchItemTitle.applyAlegreyaHeadline()

            if (item.snippet.isNotBlank()) {
                // Clean snippet text by removing HTML tags first
                val cleanSnippet = item.snippet
                    .replace("<span class=\"searchmatch\">", "")
                    .replace("</span>", "")
                    .replace(Regex("<[^>]*>"), "") // Remove any other HTML tags
                
                // Apply unified highlighting like titles if search query exists
                if (!searchQuery.isNullOrBlank()) {
                    val highlightedSnippet = highlightMatches(
                        text = cleanSnippet,
                        query = searchQuery,
                        highlightColorHex = snippetHighlightColorHex
                    )
                    binding.searchItemSnippet.text = highlightedSnippet
                } else {
                    binding.searchItemSnippet.text = cleanSnippet
                }
                
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

        /**
         * Highlights search term matches in text using spans, similar to iOS implementation.
         * Performs case-insensitive matching and applies both color and bold formatting.
         */
        private fun highlightMatches(text: String, query: String, highlightColorHex: String): SpannableString {
            val spannableString = SpannableString(text)
            val textLowerCase = text.lowercase(Locale.getDefault())
            val queryLowerCase = query.lowercase(Locale.getDefault())
            
            var startIndex = 0
            while (startIndex < textLowerCase.length) {
                val index = textLowerCase.indexOf(queryLowerCase, startIndex)
                if (index != -1) {
                    val endIndex = index + queryLowerCase.length
                    
                    // Apply color highlight
                    val highlightColor = android.graphics.Color.parseColor(highlightColorHex)
                    spannableString.setSpan(
                        ForegroundColorSpan(highlightColor),
                        index,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    // Apply bold formatting
                    spannableString.setSpan(
                        StyleSpan(Typeface.BOLD),
                        index,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    Log.d("SearchAdapter", "Title highlight: found '$queryLowerCase' at $index-$endIndex in '$text'")
                    
                    startIndex = endIndex
                } else {
                    break
                }
            }
            
            return spannableString
        }

        /**
         * Gets unified highlight color for both title and snippet across all themes.
         * Uses osrs_text_secondary_light for consistent, cohesive highlighting.
         */
        private fun getSearchHighlightColors(context: Context): Pair<Int, Int> {
            // Use the same brown color for all highlighting in both themes
            val unifiedHighlightColor = ContextCompat.getColor(
                context, 
                com.omiyawaki.osrswiki.R.color.osrs_text_secondary_light  // #8B7355
            )
            
            // Return same color for both title and snippet highlighting
            return Pair(unifiedHighlightColor, unifiedHighlightColor)
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
