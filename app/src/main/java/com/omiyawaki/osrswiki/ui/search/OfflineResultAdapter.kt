package com.omiyawaki.osrswiki.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding
// CleanedSearchResultItem is assumed to be in the same package or imported correctly.
// data class CleanedSearchResultItem(val id: String, val title: String, val snippet: String)

class OfflineResultAdapter(
    private val onItemClicked: (CleanedSearchResultItem) -> Unit
) : ListAdapter<CleanedSearchResultItem, OfflineResultAdapter.OfflineResultViewHolder>(OfflineResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfflineResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OfflineResultViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: OfflineResultViewHolder, position: Int) {
        val item = getItem(position)
        // getItem() from ListAdapter will not return null for valid positions
        holder.bind(item)
    }

    class OfflineResultViewHolder(
        private val binding: ItemSearchResultBinding,
        private val onItemClicked: (CleanedSearchResultItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: CleanedSearchResultItem? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let {
                    onItemClicked(it)
                }
            }
        }

        fun bind(item: CleanedSearchResultItem) {
            currentItem = item
            binding.itemSearchTitleTextview.text = item.title
            if (item.snippet.isEmpty()) {
                binding.itemSearchSnippetTextview.visibility = View.GONE
            } else {
                // For offline search results, the snippet might contain HTML tags (if sourced from web) or be plain text.
                // TextView by default does not render HTML. If HTML rendering is desired,
                // use Html.fromHtml(item.snippet, Html.FROM_HTML_MODE_COMPACT).
                // For now, displaying as plain text. If highlighting is not rendering, this is where to adjust.
                binding.itemSearchSnippetTextview.text = item.snippet
                binding.itemSearchSnippetTextview.visibility = View.VISIBLE
            }
        }
    }
}

class OfflineResultDiffCallback : DiffUtil.ItemCallback<CleanedSearchResultItem>() {
    override fun areItemsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CleanedSearchResultItem, newItem: CleanedSearchResultItem): Boolean {
        return oldItem == newItem
    }
}
