package com.omiyawaki.osrswiki.util

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.log.L

/**
 * Utility class to handle custom font application on Huawei devices
 * where theme-based font styles are blocked by the system.
 */
object FontUtil {
    private const val TAG = "FontUtil"
    
    // Font family enums
    enum class FontFamily {
        ALEGREYA,
        ALEGREYA_SC
    }
    
    // Font weight enums
    enum class FontWeight {
        NORMAL,
        MEDIUM,
        BOLD
    }
    
    // Font style enums  
    enum class FontStyle {
        NORMAL,
        ITALIC
    }
    
    /**
     * Apply custom font to TextView, bypassing theme system for Huawei compatibility
     */
    fun applyFont(
        textView: TextView,
        fontFamily: FontFamily,
        fontWeight: FontWeight = FontWeight.NORMAL,
        fontStyle: FontStyle = FontStyle.NORMAL
    ) {
        try {
            val context = textView.context
            val baseTypeface = getBaseTypeface(context, fontFamily)
            
            if (baseTypeface != null) {
                val finalTypeface = createStyledTypeface(baseTypeface, fontWeight, fontStyle)
                textView.typeface = finalTypeface
                L.d("$TAG: Applied ${fontFamily.name} font to TextView")
            } else {
                L.e("$TAG: Failed to load base typeface for ${fontFamily.name}")
            }
        } catch (e: Exception) {
            L.e("$TAG: Error applying font ${fontFamily.name}: ${e.message}", e)
        }
    }
    
    /**
     * Apply Alegreya Display font (bold) for headlines and titles
     */
    fun applyAlegreyaDisplay(textView: TextView) {
        applyFont(textView, FontFamily.ALEGREYA, FontWeight.BOLD)
    }
    
    /**
     * Apply Alegreya Headline font (bold) for section headers
     */
    fun applyAlegreyaHeadline(textView: TextView) {
        applyFont(textView, FontFamily.ALEGREYA, FontWeight.BOLD)
    }
    
    /**
     * Apply Alegreya Title font (bold) for card titles
     */
    fun applyAlegreyaTitle(textView: TextView) {
        applyFont(textView, FontFamily.ALEGREYA, FontWeight.BOLD)
    }
    
    
    /**
     * Apply Alegreya Small Caps font for section headers
     */
    fun applyAlegreyaSmallCaps(textView: TextView, fontWeight: FontWeight = FontWeight.BOLD) {
        applyFont(textView, FontFamily.ALEGREYA_SC, fontWeight)
    }
    
    
    
    
    private fun getBaseTypeface(context: Context, fontFamily: FontFamily): Typeface? {
        val fontResource = when (fontFamily) {
            FontFamily.ALEGREYA -> R.font.alegreya
            FontFamily.ALEGREYA_SC -> R.font.alegreya_sc
        }
        
        return try {
            ResourcesCompat.getFont(context, fontResource)
        } catch (e: Exception) {
            L.e("$TAG: Error loading font resource $fontResource: ${e.message}", e)
            null
        }
    }
    
    private fun createStyledTypeface(
        baseTypeface: Typeface,
        fontWeight: FontWeight,
        fontStyle: FontStyle
    ): Typeface {
        // For API 28+ use the more precise weight control
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val weight = when (fontWeight) {
                FontWeight.NORMAL -> 400
                FontWeight.MEDIUM -> 500
                FontWeight.BOLD -> 700
            }
            val italic = fontStyle == FontStyle.ITALIC
            return Typeface.create(baseTypeface, weight, italic)
        }
        
        // Fallback for older APIs - rely on font family definitions
        val androidStyle = when {
            fontWeight == FontWeight.BOLD && fontStyle == FontStyle.ITALIC -> Typeface.BOLD_ITALIC
            fontWeight == FontWeight.BOLD -> Typeface.BOLD
            fontWeight == FontWeight.MEDIUM && fontStyle == FontStyle.ITALIC -> Typeface.ITALIC
            fontWeight == FontWeight.MEDIUM -> Typeface.NORMAL // Font family should handle medium weight
            fontStyle == FontStyle.ITALIC -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        
        return Typeface.create(baseTypeface, androidStyle)
    }
}

/**
 * Extension functions for easier font application
 */
fun TextView.applyAlegreyaDisplay() = FontUtil.applyAlegreyaDisplay(this)
fun TextView.applyAlegreyaHeadline() = FontUtil.applyAlegreyaHeadline(this)
fun TextView.applyAlegreyaTitle() = FontUtil.applyAlegreyaTitle(this)
fun TextView.applyAlegreyaSmallCaps(fontWeight: FontUtil.FontWeight = FontUtil.FontWeight.BOLD) = 
    FontUtil.applyAlegreyaSmallCaps(this, fontWeight)
