package com.omiyawaki.osrswiki.ui.main

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ViewMainFeedSearchBinding

class MainFeedAdapter(private val callback: Callback) :
    RecyclerView.Adapter<MainFeedAdapter.SearchCardViewHolder>() {

    interface Callback {
        fun onSearchRequested(view: android.view.View)
        fun onVoiceSearchRequested()
    }

    private val items = mutableListOf<Int>()

    companion object {
        private const val VIEW_TYPE_SEARCH = 0
    }

    init {
        items.add(VIEW_TYPE_SEARCH)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = items[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchCardViewHolder {
        val binding = ViewMainFeedSearchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchCardViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: SearchCardViewHolder, position: Int) {
        holder.bind()
    }

    class SearchCardViewHolder(
        private val binding: ViewMainFeedSearchBinding,
        private val callback: Callback
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.searchContainer.setOnClickListener { callback.onSearchRequested(binding.searchContainer) }
            binding.voiceSearchButton.setOnClickListener { callback.onVoiceSearchRequested() }
            
            // Background color is now handled by the standardized search bar drawable
        }
    }
}
