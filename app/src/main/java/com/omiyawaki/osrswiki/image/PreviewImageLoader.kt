package com.omiyawaki.osrswiki.image

import android.widget.ImageView
import com.omiyawaki.osrswiki.R

/**
 * ImageLoader implementation for theme previews.
 * Loads static placeholder drawables synchronously instead of using Glide.
 */
object PreviewImageLoader : ImageLoader {
    override fun load(imageView: ImageView, url: String?) {
        // Use a static placeholder that looks good in previews
        // This avoids any async Glide operations that would fail in preview context
        imageView.setImageResource(R.drawable.ic_placeholder_image)
    }
}