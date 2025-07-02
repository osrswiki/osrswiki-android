package com.omiyawaki.osrswiki.news.ui

import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.news.model.AnnouncementItem
import com.omiyawaki.osrswiki.news.model.OnThisDayItem
import com.omiyawaki.osrswiki.news.model.PopularPageItem
import com.omiyawaki.osrswiki.news.model.UpdateItem

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
    private val onUpdateItemClicked: (UpdateItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<FeedItem>()

    companion object {
        private const val VIEW_TYPE_UPDATES = 0
        private const val VIEW_TYPE_ANNOUNCEMENT = 1
        private const val VIEW_TYPE_ON_THIS_DAY = 2
        private const val VIEW_TYPE_POPULAR = 3
    }

    fun setItems(newItems: List<FeedItem>) {
        items.clear()
        items.addAll(newItems)
        // In a real implementation, use DiffUtil for better performance
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
            is FeedItem.Announcement -> (holder as AnnouncementViewHolder).bind(item.item)
            is FeedItem.OnThisDay -> (holder as OnThisDayViewHolder).bind(item.item)
            is FeedItem.Popular -> (holder as PopularViewHolder).bind(item.items)
        }
    }

    override fun getItemCount(): Int = items.size

    class UpdatesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nestedRecyclerView: RecyclerView = itemView.findViewById(R.id.updates_recycler_view)
        fun bind(items: List<UpdateItem>, listener: (UpdateItem) -> Unit) {
            nestedRecyclerView.adapter = UpdatesAdapter(items, listener)
        }
    }

    class AnnouncementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val content: TextView = itemView.findViewById(R.id.announcement_content)
        fun bind(item: AnnouncementItem) {
            content.text = "${item.date}: ${item.content}"
        }
    }

    class OnThisDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.on_this_day_card_title)
        private val content: TextView = itemView.findViewById(R.id.on_this_day_content)
        fun bind(item: OnThisDayItem) {
            title.text = item.title
            content.text = item.events.joinToString("\n") { "• $it" }
        }
    }

    class PopularViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val content: TextView = itemView.findViewById(R.id.popular_content)
        fun bind(items: List<PopularPageItem>) {
            val htmlLinks = items.joinToString("<br>") {
                "<a href=\"${it.pageUrl}\">${it.title}</a>"
            }
            content.text = Html.fromHtml(htmlLinks, Html.FROM_HTML_MODE_COMPACT)
            content.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
