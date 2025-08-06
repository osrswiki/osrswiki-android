package com.omiyawaki.osrswiki.ui.main

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.omiyawaki.osrswiki.databinding.ViewMainFeedSearchBinding

class SearchCardViewHolder(
    private val binding: ViewMainFeedSearchBinding,
    private val callback: MainFeedAdapter.Callback
) : RecyclerView.ViewHolder(binding.root) {

    init {
        // This is the key to fixing the visual glitch.
        // It sets the card's background to be the same as the page background,
        // matching the behavior of the Wikipedia app.
        (itemView as MaterialCardView).setCardBackgroundColor(
            com.google.android.material.R.attr.colorSurface
        )

        itemView.setOnClickListener {
            callback.onSearchRequested(it)
        }

        binding.voiceSearchButton.setOnClickListener {
            callback.onVoiceSearchRequested()
        }
    }

    fun bind() {
        // Nothing to bind for now, as the content is static.
        // This method can be used later if the text needs to be dynamic.
    }
}
