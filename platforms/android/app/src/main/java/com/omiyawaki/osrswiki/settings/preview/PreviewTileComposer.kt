package com.omiyawaki.osrswiki.settings.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.DisplayMetrics
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Expert's exact implementation for composing theme previews into fixed-size tiles.
 * Handles scale-to-fit logic with proper pillar-boxing and no cropping.
 */
object PreviewTileComposer {
    
    data class Size(val w: Int, val h: Int)
    
    /**
     * Expert's transparent pillar-boxing implementation for theme previews.
     * Uses scale-to-fit to show complete content with transparent pillar-boxing areas
     * that let the parent card background show through naturally.
     * 
     * @param src Rendered screenful bitmap at 4× width (already themed)
     * @param containerPx Target container size (e.g., 96dp×192dp in px)
     * @param bgColor Unused (kept for compatibility) - transparency used instead
     * @param cornerRadiusPx Corner radius (0 if parent card clips; else round here)
     * @return Fixed-size bitmap with transparent pillar-boxing, ready for ImageView with FIT_XY
     */
    fun composeIntoFixedTile(
        src: Bitmap,
        containerPx: Size,
        bgColor: Int,
        cornerRadiusPx: Float = 0f
    ): Bitmap {
        // Expert's scale-to-fit logic (no crop) - use min scale so both dimensions fit
        val scaleW = containerPx.w / src.width.toFloat()
        val scaleH = containerPx.h / src.height.toFloat()
        val scale = min(scaleW, scaleH)
        
        // EXPERT OPTION 2B: Use ceil for constrained dimension to avoid sub-pixel gaps
        val dstW = if (scale == scaleW) {
            // Width-constrained: use exact container width, ceil for height
            containerPx.w
        } else {
            // Height-constrained: ceil the width calculation
            kotlin.math.ceil(src.width * scale).toInt()
        }
        
        val dstH = if (scale == scaleH) {
            // Height-constrained: use exact container height, ceil for width
            containerPx.h
        } else {
            // Width-constrained: ceil the height calculation
            kotlin.math.ceil(src.height * scale).toInt()
        }
        
        // EXPERT OPTION 2B: Clamp translate coordinates to integers (no sub-pixel positioning)
        val dx = kotlin.math.round((containerPx.w - dstW) / 2f)
        val dy = kotlin.math.round((containerPx.h - dstH) / 2f)
        
        android.util.Log.d("PreviewTileComposer", "composeIntoFixedTile (transparent): src=${src.width}×${src.height}, container=${containerPx.w}×${containerPx.h}")
        android.util.Log.d("PreviewTileComposer", "scaleW=$scaleW, scaleH=$scaleH, chosen scale=$scale (${if (scale == scaleW) "width-constrained" else "height-constrained"})")
        android.util.Log.d("PreviewTileComposer", "scaled size=${dstW}×${dstH} (using ceil for constrained dim), offset=($dx,$dy) (integer positioning)")
        android.util.Log.d("PreviewTileComposer", "transparentPillarBox: horizontal=${containerPx.w - dstW}, vertical=${containerPx.h - dstH} (parent card shows through)")

        val out = Bitmap.createBitmap(containerPx.w, containerPx.h, Bitmap.Config.ARGB_8888)
        // EXPERT TRANSPARENT PILLAR-BOXING: Clear to transparent, let parent card show through
        out.eraseColor(Color.TRANSPARENT)
        val c = Canvas(out)

        // Apply corner radius if needed (0 if parent card handles clipping)
        val save = if (cornerRadiusPx > 0f) {
            val p = Path().apply { 
                addRoundRect(
                    0f, 0f, 
                    containerPx.w.toFloat(), containerPx.h.toFloat(),
                    cornerRadiusPx, cornerRadiusPx, 
                    Path.Direction.CW
                ) 
            }
            val saveCount = c.save()
            c.clipPath(p)
            saveCount
        } else -1

        // EXPERT OPTION 2B: Enhanced paint settings for seamless rendering
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { 
            isDither = true  // Expert recommendation for smoother rendering
        }
        
        // Draw scaled and centered preview with integer positioning
        val m = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx.toFloat(), dy.toFloat()) // Integer positioning
        }
        c.drawBitmap(src, m, paint)

        if (save >= 0) c.restoreToCount(save)
        
        // Expert critical: Prevent ImageView auto-scaling
        out.setDensity(0) // DENSITY_NONE equivalent
        
        return out
    }
    
    /**
     * Expert's cover+epsilon scaling to eliminate pillar-boxing artifacts.
     * Uses max scale with tiny overscale (epsilon) to slightly overfill container.
     * No visible bars, negligible crop.
     * 
     * @param src Rendered bitmap to scale
     * @param containerW Target container width in px
     * @param containerH Target container height in px
     * @param bgColor Theme background color (unused with cover scaling)
     * @param epsilon Tiny overscale factor (1.006 = 0.6% overscale)
     * @return Fixed-size bitmap with no pillar-boxing artifacts
     */
    fun composeCoverNoBars(
        src: Bitmap,
        containerW: Int, 
        containerH: Int,
        bgColor: Int,
        epsilon: Float = 1.006f
    ): Bitmap {
        // Expert's cover scaling: use MAX scale (not min) with tiny epsilon overscale
        val scale = kotlin.math.max(
            containerW / src.width.toFloat(),
            containerH / src.height.toFloat()
        ) * epsilon

        val dstW = (src.width * scale).roundToInt()
        val dstH = (src.height * scale).roundToInt()
        val dx = ((containerW - dstW) / 2f)
        val dy = ((containerH - dstH) / 2f)

        android.util.Log.d("PreviewTileComposer", "composeCoverNoBars: src=${src.width}×${src.height}, container=${containerW}×${containerH}")
        android.util.Log.d("PreviewTileComposer", "cover scale=${scale} (epsilon=${epsilon}), scaled size=${dstW}×${dstH}, offset=($dx,$dy)")
        android.util.Log.d("PreviewTileComposer", "overfill: horizontal=${dstW - containerW}, vertical=${dstH - containerH}")

        val out = Bitmap.createBitmap(containerW, containerH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bgColor) // Fill background (though should be fully covered)
        
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { 
            isDither = true 
        }
        val m = Matrix().apply { 
            postScale(scale, scale)
            postTranslate(dx, dy) 
        }
        c.drawBitmap(src, m, p)
        
        // Expert critical: Prevent ImageView auto-scaling
        out.setDensity(0) // DENSITY_NONE equivalent
        
        return out
    }
}