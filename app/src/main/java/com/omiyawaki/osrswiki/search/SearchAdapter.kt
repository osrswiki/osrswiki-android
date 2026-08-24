package com.omiyawaki.osrswiki.search

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
import com.omiyawaki.osrswiki.util.StringUtil
import com.omiyawaki.osrswiki.util.applyAlegreyaHeadline

class SearchAdapter(
    private val onItemClickListener: OnItemClickListener,
    var previewStore: osrsSearchPreviewStore? = null
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
            val override = item.id.toIntOrNull()?.let { previewStore?.snippetFor(it) }
            val bound = if (!override.isNullOrBlank()) item.copy(snippet = override) else item
            holder.bind(bound, currentSearchQuery)
        }
    }

    class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val listener: OnItemClickListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CleanedSearchResultItem, searchQuery: String?) {
            val context = binding.root.context
            val displayTitle = StringUtil.extractMainTitle(item.title)
            
            // Get theme-appropriate highlight colors
            val highlightColors = getSearchHighlightColors(context)
            val titleHighlightColorHex = String.format("#%06X", (0xFFFFFF and highlightColors.first))
            val snippetHighlightColorHex = String.format("#%06X", (0xFFFFFF and highlightColors.second))
            
            if (!searchQuery.isNullOrBlank()) {
                binding.searchItemTitle.text = highlightMatches(
                    text = displayTitle,
                    ranges = SearchQueryPolicy.titleHighlightRanges(displayTitle, searchQuery),
                    highlightColorHex = titleHighlightColorHex,
                )
            } else {
                val spannableString = SpannableString(displayTitle)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                val baseColor = typedValue.data
                spannableString.setSpan(
                    ForegroundColorSpan(baseColor),
                    0,
                    displayTitle.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                binding.searchItemTitle.text = spannableString
            }
            binding.searchItemTitle.applyAlegreyaHeadline()

            var cleanSnippet = ""
            if (item.snippet.isNotBlank()) {
                cleanSnippet = cleanSearchSnippet(item.snippet)

                // Apply unified highlighting like titles if search query exists
                if (!searchQuery.isNullOrBlank()) {
                    val highlightedSnippet = highlightMatches(
                        text = cleanSnippet,
                        ranges = SearchQueryPolicy.snippetHighlightRanges(cleanSnippet, searchQuery),
                        highlightColorHex = snippetHighlightColorHex,
                    )
                    binding.searchItemSnippet.text = highlightedSnippet
                } else {
                    // No query - show plain snippet, let TextView's textColor handle the color
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

            binding.root.contentDescription = searchResultDescription(
                title = displayTitle,
                snippet = cleanSnippet
            )
            binding.root.isFocusable = true
            binding.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            binding.searchItemTitle.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            binding.searchItemSnippet.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            binding.searchItemThumbnail.contentDescription = null
            binding.searchItemThumbnail.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

            binding.root.setOnClickListener {
                listener.onItemClick(item)
            }
        }

        private fun cleanSearchSnippet(snippet: String): String {
            val htmlDecoded = StringUtil.decodeHtmlToFixedPoint(snippet)
            return htmlDecoded
                .replace("<span class=\"searchmatch\">", "")
                .replace("</span>", "")
                .replace(Regex("<[^>]*>"), "")
        }

        private fun searchResultDescription(title: String, snippet: String): String {
            return listOf(title.asSentence(), snippet.asSentence(), "Opens article.")
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }

        private fun String.asSentence(): String {
            val clean = trim()
            if (clean.isEmpty()) {
                return ""
            }
            return if (clean.last() in setOf('.', '!', '?')) clean else "$clean."
        }

        /**
         * Highlights search term matches in text using spans, similar to iOS implementation.
         * Performs case-insensitive matching and applies both color and bold formatting.
         */
        private fun highlightMatches(
            text: String,
            ranges: List<SearchQueryPolicy.HighlightRange>,
            highlightColorHex: String,
        ): SpannableString {
            val spannableString = SpannableString(text)
            val highlightColor = android.graphics.Color.parseColor(highlightColorHex)
            ranges.forEach { range ->
                if (range.startInclusive !in 0..text.length ||
                    range.endExclusive !in 0..text.length ||
                    range.startInclusive >= range.endExclusive
                ) return@forEach
                spannableString.setSpan(
                    ForegroundColorSpan(highlightColor),
                    range.startInclusive,
                    range.endExclusive,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannableString.setSpan(
                    StyleSpan(Typeface.BOLD),
                    range.startInclusive,
                    range.endExclusive,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return spannableString
        }
        
        /**
         * Gets unified highlight color for both title and snippet across all themes.
         * Uses osrs_text_secondary_light for consistent, cohesive highlighting.
         */
        private fun getSearchHighlightColors(context: Context): Pair<Int, Int> {
            // Use the dedicated high-contrast search highlight in both title and snippet.
            val unifiedHighlightColor = ContextCompat.getColor(
                context, 
                com.omiyawaki.osrswiki.R.color.search_highlight_light
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
