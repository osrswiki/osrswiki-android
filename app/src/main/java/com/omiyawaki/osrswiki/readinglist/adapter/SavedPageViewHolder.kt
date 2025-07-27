package com.omiyawaki.osrswiki.readinglist.adapter

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        
        // Set title - clean HTML entities for display
        binding.itemSavedPageTitle.text = StringUtil.fromHtml(savedPage.displayTitle).toString()

        // Set status indicator
        updateStatusIndicator(savedPage, context)
        
        // Set description snippet (prioritize description over access time)
        if (!savedPage.description.isNullOrBlank()) {
            val cleanDescription = StringUtil.fromHtml(savedPage.description).toString().trim()
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
    
    private fun updateStatusIndicator(savedPage: ReadingListPage, context: Context) {
        val (statusText, statusColor) = when (savedPage.status) {
            ReadingListPage.STATUS_QUEUE_FOR_SAVE, ReadingListPage.STATUS_QUEUE_FOR_FORCED_SAVE -> {
                "DOWNLOADING" to ContextCompat.getColor(context, android.R.color.holo_orange_dark)
            }
            ReadingListPage.STATUS_SAVED -> {
                "SAVED" to ContextCompat.getColor(context, android.R.color.holo_green_dark)
            }
            ReadingListPage.STATUS_ERROR -> {
                "ERROR" to ContextCompat.getColor(context, android.R.color.holo_red_dark)
            }
            else -> {
                "UNKNOWN" to ContextCompat.getColor(context, android.R.color.darker_gray)
            }
        }
        
        binding.itemSavedPageStatus.text = statusText
        binding.itemSavedPageStatus.setTextColor(statusColor)
    }
    
    private fun updatePageInfo(savedPage: ReadingListPage, context: Context) {
        val infoText = buildString {
            // Add file size if available
            if (savedPage.sizeBytes > 0) {
                append(Formatter.formatFileSize(context, savedPage.sizeBytes))
            }
            
            // Add last server update time (mtime represents when content was last modified on server)
            if (savedPage.mtime > 0) {
                if (isNotEmpty()) append(" • ")
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
            // Clear the view and hide it to handle view recycling correctly
            Glide.with(binding.root.context).clear(binding.itemSavedPageThumbnail)
            binding.itemSavedPageThumbnail.visibility = View.GONE
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