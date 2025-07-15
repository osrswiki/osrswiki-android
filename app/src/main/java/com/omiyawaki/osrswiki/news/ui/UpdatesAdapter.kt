package com.omiyawaki.osrswiki.news.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.news.model.UpdateItem

class UpdatesAdapter(
    private val items: List<UpdateItem>,
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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.news_item_image)
        private val titleView: TextView = itemView.findViewById(R.id.news_item_title)
        private val snippetView: TextView = itemView.findViewById(R.id.news_item_snippet)

        fun bind(item: UpdateItem, onItemClicked: (UpdateItem) -> Unit) {
            titleView.text = item.title
            snippetView.text = item.snippet.replace('’', '\'')

            val app = itemView.context.applicationContext as OSRSWikiApp
            val cachedBitmap = app.imageCache.get(item.imageUrl)

            if (cachedBitmap != null) {
                imageView.setImageBitmap(cachedBitmap)
            } else {
                Glide.with(itemView.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_placeholder_image)
                    .error(R.drawable.ic_error_image)
                    .dontAnimate()
                    .into(imageView)
            }

            itemView.setOnClickListener {
                onItemClicked(item)
            }
        }
    }
}