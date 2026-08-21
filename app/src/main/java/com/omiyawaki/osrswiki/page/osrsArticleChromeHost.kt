package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.databinding.ActivityPageBinding

/**
 * Article drawer chrome shared by [PageActivity] and the same-window overlay host.
 */
interface osrsArticleChromeHost {
    val articleChromeBinding: ActivityPageBinding
    fun openContents()
    fun closeContents(animate: Boolean = true)
    fun isContentsDrawerOpen(): Boolean
}
