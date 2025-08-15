package com.omiyawaki.osrswiki.image

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.omiyawaki.osrswiki.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Expert-optimized synchronous ImageLoader for theme previews.
 * Uses blocking Glide approach with fast timeout and size override to avoid huge decodes.
 * Images are loaded synchronously before measure/layout/draw phase to ensure they're available.
 */
class SynchronousImageLoader(private val context: Context) : ImageLoader {
    
    companion object {
        private const val LOAD_TIMEOUT_MS = 1500L // Expert's fast 1.5s timeout
        private const val TARGET_SIZE_PX = 200     // Expert's override to avoid huge decodes
    }
    
    override fun load(imageView: ImageView, url: String?) {
        if (url.isNullOrEmpty()) {
            // No URL provided, use placeholder
            imageView.setImageResource(R.drawable.ic_placeholder_image)
            return
        }
        
        try {
            // Expert's blocking Glide approach with optimizations
            val drawable = runBlocking {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        Glide.with(context.applicationContext)
                            .asDrawable()
                            .load(url)
                            .dontAnimate()  // Expert: No shimmers, crossfades, animations
                            .override(TARGET_SIZE_PX, TARGET_SIZE_PX)  // Expert: Size override to avoid huge decodes
                            .submit()
                            .get()
                    }
                }
            }
            
            // Set the loaded drawable or fallback to placeholder
            if (drawable != null) {
                imageView.setImageDrawable(drawable)
            } else {
                // Timeout or load failure - use placeholder
                imageView.setImageResource(R.drawable.ic_placeholder_image)
            }
            
        } catch (e: Exception) {
            // Any exception during load - use placeholder
            imageView.setImageResource(R.drawable.ic_placeholder_image)
        }
    }
}