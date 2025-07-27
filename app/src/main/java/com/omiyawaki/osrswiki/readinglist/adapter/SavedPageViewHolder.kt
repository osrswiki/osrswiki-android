package com.omiyawaki.osrswiki.readinglist.adapter

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        val context = binding.root.context
        
        // Use StringUtil.fromHtml to decode HTML entities for display
        binding.itemSavedPageTitle.text = StringUtil.fromHtml(savedPage.displayTitle).toString()

        // Set status indicator
        updateStatusIndicator(savedPage, context)
        
        // Set subtitle (description or last accessed)
        if (savedPage.atime > 0) {
            val formattedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(savedPage.atime))
            binding.itemSavedPageSubtitle.text = context.getString(R.string.saved_page_last_accessed_format, formattedDate)
        } else {
            // Also process description if it might contain HTML
            binding.itemSavedPageSubtitle.text = savedPage.description?.let {
                StringUtil.fromHtml(it).toString().takeIf { desc -> desc.isNotBlank() }
            } ?: "" // Fallback to empty if no description or it's blank after processing
        }

        // Set page info (size and download time)
        updatePageInfo(savedPage, context)

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
            
            // Add download/modified time
            if (savedPage.mtime > 0) {
                if (isNotEmpty()) append(" • ")
                val timeAgo = getTimeAgo(savedPage.mtime, context)
                append("Downloaded $timeAgo")
            }
        }
        
        if (infoText.isNotEmpty()) {
            binding.itemSavedPageInfo.text = infoText
            binding.itemSavedPageInfo.visibility = View.VISIBLE
        } else {
            binding.itemSavedPageInfo.visibility = View.GONE
        }
    }
    
    private fun getTimeAgo(timestamp: Long, context: Context): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000} minutes ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))
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