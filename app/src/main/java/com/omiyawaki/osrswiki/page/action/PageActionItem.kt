package com.omiyawaki.osrswiki.page.action

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.omiyawaki.osrswiki.R // Import your app's R class

enum class PageActionItem(
    val id: Int, // Unique internal ID for the action
    @IdRes val viewIdRes: Int,
    @StringRes val titleResId: Int,
    @DrawableRes val defaultIconResId: Int,
    val isAvailableOnMobileWeb: Boolean = true // Retained from Wikipedia for structural similarity
) {
    SAVE(0, R.id.page_action_save, R.string.page_action_save, R.drawable.ic_page_action_save_border, false) {
        override fun select(cb: Callback) { cb.onSaveSelected() }
    },
    // LANGUAGE action removed as per request
    FIND_IN_ARTICLE(2, R.id.page_action_find_in_article, R.string.page_action_find_in_article, R.drawable.ic_page_action_find_in_page) {
        override fun select(cb: Callback) { cb.onFindInArticleSelected() }
    },
    THEME(3, R.id.page_action_theme, R.string.page_action_theme, R.drawable.ic_page_action_theme, false) {
        override fun select(cb: Callback) { cb.onThemeSelected() }
    },
    CONTENTS(4, R.id.page_action_contents, R.string.page_action_contents, R.drawable.ic_page_action_contents, false) {
        override fun select(cb: Callback) { cb.onContentsSelected() }
    };
    // TODO: Add other actions here if mirroring Wikipedia's overflow menu (Share, Add to Watchlist, etc.)

    abstract fun select(cb: Callback)

    interface Callback {
        fun onSaveSelected()
        // fun onLanguageSelected() // Removed
        fun onFindInArticleSelected()
        fun onThemeSelected()
        fun onContentsSelected()
        // TODO: Add more callback methods as new actions from overflow menu are implemented
    }

    companion object {
        // Defines the default items and order for the bottom action bar based on their internal 'id'
        val DEFAULT_BOTTOM_BAR_ITEMS_ORDER: List<PageActionItem> = listOf(
            SAVE,
            FIND_IN_ARTICLE,
            THEME,
            CONTENTS
            // LANGUAGE removed
        )

        fun findByInternalId(id: Int): PageActionItem? {
            return entries.find { it.id == id }
        }

        fun findByViewId(@IdRes viewId: Int): PageActionItem? {
            return entries.find { it.viewIdRes == viewId }
        }

        // Helper to get the correct save icon based on state
        @DrawableRes
        fun getSaveIcon(isSaved: Boolean): Int {
            return if (isSaved) R.drawable.ic_page_action_save_filled else R.drawable.ic_page_action_save_border
        }
    }
}
