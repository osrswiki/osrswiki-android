package com.omiyawaki.osrswiki.readinglist.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSavedPageBinding
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.util.StringUtil // Assuming this is your HTML utility
import java.text.DateFormat
import java.util.Date

class SavedPageViewHolder(
    private val binding: ItemSavedPageBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        savedPage: ReadingListPage,
        onItemClicked: (ReadingListPage) -> Unit
    ) {
        // Use StringUtil.fromHtml to decode HTML entities for display
        binding.itemSavedPageTitle.text = StringUtil.fromHtml(savedPage.displayTitle).toString()

        if (savedPage.atime > 0) {
            val context = binding.root.context
            val formattedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(savedPage.atime))
            binding.itemSavedPageSubtitle.text = context.getString(R.string.saved_page_last_accessed_format, formattedDate)
        } else {
            // Also process description if it might contain HTML
            binding.itemSavedPageSubtitle.text = savedPage.description?.let {
                StringUtil.fromHtml(it).toString().takeIf { desc -> desc.isNotBlank() }
            } ?: "" // Fallback to empty if no description or it's blank after processing
        }

        binding.root.setOnClickListener {
            onItemClicked(savedPage)
        }
    }

    companion object {
        fun create(parent: ViewGroup): SavedPageViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemSavedPageBinding.inflate(inflater, parent, false)
            return SavedPageViewHolder(binding)
        }
    }
}