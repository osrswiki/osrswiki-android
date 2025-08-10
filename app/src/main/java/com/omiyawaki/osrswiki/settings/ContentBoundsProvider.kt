package com.omiyawaki.osrswiki.settings

import android.app.Activity
import android.util.Log
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw

/**
 * Expert's solution: Captures live Activity content bounds and insets for faithful off-screen rendering.
 * Ensures theme previews match exactly what users see in the live UI.
 */
object ContentBoundsProvider {
    
    private const val TAG = "ContentBoundsProvider"
    
    @Volatile private var contentAspect: Float? = null
    @Volatile private var systemBarInsets: Insets? = null
    @Volatile private var contentWidth: Int? = null
    @Volatile private var contentHeight: Int? = null
    @Volatile private var contentPadding: Padding? = null
    
    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class ContentBounds(
        val width: Int,
        val height: Int, 
        val aspect: Float,
        val systemInsets: Insets,
        val padding: Padding
    )
    
    /**
     * Captures content bounds from a live Activity's content root.
     * Call this from Activity.onCreate() or onResume() after layout is complete.
     */
    fun publishFrom(activity: Activity) {
        val contentRoot = activity.findViewById<View>(android.R.id.content)
        
        // Wait for layout to complete before capturing dimensions
        contentRoot.doOnPreDraw {
            val w = contentRoot.width
            val h = contentRoot.height
            
            if (w > 0 && h > 0) {
                contentWidth = w
                contentHeight = h
                contentAspect = h.toFloat() / w
                
                // Capture live system bar insets that affect content positioning
                val rootInsets = ViewCompat.getRootWindowInsets(contentRoot)
                systemBarInsets = rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: Insets.NONE
                
                // Capture actual padding applied to content root
                contentPadding = Padding(
                    contentRoot.paddingLeft,
                    contentRoot.paddingTop, 
                    contentRoot.paddingRight,
                    contentRoot.paddingBottom
                )
                
                Log.d(TAG, "Captured live content bounds: ${w}×${h}, aspect=${contentAspect}")
                Log.d(TAG, "System bar insets: top=${systemBarInsets?.top}, bottom=${systemBarInsets?.bottom}")
                Log.d(TAG, "Content padding: top=${contentPadding?.top}, bottom=${contentPadding?.bottom}")
            }
        }
    }
    
    /**
     * Gets the captured content aspect ratio, or fallback if not yet captured.
     */
    fun contentAspectOr(fallback: Float): Float = contentAspect ?: fallback
    
    /**
     * Gets the captured system bar insets, or NONE if not yet captured.
     */
    fun systemBarInsetsOrNone(): Insets = systemBarInsets ?: Insets.NONE
    
    /**
     * Gets the captured content bounds, or null if not yet captured.
     */
    fun getContentBounds(): ContentBounds? {
        val w = contentWidth ?: return null
        val h = contentHeight ?: return null
        val aspect = contentAspect ?: return null
        val insets = systemBarInsets ?: Insets.NONE
        val padding = contentPadding ?: Padding(0, 0, 0, 0)
        
        return ContentBounds(w, h, aspect, insets, padding)
    }
    
    /**
     * Checks if content bounds have been captured.
     */
    fun isAvailable(): Boolean = contentWidth != null && contentHeight != null
}