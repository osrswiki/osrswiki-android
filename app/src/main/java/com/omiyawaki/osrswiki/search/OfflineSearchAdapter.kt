package com.omiyawaki.osrswiki.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter // Make sure this is androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil   // Make sure this is androidx.recyclerview.widget.DiffUtil
import com.omiyawaki.osrswiki.databinding.ItemSearchResultBinding

/**
 * Adapter for displaying offline search results (both title matches and FTS results).
 * It reuses SearchAdapter.SearchResultViewHolder and SearchAdapter.OnItemClickListener.
 */
class OfflineSearchAdapter(
    private val onItemClickListener: SearchAdapter.OnItemClickListener // Reusing the interface from SearchAdapter
) : ListAdapter<CleanedSearchResultItem, SearchAdapter.SearchResultViewHolder>(
    SearchAdapter.SEARCH_RESULT_COMPARATOR // Reusing the DiffUtil.ItemCallback from SearchAdapter's companion object
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchAdapter.SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        // Directly instantiate SearchAdapter.SearchResultViewHolder
        // It's a nested class, accessible via SearchAdapter.SearchResultViewHolder
        return SearchAdapter.SearchResultViewHolder(binding, onItemClickListener)
    }

    override fun onBindViewHolder(holder: SearchAdapter.SearchResultViewHolder, position: Int) {
        val item = getItem(position)
        // ListAdapter's getItem can return null if placeholders are enabled (not typical without Paging)
        // or during certain list update scenarios, so a null check is safe.
        if (item != null) {
            holder.bind(item)
        }
    }
}