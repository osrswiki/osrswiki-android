package com.omiyawaki.osrswiki.readinglist.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage

/**
 * Adapter for the RecyclerView in the Saved Pages screen.
 * Uses ListAdapter for efficient list updates with DiffUtil.
 */
class SavedPagesAdapter(
    private val onItemClicked: (ReadingListPage) -> Unit
) : ListAdapter<ReadingListPage, SavedPageViewHolder>(SavedPageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedPageViewHolder {
        return SavedPageViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: SavedPageViewHolder, position: Int) {
        val savedPage = getItem(position)
        holder.bind(savedPage, onItemClicked)
    }
}

/**
 * DiffUtil.ItemCallback for ReadingListPage.
 * Helps ListAdapter determine how to efficiently update the list.
 */
class SavedPageDiffCallback : DiffUtil.ItemCallback<ReadingListPage>() {
    override fun areItemsTheSame(oldItem: ReadingListPage, newItem: ReadingListPage): Boolean {
        // 'id' is the unique primary key for ReadingListPage.
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ReadingListPage, newItem: ReadingListPage): Boolean {
        // ReadingListPage is a data class, so '==' checks for structural equality.
        // This is usually sufficient for determining if the displayed content has changed.
        return oldItem == newItem
    }
}
