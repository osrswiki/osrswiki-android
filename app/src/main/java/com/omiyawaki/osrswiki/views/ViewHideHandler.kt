package com.omiyawaki.osrswiki.views

import android.view.View

/**
 * A simple utility to translate a view vertically based on scroll events.
 * Its only responsibility is to modify the translationY property of its target view.
 */
class ViewHideHandler(
    private val targetView: View,
    private val gravity: Int = 0 // -1 for up, 1 for down
) {
    private var range = 0
    private var curTranslation = 0

    init {
        targetView.post {
            range = if (gravity < 0) -targetView.height else if (gravity > 0) targetView.height else 0
        }
    }

    fun onScrolled(scrollY: Int, oldScrollY: Int) {
        if (range == 0) return
        val diff = scrollY - oldScrollY
        if (diff == 0) return
        
        val newTranslation = curTranslation - diff
        translate(newTranslation)
    }

    private fun translate(newTranslation: Int) {
        var translation = newTranslation
        translation = if (gravity < 0) {
            translation.coerceIn(range, 0)
        } else {
            translation.coerceIn(0, range)
        }

        if (translation == curTranslation) return
        
        curTranslation = translation
        targetView.translationY = curTranslation.toFloat()
    }
}
