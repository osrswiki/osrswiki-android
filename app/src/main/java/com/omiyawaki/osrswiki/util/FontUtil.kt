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
        INTER,
        ALEGREYA,
        ALEGREYA_SC,
        RUBIK,
        CASCADIA_CODE
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
     * Apply Inter Body font (regular) for content text - better readability at small sizes
     */
    fun applyInterBody(textView: TextView) {
        applyFont(textView, FontFamily.INTER, FontWeight.NORMAL)
    }
    
    /**
     * Apply Inter Label font (regular) for UI elements - better readability at small sizes
     */
    fun applyInterLabel(textView: TextView) {
        applyFont(textView, FontFamily.INTER, FontWeight.NORMAL)
    }
    
    /**
     * Apply Alegreya Small Caps font for section headers
     */
    fun applyAlegreyaSmallCaps(textView: TextView, fontWeight: FontWeight = FontWeight.BOLD) {
        applyFont(textView, FontFamily.ALEGREYA_SC, fontWeight)
    }
    
    /**
     * Apply Rubik UI Label font (normal) for navigation and UI controls
     */
    fun applyRubikUILabel(textView: TextView) {
        applyFont(textView, FontFamily.RUBIK, FontWeight.NORMAL)
    }
    
    /**
     * Apply Rubik UI Label font (medium weight) for emphasized labels
     */
    fun applyRubikUILabelMedium(textView: TextView) {
        applyFont(textView, FontFamily.RUBIK, FontWeight.MEDIUM)
    }
    
    /**
     * Apply Rubik UI Label font (normal weight) with small size for compact labels
     */
    fun applyRubikUILabelSmall(textView: TextView) {
        applyFont(textView, FontFamily.RUBIK, FontWeight.NORMAL)
        textView.textSize = 16f // 16sp
    }
    
    /**
     * Apply Rubik UI Button font (normal) for button text
     */
    fun applyRubikUIButton(textView: TextView) {
        applyFont(textView, FontFamily.RUBIK, FontWeight.NORMAL)
    }
    
    /**
     * Apply Rubik UI Hint font (normal) for form hints and placeholder text
     */
    fun applyRubikUIHint(textView: TextView) {
        applyFont(textView, FontFamily.RUBIK, FontWeight.NORMAL)
    }
    
    /**
     * Apply Rubik UI Caption font (normal) for helper text and captions
     */
    fun applyRubikUICaption(textView: TextView) {
        applyFont(textView, FontFamily.RUBIK, FontWeight.NORMAL)
    }
    
    /**
     * Apply Cascadia Code font for code/monospace text
     */
    fun applyCascadiaCode(textView: TextView) {
        applyFont(textView, FontFamily.CASCADIA_CODE, FontWeight.NORMAL)
    }
    
    
    private fun getBaseTypeface(context: Context, fontFamily: FontFamily): Typeface? {
        val fontResource = when (fontFamily) {
            FontFamily.INTER -> R.font.inter
            FontFamily.ALEGREYA -> R.font.alegreya
            FontFamily.ALEGREYA_SC -> R.font.alegreya_sc
            FontFamily.RUBIK -> R.font.rubik
            FontFamily.CASCADIA_CODE -> R.font.cascadia_code
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
fun TextView.applyInterBody() = FontUtil.applyInterBody(this)
fun TextView.applyInterLabel() = FontUtil.applyInterLabel(this)
fun TextView.applyAlegreyaSmallCaps(fontWeight: FontUtil.FontWeight = FontUtil.FontWeight.BOLD) = 
    FontUtil.applyAlegreyaSmallCaps(this, fontWeight)
fun TextView.applyRubikUILabel() = FontUtil.applyRubikUILabel(this)
fun TextView.applyRubikUILabelMedium() = FontUtil.applyRubikUILabelMedium(this)
fun TextView.applyRubikUILabelSmall() = FontUtil.applyRubikUILabelSmall(this)
fun TextView.applyRubikUIButton() = FontUtil.applyRubikUIButton(this)
fun TextView.applyRubikUIHint() = FontUtil.applyRubikUIHint(this)
fun TextView.applyRubikUICaption() = FontUtil.applyRubikUICaption(this)
fun TextView.applyCascadiaCode() = FontUtil.applyCascadiaCode(this)
