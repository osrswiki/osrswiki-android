package com.omiyawaki.osrswiki.history

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemHistoryEntryBinding
import com.omiyawaki.osrswiki.databinding.ViewHistoryDateHeaderBinding
import com.omiyawaki.osrswiki.databinding.ViewHistorySearchCardBinding
import com.omiyawaki.osrswiki.history.db.HistoryEntry
import com.omiyawaki.osrswiki.util.StringUtil

class HistoryAdapter(
    private val onItemClick: (HistoryEntry) -> Unit,
    private val onItemDelete: (HistoryEntry) -> Unit,
    private val onSearchClick: () -> Unit,
    private val onClearHistoryClick: () -> Unit
) : ListAdapter<HistoryItem, RecyclerView.ViewHolder>(HistoryItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SEARCH_CARD = 0
        private const val VIEW_TYPE_DATE_HEADER = 1
        private const val VIEW_TYPE_ENTRY = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryItem.SearchCard -> VIEW_TYPE_SEARCH_CARD
            is HistoryItem.DateHeader -> VIEW_TYPE_DATE_HEADER
            is HistoryItem.EntryItem -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEARCH_CARD -> {
                val binding = ViewHistorySearchCardBinding.inflate(inflater, parent, false)
                SearchCardViewHolder(binding)
            }
            VIEW_TYPE_DATE_HEADER -> {
                val binding = ViewHistoryDateHeaderBinding.inflate(inflater, parent, false)
                DateHeaderViewHolder(binding)
            }
            VIEW_TYPE_ENTRY -> {
                val binding = ItemHistoryEntryBinding.inflate(inflater, parent, false)
                EntryViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryItem.SearchCard -> {
                // No binding needed for search card
            }
            is HistoryItem.DateHeader -> {
                (holder as DateHeaderViewHolder).bind(item.dateString)
            }
            is HistoryItem.EntryItem -> {
                (holder as EntryViewHolder).bind(item.historyEntry)
            }
        }
    }

    inner class SearchCardViewHolder(
        private val binding: ViewHistorySearchCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.historyFilterButton.setOnClickListener {
                onSearchClick()
            }
            binding.historyDeleteButton.setOnClickListener {
                onClearHistoryClick()
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
        private val binding: ItemHistoryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(historyEntry: HistoryEntry) {
            binding.apply {
                // Set page title - clean HTML from display text
                val cleanTitle = StringUtil.fromHtml(historyEntry.pageTitle.displayText).toString().trim()
                pageTitleText.text = cleanTitle

                // Set relative time (e.g., "2 hours ago")
                val relativeTime = DateUtils.getRelativeTimeSpanString(
                    historyEntry.timestamp.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                timestampText.text = relativeTime

                // Set click listener
                root.setOnClickListener {
                    onItemClick(historyEntry)
                }

                // Set delete button listener
                deleteButton.setOnClickListener {
                    onItemDelete(historyEntry)
                }
            }
        }
    }

    class HistoryItemDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return when {
                oldItem is HistoryItem.SearchCard && newItem is HistoryItem.SearchCard -> true
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