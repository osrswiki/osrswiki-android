package com.omiyawaki.osrswiki.history

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.omiyawaki.osrswiki.databinding.ItemHistoryEntryRichBinding
import com.omiyawaki.osrswiki.databinding.ViewHistoryDateHeaderBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.StringUtil
import com.omiyawaki.osrswiki.R

class HistoryAdapter(
    private val onItemClick: (HistoryEntry) -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 1
        private const val VIEW_TYPE_ENTRY = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryItem.DateHeader -> VIEW_TYPE_DATE_HEADER
            is HistoryItem.EntryItem -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> {
                val binding = ViewHistoryDateHeaderBinding.inflate(inflater, parent, false)
                DateHeaderViewHolder(binding)
            }
            VIEW_TYPE_ENTRY -> {
                val binding = ItemHistoryEntryRichBinding.inflate(inflater, parent, false)
                EntryViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.dateString)
            }
            is HistoryItem.EntryItem -> {
                (holder as EntryViewHolder).bind(item.historyEntry)
            }
        }
    }

    inner class DateHeaderViewHolder(
        private val binding: ViewHistoryDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dateString: String) {
            binding.dateHeaderText.text = dateString
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemHistoryEntryRichBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(historyEntry: HistoryEntry) {
            binding.apply {
                // Set page title - clean HTML from display text
                val cleanTitle = StringUtil.fromHtml(historyEntry.pageTitle.displayText).toString().trim()
                pageTitleText.text = cleanTitle

                // Set snippet text from history entry or hide if empty
                if (!historyEntry.snippet.isNullOrBlank()) {
                    pageSnippetText.text = historyEntry.snippet
                    pageSnippetText.visibility = View.VISIBLE
                } else {
                    pageSnippetText.visibility = View.GONE
                }


                // Load the thumbnail if the URL exists, otherwise hide the image view
                if (!historyEntry.thumbnailUrl.isNullOrBlank()) {
                    pageThumbnail.visibility = View.VISIBLE
                    Glide.with(binding.root.context)
                        .load(historyEntry.thumbnailUrl)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(pageThumbnail)
                } else {
                    // Clear the view and set visibility to GONE to handle view recycling correctly
                    Glide.with(binding.root.context).clear(pageThumbnail)
                    pageThumbnail.visibility = View.GONE
                }

                // Set click listener
                root.setOnClickListener {
                    onItemClick(historyEntry)
                }

            }
        }
    }

    class HistoryItemDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return when {
                oldItem is HistoryItem.DateHeader && newItem is HistoryItem.DateHeader -> 
                    oldItem.dateString == newItem.dateString
                oldItem is HistoryItem.EntryItem && newItem is HistoryItem.EntryItem -> 
                    oldItem.historyEntry.id == newItem.historyEntry.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}