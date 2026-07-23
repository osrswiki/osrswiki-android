package com.omiyawaki.osrswiki.image

import android.widget.ImageView

/**
 * Interface for loading images into ImageViews.
 * Allows dependency injection of different image loading strategies
 * (e.g., Glide for production, static drawables for previews).
 */
interface ImageLoader {
    /**
     * Load an image from the given URL into the ImageView.
     * @param imageView The target ImageView
     * @param url The image URL to load, can be null
     */
    fun load(imageView: ImageView, url: String?)
}