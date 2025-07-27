package com.omiyawaki.osrswiki.readinglist.adapter

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.ItemSavedPageBinding
import com.omiyawaki.osrswiki.readinglist.database.ReadingListPage
import com.omiyawaki.osrswiki.util.StringUtil
import java.text.DateFormat
import java.util.Date

class SavedPageViewHolder(
    private val binding: ItemSavedPageBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        savedPage: ReadingListPage,
        onItemClicked: (ReadingListPage) -> Unit
    ) {
        val context = binding.root.context
        
        // Set title - clean HTML entities for display and remove "Update:" prefix
        val cleanTitle = StringUtil.fromHtml(savedPage.displayTitle).toString()
        val titleText = when {
            cleanTitle.startsWith("Update: ") -> cleanTitle.removePrefix("Update: ")
            cleanTitle.startsWith("Update:") -> cleanTitle.removePrefix("Update:")
            else -> cleanTitle
        }
        binding.itemSavedPageTitle.text = titleText
        
        // Set description snippet (prioritize description over access time)
        if (!savedPage.description.isNullOrBlank()) {
            val cleanDescription = StringUtil.fromHtml(savedPage.description).toString()
                .trim()
                .replace('\u00A0', ' ') // Replace non-breaking spaces with regular spaces
                .replace("\\s+".toRegex(), " ") // Replace multiple whitespace with single space
                .trim() // Final trim after cleanup
            if (cleanDescription.isNotBlank()) {
                binding.itemSavedPageSnippet.text = cleanDescription
                binding.itemSavedPageSnippet.visibility = View.VISIBLE
            } else {
                binding.itemSavedPageSnippet.visibility = View.GONE
            }
        } else {
            binding.itemSavedPageSnippet.visibility = View.GONE
        }

        // Set page info (size and server update time)
        updatePageInfo(savedPage, context)

        // Load thumbnail if available
        loadThumbnail(savedPage)

        binding.root.setOnClickListener {
            onItemClicked(savedPage)
        }
    }
    
    private fun updatePageInfo(savedPage: ReadingListPage, context: Context) {
        val infoText = buildString {
            // Add status indicator
            val statusText = when (savedPage.status) {
                ReadingListPage.STATUS_QUEUE_FOR_SAVE, ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE -> "DOWNLOADING"
                ReadingListPage.STATUS_SAVED -> "SAVED"
                ReadingListPage.STATUS_ERROR -> "ERROR"
                else -> "UNKNOWN"
            }
            append(statusText)
            
            // Add file size if available
            if (savedPage.sizeBytes > 0) {
                append(" • ")
                append(Formatter.formatFileSize(context, savedPage.sizeBytes))
            }
            
            // Add last server update time (mtime represents when content was last modified on server)
            if (savedPage.mtime > 0) {
                append(" • ")
                val updateDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(savedPage.mtime))
                append("Last updated: $updateDate")
            }
        }
        
        if (infoText.isNotEmpty()) {
            binding.itemSavedPageInfo.text = infoText
            binding.itemSavedPageInfo.visibility = View.VISIBLE
        } else {
            binding.itemSavedPageInfo.visibility = View.GONE
        }
    }
    
    private fun loadThumbnail(savedPage: ReadingListPage) {
        if (!savedPage.thumbUrl.isNullOrBlank()) {
            binding.itemSavedPageThumbnail.visibility = View.VISIBLE
            Glide.with(binding.root.context)
                .load(savedPage.thumbUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.itemSavedPageThumbnail)
        } else {
            // Clear the view but keep space reserved for consistent layout
            Glide.with(binding.root.context).clear(binding.itemSavedPageThumbnail)
            binding.itemSavedPageThumbnail.visibility = View.INVISIBLE
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