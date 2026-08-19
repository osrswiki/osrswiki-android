package com.omiyawaki.osrswiki.news.ui

import android.annotation.SuppressLint
import android.graphics.Rect
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
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.image.ImageLoader
import com.omiyawaki.osrswiki.util.applyAlegreyaSmallCaps
import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.PopularPageItem
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.page.preemptive.ArticlePrewarmRequest
import com.omiyawaki.osrswiki.page.preemptive.VisibleRowViewportPolicy

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
    private val imageLoader: ImageLoader,
    private val onUpdateItemClicked: (UpdateItem) -> Unit,
    private val onLinkClicked: (url: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FeedItem>()
    private var updatesViewHolder: UpdatesViewHolder? = null
    private var prewarmVisibilityChanged: (() -> Unit)? = null

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
    
    fun updateLastUpdatedText(text: String) {
        updatesViewHolder?.updateLastUpdatedText(text)
    }

    fun setPrewarmVisibilityListener(listener: (() -> Unit)?) {
        prewarmVisibilityChanged = listener
    }

    /** Every actually visible tappable article in a grouped home row is independently eligible. */
    fun prewarmCandidatesAt(position: Int, rowView: View): Set<ArticlePrewarmRequest> {
        return when (val item = items.getOrNull(position)) {
            is FeedItem.Updates -> {
                val nested = rowView.findViewById<RecyclerView>(R.id.updates_recycler_view)
                visibleRecyclerPositions(nested).mapNotNull { childPosition ->
                    item.items.getOrNull(childPosition)?.let { update ->
                        ArticlePrewarmRequest.fromWikiUrl(update.articleUrl, update.title)
                    }
                }.toSet()
            }
            is FeedItem.Announcement -> internalArticles(item.item.content)
            is FeedItem.OnThisDay -> {
                val container = rowView.findViewById<LinearLayout>(R.id.on_this_day_content_container)
                visibleLinearChildIndices(container).flatMap { index ->
                    item.item.events.getOrNull(index)?.let(::internalArticles).orEmpty()
                }.toSet()
            }
            is FeedItem.Popular -> {
                val container = rowView.findViewById<LinearLayout>(R.id.popular_content_container)
                visibleLinearChildIndices(container).mapNotNull { index ->
                    item.items.getOrNull(index)?.let { popular ->
                        ArticlePrewarmRequest.fromWikiUrl(popular.pageUrl, popular.title)
                    }
                }.toSet()
            }
            null -> emptySet()
        }
    }

    private fun internalArticles(html: String): Set<ArticlePrewarmRequest> {
        val href = Regex("""href=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
        return href.findAll(html).mapNotNull { match ->
            match.groupValues.getOrNull(1)?.let(ArticlePrewarmRequest::fromWikiUrl)
        }.toSet()
    }

    private fun visibleRecyclerPositions(recyclerView: RecyclerView): List<Int> {
        val viewport = Rect()
        if (!recyclerView.getGlobalVisibleRect(viewport)) return emptyList()
        return recyclerView.children.mapNotNull { child ->
            if (!intersectsVisibleViewport(child, viewport)) return@mapNotNull null
            recyclerView.getChildAdapterPosition(child).takeUnless { it == RecyclerView.NO_POSITION }
        }.toList()
    }

    private fun visibleLinearChildIndices(container: LinearLayout): List<Int> {
        val viewport = Rect()
        if (!container.getGlobalVisibleRect(viewport)) return emptyList()
        return container.children.mapIndexedNotNull { index, child ->
            index.takeIf { intersectsVisibleViewport(child, viewport) }
        }.toList()
    }

    private fun intersectsVisibleViewport(child: View, viewport: Rect): Boolean {
        val childBounds = Rect()
        if (!child.getGlobalVisibleRect(childBounds)) return false
        return VisibleRowViewportPolicy.intersectsViewport(
            child.isShown,
            viewport.left,
            viewport.top,
            viewport.right,
            viewport.bottom,
            childBounds.left,
            childBounds.top,
            childBounds.right,
            childBounds.bottom
        )
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
            VIEW_TYPE_UPDATES -> {
                val holder = UpdatesViewHolder(
                    inflater.inflate(R.layout.item_news_card_updates, parent, false)
                )
                updatesViewHolder = holder
                holder
            }
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

    inner class UpdatesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sectionTitle: TextView = itemView.findViewById(R.id.updates_section_title)
        private val lastUpdatedText: TextView = itemView.findViewById(R.id.last_updated_text)
        private val nestedRecyclerView: RecyclerView = itemView.findViewById(R.id.updates_recycler_view)
        private val childBounds = Rect()
        private var accessibilityListenersAttached = false
        
        init {
            nestedRecyclerView.clipChildren = false
            nestedRecyclerView.clipToPadding = false
            sectionTitle.applyAlegreyaSmallCaps()
        }
        
        fun bind(items: List<UpdateItem>, listener: (UpdateItem) -> Unit) {
            nestedRecyclerView.adapter = UpdatesAdapter(items, imageLoader, listener)
            attachAccessibilityListenersOnce()
            nestedRecyclerView.post {
                updateCarouselChildAccessibility()
                prewarmVisibilityChanged?.invoke()
            }
        }
        
        fun updateLastUpdatedText(text: String) {
            lastUpdatedText.text = text
        }

        private fun attachAccessibilityListenersOnce() {
            if (accessibilityListenersAttached) {
                return
            }
            accessibilityListenersAttached = true

            nestedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateCarouselChildAccessibility()
                    prewarmVisibilityChanged?.invoke()
                }
            })
            nestedRecyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    nestedRecyclerView.post {
                        updateCarouselChildAccessibility()
                        prewarmVisibilityChanged?.invoke()
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
        }

        private fun updateCarouselChildAccessibility() {
            val viewportStart = nestedRecyclerView.paddingLeft
            val viewportEnd = nestedRecyclerView.width - nestedRecyclerView.paddingRight
            if (viewportEnd <= viewportStart) {
                return
            }

            nestedRecyclerView.children.forEach { child ->
                nestedRecyclerView.getDecoratedBoundsWithMargins(child, childBounds)
                val fullyVisible = NewsAccessibilityPolicy.isCarouselChildFullyVisible(
                    viewportStart = viewportStart,
                    viewportEnd = viewportEnd,
                    childStart = childBounds.left,
                    childEnd = childBounds.right
                )
                child.importantForAccessibility = if (fullyVisible) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
                child.isFocusable = fullyVisible
                // Peeking cards stay tappable; TalkBack still only focuses fully visible ones.
                child.isClickable = true
            }
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
        
        @SuppressLint("WrongConstant")
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
        private val contentContainer: LinearLayout = itemView.findViewById(R.id.popular_content_container)
        
        init {
            // Apply fonts on ViewHolder creation
            title.applyAlegreyaSmallCaps()
        }
        
        fun bind(items: List<PopularPageItem>, onLinkClick: (url: String) -> Unit) {
            contentContainer.removeAllViews()
            items.forEach { item ->
                val linkView = TextView(itemView.context).apply {
                    text = item.title
                    setTextAppearance(R.style.AppTextAppearance_BodyMedium)
                    setTextColor(resolveThemeColor(R.attr.linkColor))
                    contentDescription = NewsAccessibilityPolicy.popularPageDescription(item.title)
                    isClickable = true
                    isFocusable = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    setOnClickListener { onLinkClick(item.pageUrl) }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                contentContainer.addView(linkView)
            }
        }

        private fun TextView.resolveThemeColor(attr: Int): Int {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(attr, typedValue, true)
            return typedValue.data
        }
    }
}
