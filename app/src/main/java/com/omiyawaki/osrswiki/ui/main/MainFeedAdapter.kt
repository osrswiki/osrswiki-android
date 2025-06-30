package com.omiyawaki.osrswiki.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R

class MainFeedAdapter(private val callback: Callback) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callback {
        fun onSearchRequested()
    }

    private val items = mutableListOf<Int>()

    companion object {
        private const val VIEW_TYPE_SEARCH = 0
        // Add other view types for News, Saved Pages, etc. here later
    }

    init {
        // For now, the feed only contains the search card.
        items.add(VIEW_TYPE_SEARCH)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEARCH -> SearchCardViewHolder(
                inflater.inflate(R.layout.view_main_search_bar, parent, false)
            )
            else -> throw IllegalStateException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is SearchCardViewHolder) {
            holder.setCallback(callback)
        }
    }

    class SearchCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun setCallback(callback: Callback) {
            itemView.setOnClickListener {
                callback.onSearchRequested()
            }
        }
    }
}
