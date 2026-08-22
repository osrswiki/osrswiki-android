package com.omiyawaki.osrswiki.page

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.util.log.L

/**
 * Hosts article chrome on the launching activity so interactive-back reveals a
 * live, hittable previous page in the same window.
 */
object osrsArticleOverlayPresenter {
    const val HOST_VIEW_ID = R.id.osrs_article_overlay_host
    const val TAG_PREFIX = "osrs-article-overlay-"
    const val EXTRA_ARTICLE_OVERLAY_RESTORE = "osrs_article_overlay_restore"

    fun present(context: Context, intent: Intent): Boolean {
        val activity = activityFrom(context) as? FragmentActivity ?: return false
        if (activity is PageActivity) {
            return false
        }
        val host = ensureHost(activity)
        host.translationX = 0f
        host.alpha = 1f
        host.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val tag = TAG_PREFIX + System.nanoTime()
        val fragment = osrsArticleOverlayFragment.newInstance(intent)
        return try {
            activity.supportFragmentManager.beginTransaction()
                .add(host.id, fragment, tag)
                .commitNowAllowingStateLoss()
            host.visibility = View.VISIBLE
            host.isClickable = true
            host.isFocusable = true
            host.bringToFront()
            true
        } catch (error: Throwable) {
            L.e("osrsArticleOverlayPresenter: overlay present failed", error)
            try {
                activity.supportFragmentManager.findFragmentByTag(tag)?.let { added ->
                    activity.supportFragmentManager.beginTransaction()
                        .remove(added)
                        .commitNowAllowingStateLoss()
                }
            } catch (_: Throwable) {
            }
            false
        }
    }

    fun pop(activity: FragmentActivity): Boolean {
        val top = topFragment(activity) ?: return false
        activity.supportFragmentManager.beginTransaction()
            .remove(top)
            .commitNowAllowingStateLoss()
        if (topFragment(activity) == null) {
            detachHost(activity)
        }
        return true
    }

    fun popAll(activity: FragmentActivity) {
        val overlays = activity.supportFragmentManager.fragments
            .filterIsInstance<osrsArticleOverlayFragment>()
            .filter { it.isAdded && !it.isRemoving }
        if (overlays.isEmpty()) {
            detachHost(activity)
            return
        }
        val transaction = activity.supportFragmentManager.beginTransaction()
        overlays.forEach { transaction.remove(it) }
        transaction.commitNowAllowingStateLoss()
        detachHost(activity)
    }

    fun snapshot(activity: FragmentActivity): ArrayList<android.os.Bundle> {
        val snapshots = ArrayList<android.os.Bundle>()
        activity.supportFragmentManager.fragments
            .filterIsInstance<osrsArticleOverlayFragment>()
            .filter { it.isAdded && !it.isRemoving }
            .forEach { fragment ->
                fragment.arguments?.let { snapshots.add(android.os.Bundle(it)) }
            }
        return snapshots
    }

    fun restore(activity: FragmentActivity, snapshots: List<android.os.Bundle>) {
        snapshots.forEach { extras ->
            present(activity, Intent().putExtras(extras))
        }
    }

    fun topFragment(activity: FragmentActivity): osrsArticleOverlayFragment? {
        return activity.supportFragmentManager.fragments
            .filterIsInstance<osrsArticleOverlayFragment>()
            .lastOrNull { it.isAdded && !it.isRemoving }
    }

    fun slidingView(fragment: osrsArticleOverlayFragment): View? = fragment.slidingChrome()

    internal fun ensureHost(activity: FragmentActivity): FrameLayout {
        activity.findViewById<FrameLayout>(HOST_VIEW_ID)?.let { return it }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: throw IllegalStateException("Activity content view is missing")
        val host = FrameLayout(activity).apply {
            id = HOST_VIEW_ID
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            elevation = 0f
            isClickable = true
            isFocusable = true
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        content.addView(host)
        return host
    }

    fun resetChrome(activity: FragmentActivity) {
        activity.supportFragmentManager.fragments
            .filterIsInstance<osrsArticleOverlayFragment>()
            .forEach { fragment ->
                fragment.slidingChrome()?.let { sliding ->
                    sliding.animate().cancel()
                    sliding.translationX = 0f
                    sliding.alpha = 1f
                }
            }
        val host = activity.findViewById<View>(HOST_VIEW_ID) ?: return
        host.animate().cancel()
        host.translationX = 0f
        host.alpha = 1f
        if (topFragment(activity) == null) {
            detachHost(activity)
        }
    }

    fun detachHost(activity: FragmentActivity) {
        val host = activity.findViewById<ViewGroup>(HOST_VIEW_ID) ?: return
        host.animate().cancel()
        host.translationX = 0f
        host.alpha = 1f
        host.visibility = View.GONE
        host.isClickable = false
        host.isFocusable = false
        (host.parent as? ViewGroup)?.removeView(host)
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
