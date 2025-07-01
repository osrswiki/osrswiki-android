package com.omiyawaki.osrswiki.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ViewMainFeedSearchBinding

class MainFeedAdapter(private val callback: Callback) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // The fragment that hosts this adapter must implement this interface.
    interface Callback {
        fun onSearchRequested()
        fun onVoiceSearchRequested() // New callback for the mic icon
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
        return when (viewType) {
            VIEW_TYPE_SEARCH -> {
                val binding = ViewMainFeedSearchBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SearchCardViewHolder(binding, callback)
            }
            else -> throw IllegalStateException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is SearchCardViewHolder) {
            holder.bind()
        }
    }
}
