package com.omiyawaki.osrswiki.image

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.OSRSWikiApp
import com.omiyawaki.osrswiki.R

/**
 * Production ImageLoader implementation using Glide.
 * Handles image loading with caching, placeholders, and error states.
 */
class GlideImageLoader(private val context: Context) : ImageLoader {
    override fun load(imageView: ImageView, url: String?) {
        // Check app-level cache first (preserving existing behavior)
        val app = context.applicationContext as? OSRSWikiApp
        val cachedBitmap = if (url != null) app?.imageCache?.get(url) else null

        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
        } else {
            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .dontAnimate()
                .into(imageView)
        }
    }
}