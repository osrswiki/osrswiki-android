package com.omiyawaki.osrswiki.util

import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.omiyawaki.osrswiki.R

/**
 * Helper class to manage voice search button animations and states
 */
class VoiceSearchAnimationHelper(private val voiceButton: ImageView) {
    
    private var currentAnimation: Any? = null
    private val originalTintList = voiceButton.imageTintList // Store the original tint
    
    fun setIdleState() {
        stopCurrentAnimation()
        voiceButton.setImageResource(R.drawable.ic_voice_search_24)
        // Restore the original tint that was set in the layout
        voiceButton.imageTintList = originalTintList
    }
    
    fun setListeningState() {
        stopCurrentAnimation()
        
        // Clear any tint to show the true red color from animations
        voiceButton.imageTintList = null
        
        // Try to use the animated vector drawable first (API 21+)
        try {
            voiceButton.setImageResource(R.drawable.voice_search_pulse_animation)
            val drawable = voiceButton.drawable
            if (drawable is AnimatedVectorDrawable) {
                currentAnimation = drawable
                drawable.start()
                return
            }
        } catch (e: Exception) {
            // Fall back to animation list
        }
        
        // Fallback to simple animation list
        voiceButton.setImageResource(R.drawable.voice_search_animation)
        val drawable = voiceButton.drawable
        if (drawable is AnimationDrawable) {
            currentAnimation = drawable
            drawable.start()
        }
    }
    
    fun setProcessingState() {
        stopCurrentAnimation()
        // Clear any tint to show the red color
        voiceButton.imageTintList = null
        voiceButton.setImageResource(R.drawable.ic_voice_search_recording)
    }
    
    fun setErrorState() {
        stopCurrentAnimation()
        voiceButton.setImageResource(R.drawable.ic_voice_search_24)
        // Could add a red tint or shake animation here
    }
    
    private fun stopCurrentAnimation() {
        val animation = currentAnimation
        when (animation) {
            is AnimatedVectorDrawable -> {
                animation.stop()
            }
            is AnimationDrawable -> {
                animation.stop()
            }
        }
        currentAnimation = null
    }
    
    fun cleanup() {
        stopCurrentAnimation()
    }
}

/**
 * Extension function to easily create and attach a VoiceSearchAnimationHelper to an ImageView
 */
fun ImageView.createVoiceSearchAnimationHelper(): VoiceSearchAnimationHelper {
    return VoiceSearchAnimationHelper(this)
}