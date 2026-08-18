package com.omiyawaki.osrswiki.page

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

/**
 * Snapshot of the activity that launched [PageActivity], used as the
 * interactive-back underlay when there is no previous article bitmap.
 */
internal object osrsUnderlyingActivityPreview {
    @Volatile
    private var bitmap: Bitmap? = null

    fun peek(): Bitmap? = bitmap

    fun captureFromCaller(context: Context) {
        val activity = activityFrom(context) ?: return
        if (activity is PageActivity) {
            return
        }
        val source = activity.findViewById<View>(android.R.id.content) ?: activity.window?.decorView
        if (source == null || source.width <= 0 || source.height <= 0) {
            return
        }
        val next = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        source.draw(Canvas(next))
        val previous = bitmap
        bitmap = next
        if (previous != null && previous != next) {
            previous.recycle()
        }
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
