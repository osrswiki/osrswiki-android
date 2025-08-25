package com.omiyawaki.osrswiki.test

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.io.File
import java.io.FileOutputStream

object SearchScreenshotTest {
    
    fun captureSearchScreenshot(activity: Activity, delay: Long = 3000) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val rootView = activity.window.decorView.rootView
                rootView.isDrawingCacheEnabled = true
                val bitmap = Bitmap.createBitmap(rootView.drawingCache)
                rootView.isDrawingCacheEnabled = false
                
                // Save screenshot
                val file = File(activity.externalCacheDir, "search_screenshot_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                Log.e("ScreenshotTest", "Screenshot saved to: ${file.absolutePath}")
                
                // Analyze colors
                analyzeScreenshotColors(bitmap)
                
            } catch (e: Exception) {
                Log.e("ScreenshotTest", "Failed to capture screenshot", e)
            }
        }, delay)
    }
    
    private fun analyzeScreenshotColors(bitmap: Bitmap) {
        // Sample different Y positions to find search results
        val width = bitmap.width
        val height = bitmap.height
        
        Log.e("ScreenshotTest", "=== SCREENSHOT COLOR ANALYSIS ===")
        Log.e("ScreenshotTest", "Bitmap size: ${width}x${height}")
        
        // Sample colors at various positions
        val samplePositions = listOf(
            200 to "Top area",
            400 to "Upper middle",
            600 to "Middle",
            800 to "Lower middle",
            1000 to "Bottom area"
        )
        
        for ((y, label) in samplePositions) {
            if (y < height) {
                val leftColor = bitmap.getPixel(100, y)
                val centerColor = bitmap.getPixel(width / 2, y)
                val rightColor = bitmap.getPixel(width - 100, y)
                
                Log.e("ScreenshotTest", "$label (y=$y):")
                Log.e("ScreenshotTest", "  Left: #${Integer.toHexString(leftColor)}")
                Log.e("ScreenshotTest", "  Center: #${Integer.toHexString(centerColor)}")
                Log.e("ScreenshotTest", "  Right: #${Integer.toHexString(rightColor)}")
            }
        }
    }
}