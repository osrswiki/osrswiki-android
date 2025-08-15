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

class UpdatesAdapter(
    private val items: List<UpdateItem>,
    private val imageLoader: ImageLoader,
    private val onItemClicked: (UpdateItem) -> Unit
) : RecyclerView.Adapter<UpdatesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_update, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClicked)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.news_item_image)
        private val titleView: TextView = itemView.findViewById(R.id.news_item_title)
        private val snippetView: TextView = itemView.findViewById(R.id.news_item_snippet)

        init {
            // Apply fonts on ViewHolder creation
            titleView.applyAlegreyaTitle()
        }

        fun bind(item: UpdateItem, onItemClicked: (UpdateItem) -> Unit) {
            titleView.text = item.title
            snippetView.text = item.snippet.replace("'", "'")

            // Use injected ImageLoader instead of direct Glide calls
            // This enables dependency injection for preview vs production contexts
            imageLoader.load(imageView, item.imageUrl)

            itemView.setOnClickListener {
                onItemClicked(item)
            }
        }
    }
}