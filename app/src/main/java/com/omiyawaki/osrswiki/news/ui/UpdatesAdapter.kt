package com.omiyawaki.osrswiki.news.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.news.model.UpdateItem

/**
 * A nested adapter to display the horizontal list of "Recent Update" cards.
 */
class UpdatesAdapter(private val items: List<UpdateItem>) :
    RecyclerView.Adapter<UpdatesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_update, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.news_item_image)
        private val titleView: TextView = itemView.findViewById(R.id.news_item_title)
        private val snippetView: TextView = itemView.findViewById(R.id.news_item_snippet)

        fun bind(item: UpdateItem) {
            titleView.text = item.title
            snippetView.text = item.snippet

            // Add granular logging for each line's position.
            itemView.post {
                val tag = "UpdatesAdapter"
                Log.d(tag, "--- Line Diagnostics for '${item.title}' ---")
                Log.d(tag, " Snippet Height: ${snippetView.height}px")
                val layout = snippetView.layout
                if (layout != null) {
                    for (i in 0 until layout.lineCount) {
                        Log.d(tag, "   Line $i Top: ${layout.getLineTop(i)}px | Baseline: ${layout.getLineBaseline(i)}px")
                    }
                }
                Log.d(tag, "=======================================================")
            }

            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .into(imageView)
        }
    }
}
