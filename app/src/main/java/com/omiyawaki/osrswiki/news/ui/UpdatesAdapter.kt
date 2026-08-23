package com.omiyawaki.osrswiki.news.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.image.ImageLoader
import com.omiyawaki.osrswiki.news.model.UpdateItem
import com.omiyawaki.osrswiki.util.applyAlegreyaTitle
import com.omiyawaki.osrswiki.util.StringUtil

class UpdatesAdapter(
    private val items: List<UpdateItem>,
    private val imageLoader: ImageLoader,
    private val onItemClicked: (UpdateItem) -> Unit,
    private val onViewMoreClicked: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_UPDATE = 0
        private const val TYPE_VIEW_MORE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (onViewMoreClicked != null && position == items.size) TYPE_VIEW_MORE else TYPE_UPDATE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_VIEW_MORE) {
            ViewMoreViewHolder(inflater.inflate(R.layout.item_news_updates_view_more, parent, false))
        } else {
            UpdateViewHolder(inflater.inflate(R.layout.item_news_update, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UpdateViewHolder -> holder.bind(items[position], onItemClicked)
            is ViewMoreViewHolder -> holder.bind(onViewMoreClicked)
        }
    }

    override fun getItemCount(): Int = items.size + if (onViewMoreClicked != null) 1 else 0

    inner class UpdateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.news_item_image)
        private val titleView: TextView = itemView.findViewById(R.id.news_item_title)
        private val snippetView: TextView = itemView.findViewById(R.id.news_item_snippet)

        init {
            titleView.applyAlegreyaTitle()
        }

        fun bind(item: UpdateItem, onItemClicked: (UpdateItem) -> Unit) {
            titleView.text = StringUtil.extractMainTitle(item.title)
            snippetView.text = item.snippet.replace("'", "'")
            itemView.contentDescription = NewsAccessibilityPolicy.updateCardDescription(
                title = titleView.text.toString(),
                snippet = snippetView.text.toString()
            )
            itemView.isFocusable = true
            itemView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            imageView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            titleView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            snippetView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            imageLoader.load(imageView, item.imageUrl)
            itemView.setOnClickListener { onItemClicked(item) }
        }
    }

    inner class ViewMoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewMore: TextView = itemView.findViewById(R.id.home_updates_view_more)

        fun bind(onViewMore: (() -> Unit)?) {
            viewMore.setOnClickListener { onViewMore?.invoke() }
        }
    }
}
