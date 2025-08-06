package com.omiyawaki.osrswiki.news.ui

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.applyAlegreyaSmallCaps
import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.PopularPageItem
import com.omiyawaki.osrswiki.news.model.UpdateItem

// Helper function moved to top-level to be accessible by all classes in this file.
private fun TextView.setTextWithClickableLinks(html: String, onLinkClick: (url: String) -> Unit) {
    val sequence = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val strBuilder = SpannableStringBuilder(sequence)
    val urls = strBuilder.getSpans(0, sequence.length, URLSpan::class.java)
    for (span in urls) {
        val start = strBuilder.getSpanStart(span)
        val end = strBuilder.getSpanEnd(span)
        val flags = strBuilder.getSpanFlags(span)
        val clickable = object : ClickableSpan() {
            override fun onClick(view: View) {
                onLinkClick(span.url)
            }
        }
        strBuilder.setSpan(clickable, start, end, flags)
        strBuilder.removeSpan(span)
    }
    text = strBuilder
    movementMethod = LinkMovementMethod.getInstance()
}

// Helper function to set text with mixed fonts - monospace for year/dash, regular for the rest
private fun TextView.setTextWithMixedFonts(html: String, onLinkClick: (url: String) -> Unit) {
    val sequence = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val strBuilder = SpannableStringBuilder(sequence)
    
    // Handle clickable links first
    val urls = strBuilder.getSpans(0, sequence.length, URLSpan::class.java)
    for (span in urls) {
        val start = strBuilder.getSpanStart(span)
        val end = strBuilder.getSpanEnd(span)
        val flags = strBuilder.getSpanFlags(span)
        val clickable = object : ClickableSpan() {
            override fun onClick(view: View) {
                onLinkClick(span.url)
            }
        }
        strBuilder.setSpan(clickable, start, end, flags)
        strBuilder.removeSpan(span)
    }
    
    // Apply system monospace to year and dash pattern (e.g., "• 2024 – " or "• 2006 – ")
    val yearDashPattern = Regex("^(• \\d{4} – )")
    val match = yearDashPattern.find(strBuilder.toString())
    if (match != null) {
        val start = match.range.first
        val end = match.range.last + 1
        
        // Use system monospace font
        strBuilder.setSpan(
            TypefaceSpan("monospace"),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    
    text = strBuilder
    movementMethod = LinkMovementMethod.getInstance()
}

/**
 * A sealed class representing all possible items that can be displayed in the news feed.
 */
sealed class FeedItem {
    data class Updates(val items: List<UpdateItem>) : FeedItem()
    data class Announcement(val item: AnnouncementItem) : FeedItem()
    data class OnThisDay(val item: OnThisDayItem) : FeedItem()
    data class Popular(val items: List<PopularPageItem>) : FeedItem()
}

/**
 * RecyclerView.Adapter for the main news feed in NewsFragment.
 */
class NewsFeedAdapter(
    private val onUpdateItemClicked: (UpdateItem) -> Unit,
    private val onLinkClicked: (url: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FeedItem>()

    companion object {
        private const val VIEW_TYPE_UPDATES = 0
        private const val VIEW_TYPE_ANNOUNCEMENT = 1
        private const val VIEW_TYPE_ON_THIS_DAY = 2
        private const val VIEW_TYPE_POPULAR = 3
        private const val TAG = "NewsFeedAdapter"
    }

    fun setItems(newItems: List<FeedItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FeedItem.Updates -> VIEW_TYPE_UPDATES
            is FeedItem.Announcement -> VIEW_TYPE_ANNOUNCEMENT
            is FeedItem.OnThisDay -> VIEW_TYPE_ON_THIS_DAY
            is FeedItem.Popular -> VIEW_TYPE_POPULAR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_UPDATES -> UpdatesViewHolder(
                inflater.inflate(R.layout.item_news_card_updates, parent, false)
            )
            VIEW_TYPE_ANNOUNCEMENT -> AnnouncementViewHolder(
                inflater.inflate(R.layout.item_news_card_announcements, parent, false)
            )
            VIEW_TYPE_ON_THIS_DAY -> OnThisDayViewHolder(
                inflater.inflate(R.layout.item_news_card_on_this_day, parent, false)
            )
            VIEW_TYPE_POPULAR -> PopularViewHolder(
                inflater.inflate(R.layout.item_news_card_popular, parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FeedItem.Updates -> (holder as UpdatesViewHolder).bind(item.items, onUpdateItemClicked)
            is FeedItem.Announcement -> (holder as AnnouncementViewHolder).bind(item.item, onLinkClicked)
            is FeedItem.OnThisDay -> (holder as OnThisDayViewHolder).bind(item.item, onLinkClicked)
            is FeedItem.Popular -> (holder as PopularViewHolder).bind(item.items, onLinkClicked)
        }
    }

    override fun getItemCount(): Int = items.size

    class UpdatesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sectionTitle: TextView = itemView.findViewById(R.id.updates_section_title)
        private val nestedRecyclerView: RecyclerView = itemView.findViewById(R.id.updates_recycler_view)
        
        init {
            // Apply fonts on ViewHolder creation
            sectionTitle.applyAlegreyaSmallCaps()
        }
        
        fun bind(items: List<UpdateItem>, listener: (UpdateItem) -> Unit) {
            nestedRecyclerView.adapter = UpdatesAdapter(items, listener)
        }
    }

    class AnnouncementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.announcement_card_title)
        private val content: TextView = itemView.findViewById(R.id.announcement_content)
        
        init {
            // Apply fonts on ViewHolder creation
            title.applyAlegreyaSmallCaps()
        }
        
        fun bind(item: AnnouncementItem, onLinkClick: (url: String) -> Unit) {
            val fullContent = "${item.date}: ${item.content}"
            Log.d(TAG, "Announcement content to parse: $fullContent")
            content.setTextWithClickableLinks(fullContent, onLinkClick)
        }
    }

    class OnThisDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.on_this_day_card_title)
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.on_this_day_content_container)
        
        init {
            // Apply fonts on ViewHolder creation
            title.applyAlegreyaSmallCaps()
        }
        
        fun bind(item: OnThisDayItem, onLinkClick: (url: String) -> Unit) {
            title.text = item.title
            
            // Clear any existing TextViews
            contentContainer.removeAllViews()
            
            // Create individual TextView for each event
            item.events.forEach { event ->
                val eventTextView = TextView(itemView.context).apply {
                    // Set appearance and behavior for single-line truncation with consistent widths
                    setSingleLine(true)  // More aggressive than maxLines = 1, allows mid-word breaks
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    
                    // Apply proper text appearance matching original XML
                    setTextAppearance(R.style.AppTextAppearance_BodyMedium)
                    // Using system font via text appearance - no custom font needed
                    
                    // Get link color from theme attribute
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(R.attr.linkColor, typedValue, true)
                    setLinkTextColor(typedValue.data)
                    
                    // Add break strategy for API 23+ to allow mid-word breaks
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    }
                    
                    // Set the content with bullet point and mixed fonts
                    val htmlContent = "• $event"
                    setTextWithMixedFonts(htmlContent, onLinkClick)
                    
                    // Set layout params
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                contentContainer.addView(eventTextView)
            }
            
            Log.d(TAG, "OnThisDay created ${item.events.size} individual TextViews")
        }
    }

    class PopularViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.popular_card_title)
        private val content: TextView = itemView.findViewById(R.id.popular_content)
        
        init {
            // Apply fonts on ViewHolder creation
            title.applyAlegreyaSmallCaps()
        }
        
        fun bind(items: List<PopularPageItem>, onLinkClick: (url: String) -> Unit) {
            val htmlLinks = items.joinToString("<br>") {
                "<a href=\"${it.pageUrl}\">${it.title}</a>"
            }
            content.setTextWithClickableLinks(htmlLinks, onLinkClick)
        }
    }
}
