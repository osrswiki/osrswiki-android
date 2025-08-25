package com.omiyawaki.osrswiki.search

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
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
import com.omiyawaki.osrswiki.util.StringUtil
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
            
            // DIAGNOSTIC: Log the actual colors being used
            Log.d("SearchAdapter", "=== COLOR DIAGNOSTICS for '${item.title.take(20)}...' ===")
            Log.d("SearchAdapter", "Search query: ${searchQuery ?: "(none)"}")
            Log.d("SearchAdapter", "Title TextView currentTextColor BEFORE: #${Integer.toHexString(binding.searchItemTitle.currentTextColor)}")
            Log.d("SearchAdapter", "Snippet TextView currentTextColor BEFORE: #${Integer.toHexString(binding.searchItemSnippet.currentTextColor)}")
            
            // Title highlighting - implement title search term highlighting like iOS
            if (!searchQuery.isNullOrBlank()) {
                val highlightedTitle = highlightMatchesFullCoverage(
                    text = item.title,
                    query = searchQuery,
                    highlightColorHex = titleHighlightColorHex
                )
                binding.searchItemTitle.text = highlightedTitle
            } else {
                // Apply base color even without search query for consistency
                val spannableString = SpannableString(item.title)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                val baseColor = typedValue.data
                spannableString.setSpan(
                    ForegroundColorSpan(baseColor),
                    0,
                    item.title.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                binding.searchItemTitle.text = spannableString
            }
            // DIAGNOSTIC: Check spans before font application
            val titleTextBefore = binding.searchItemTitle.text
            Log.d("SearchAdapter", "Title text type: ${titleTextBefore?.javaClass?.simpleName}")
            var spansBeforeCount = 0
            if (titleTextBefore is Spanned) {
                val spansBefore = titleTextBefore.getSpans(0, titleTextBefore.length, ForegroundColorSpan::class.java)
                spansBeforeCount = spansBefore.size
                Log.d("SearchAdapter", "Title spans BEFORE font: ${spansBefore.size}")
                spansBefore.forEachIndexed { i, span ->
                    val start = titleTextBefore.getSpanStart(span)
                    val end = titleTextBefore.getSpanEnd(span)
                    Log.d("SearchAdapter", "  Span $i: [$start-$end] color=#${Integer.toHexString(span.foregroundColor)}")
                }
            } else {
                Log.d("SearchAdapter", "Title text is not Spanned")
            }
            
            binding.searchItemTitle.applyAlegreyaHeadline()
            
            // DIAGNOSTIC: Check spans after font application
            val titleTextAfter = binding.searchItemTitle.text
            if (titleTextAfter is Spanned) {
                val spansAfter = titleTextAfter.getSpans(0, titleTextAfter.length, ForegroundColorSpan::class.java)
                Log.d("SearchAdapter", "Title spans AFTER font: ${spansAfter.size}")
                if (spansAfter.isEmpty() && !searchQuery.isNullOrBlank()) {
                    Log.e("SearchAdapter", "🔴 Font application CLEARED the color spans!")
                } else if (spansAfter.size != spansBeforeCount) {
                    Log.e("SearchAdapter", "🔴 Font application changed span count from $spansBeforeCount to ${spansAfter.size}")
                }
            } else {
                Log.d("SearchAdapter", "Title text is not Spanned after font")
            }
            
            // DIAGNOSTIC: Log title color after font application
            Log.d("SearchAdapter", "Title TextView currentTextColor AFTER font: #${Integer.toHexString(binding.searchItemTitle.currentTextColor)}")

            if (item.snippet.isNotBlank()) {
                // Clean snippet text by properly decoding HTML entities and removing tags
                val htmlDecoded = StringUtil.fromHtml(item.snippet).toString()
                val cleanSnippet = htmlDecoded
                    .replace("<span class=\"searchmatch\">", "")
                    .replace("</span>", "")
                    .replace(Regex("<[^>]*>"), "") // Remove any remaining HTML tags
                
                // Apply unified highlighting like titles if search query exists
                if (!searchQuery.isNullOrBlank()) {
                    val highlightedSnippet = highlightMatchesFullCoverage(
                        text = cleanSnippet,
                        query = searchQuery,
                        highlightColorHex = snippetHighlightColorHex
                    )
                    binding.searchItemSnippet.text = highlightedSnippet
                } else {
                    // No query - show plain snippet, let TextView's textColor handle the color
                    binding.searchItemSnippet.text = cleanSnippet
                }
                
                binding.searchItemSnippet.visibility = View.VISIBLE
                
                // DIAGNOSTIC: Check spans in the text
                val snippetText = binding.searchItemSnippet.text
                if (snippetText is Spanned) {
                    val spans = snippetText.getSpans(0, snippetText.length, ForegroundColorSpan::class.java)
                    Log.d("SearchAdapter", "Snippet has ${spans.size} color spans")
                    spans.take(3).forEachIndexed { i, span ->
                        val start = snippetText.getSpanStart(span)
                        val end = snippetText.getSpanEnd(span)
                        Log.d("SearchAdapter", "  Snippet span $i: [$start-$end] color=#${Integer.toHexString(span.foregroundColor)}")
                    }
                }
                
                // DIAGNOSTIC: Log snippet color after text is set
                Log.d("SearchAdapter", "Snippet TextView currentTextColor AFTER text set: #${Integer.toHexString(binding.searchItemSnippet.currentTextColor)}")
                Log.d("SearchAdapter", "Snippet TextView alpha: ${binding.searchItemSnippet.alpha}")
                Log.d("SearchAdapter", "Snippet TextView paint alpha: ${binding.searchItemSnippet.paint.alpha}")
                Log.d("SearchAdapter", "Snippet TextView paint color: #${Integer.toHexString(binding.searchItemSnippet.paint.color)}")
                Log.d("SearchAdapter", "Snippet TextView textColors: ${binding.searchItemSnippet.textColors}")
                Log.d("SearchAdapter", "Snippet TextView linkTextColors: ${binding.searchItemSnippet.linkTextColors}")
                Log.d("SearchAdapter", "Snippet TextView hintTextColors: ${binding.searchItemSnippet.hintTextColors}")
                
                // DIAGNOSTIC: Check if colors match
                val titleColor = binding.searchItemTitle.currentTextColor
                val snippetColor = binding.searchItemSnippet.currentTextColor
                if (titleColor != snippetColor) {
                    Log.e("SearchAdapter", "🔴 COLOR MISMATCH! Title: #${Integer.toHexString(titleColor)}, Snippet: #${Integer.toHexString(snippetColor)}")
                    Log.e("SearchAdapter", "   Title decimal: $titleColor, Snippet decimal: $snippetColor")
                    Log.e("SearchAdapter", "   Difference: ${titleColor - snippetColor}")
                } else {
                    Log.d("SearchAdapter", "✅ Colors match: #${Integer.toHexString(titleColor)}")
                }
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
            
            val highlightColor = android.graphics.Color.parseColor(highlightColorHex)
            
            // Find and apply highlights only - let TextView's textColor handle the base color
            var searchIndex = 0
            var highlightCount = 0
            while (searchIndex < textLowerCase.length) {
                val index = textLowerCase.indexOf(queryLowerCase, searchIndex)
                if (index != -1) {
                    val endIndex = index + queryLowerCase.length
                    
                    // Apply highlight color and bold ONLY to the matched portion
                    spannableString.setSpan(
                        ForegroundColorSpan(highlightColor),
                        index,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannableString.setSpan(
                        StyleSpan(Typeface.BOLD),
                        index,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    highlightCount++
                    searchIndex = endIndex
                } else {
                    break
                }
            }
            
            Log.d("SearchAdapter", "highlightMatches: Applied $highlightCount highlights with color #${Integer.toHexString(highlightColor)}")
            Log.d("SearchAdapter", "highlightMatches: Non-highlighted text uses TextView's base textColor")
            
            return spannableString
        }

        /**
         * Highlights search term matches with FULL color span coverage.
         * This ensures every character has an explicit color, preventing fallback to default gray.
         */
        private fun highlightMatchesFullCoverage(text: String, query: String, highlightColorHex: String): SpannableString {
            // Use the same simplified approach as highlightMatches - only apply spans to highlights
            return highlightMatches(text, query, highlightColorHex)
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
