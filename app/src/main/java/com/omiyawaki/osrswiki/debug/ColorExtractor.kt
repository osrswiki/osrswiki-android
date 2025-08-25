package com.omiyawaki.osrswiki.debug

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.R
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

/**
 * Color Extraction Utility for Android Tab Bar Testing
 * 
 * This utility extracts the actual rendered colors from Android bottom navigation
 * to enable programmatic color comparison testing.
 */
object ColorExtractor {
    
    fun extractBottomNavColors(context: Context): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        try {
            // Extract colors from resources
            val activeColor = ContextCompat.getColor(context, R.color.osrs_text_dark)
            val inactiveColor = ContextCompat.getColor(context, R.color.bottom_nav_inactive)
            val surfaceColor = ContextCompat.getColor(context, R.color.osrs_parchment_light)
            val placeholderColor = ContextCompat.getColor(context, R.color.osrs_brown_deep)
            
            results["active_color"] = colorToHex(activeColor)
            // Extract the visual RGB result (ignore alpha channel for comparison)
            results["inactive_color_argb"] = String.format("#%08X", inactiveColor)
            results["inactive_color_rgb"] = colorToHex(inactiveColor)
            results["surface_color"] = colorToHex(surfaceColor)
            results["placeholder_color"] = colorToHex(placeholderColor)
            
            // Calculate 40% alpha of active color
            val calculated40Alpha = applyAlpha(activeColor, 0.4f)
            results["calculated_40_alpha"] = colorToHex(calculated40Alpha)
            
            // Calculate visual result of 40% alpha over surface
            val visualResult = calculateAlphaBlending(activeColor, surfaceColor, 0.4f)
            results["visual_40_alpha_result"] = colorToHex(visualResult)
            
            // Extract ARGB components of inactive color to verify alpha
            val alpha = Color.alpha(inactiveColor)
            val red = Color.red(inactiveColor)
            val green = Color.green(inactiveColor)
            val blue = Color.blue(inactiveColor)
            
            results["inactive_color_argb"] = "alpha=$alpha, red=$red, green=$green, blue=$blue"
            results["inactive_color_alpha_percentage"] = "${(alpha / 255.0f * 100).toInt()}%"
            
        } catch (e: Exception) {
            results["error"] = e.message ?: "Unknown error"
        }
        
        return results
    }
    
    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
    
    private fun applyAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (alpha * 255).toInt()
        return Color.argb(
            alphaInt,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
    
    private fun calculateAlphaBlending(foreground: Int, background: Int, alpha: Float): Int {
        val fgR = Color.red(foreground) / 255.0f
        val fgG = Color.green(foreground) / 255.0f
        val fgB = Color.blue(foreground) / 255.0f
        
        val bgR = Color.red(background) / 255.0f
        val bgG = Color.green(background) / 255.0f
        val bgB = Color.blue(background) / 255.0f
        
        // Alpha blending formula: result = alpha * foreground + (1 - alpha) * background
        val resultR = alpha * fgR + (1 - alpha) * bgR
        val resultG = alpha * fgG + (1 - alpha) * bgG
        val resultB = alpha * fgB + (1 - alpha) * bgB
        
        return Color.rgb(
            (resultR * 255).toInt(),
            (resultG * 255).toInt(),
            (resultB * 255).toInt()
        )
    }
    
    fun exportColorsToJSON(context: Context) {
        val colors = extractBottomNavColors(context)
        
        try {
            val jsonObject = JSONObject()
            for ((key, value) in colors) {
                jsonObject.put(key, value)
            }
            
            val jsonString = jsonObject.toString(2)
            println("🎨 ANDROID COLOR EXTRACTION RESULTS:")
            println(jsonString)
            android.util.Log.d("ColorExtractor", "🎨 ANDROID COLOR EXTRACTION RESULTS:")
            android.util.Log.d("ColorExtractor", jsonString)
            
            // Write to external files directory for easy access
            val file = File(context.getExternalFilesDir(null), "android_colors.json")
            FileWriter(file).use { writer ->
                writer.write(jsonString)
            }
            println("🤖 Android colors exported to: ${file.absolutePath}")
            
        } catch (e: Exception) {
            println("❌ Error exporting Android colors: ${e.message}")
        }
    }
}

// Extension to add color extraction to MainActivity
fun com.omiyawaki.osrswiki.MainActivity.debugColorExtraction() {
    ColorExtractor.exportColorsToJSON(this)
}