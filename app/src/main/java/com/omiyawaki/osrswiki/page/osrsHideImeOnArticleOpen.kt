package com.omiyawaki.osrswiki.page

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Dismisses the host-window IME when an article is opened over a still-focused
 * search field (Search results, Home View more, Saved-pages search).
 */
object osrsHideImeOnArticleOpen {
    fun hide(context: Context) {
        val activity = activityFrom(context)
        val focused = activity?.currentFocus
        val target = focused ?: activity?.window?.decorView ?: return
        hideFrom(target)
        focused?.clearFocus()
    }

    fun hideFrom(view: View) {
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
        val token = view.windowToken ?: return
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(token, 0)
    }

    private fun activityFrom(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        return current as? Activity
    }
}
