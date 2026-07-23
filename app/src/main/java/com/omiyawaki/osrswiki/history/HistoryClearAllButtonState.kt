package com.omiyawaki.osrswiki.history

import android.view.View

object HistoryClearAllButtonState {
    fun apply(clearAllButton: View?, hasEntries: Boolean) {
        clearAllButton?.apply {
            visibility = if (hasEntries) View.VISIBLE else View.GONE
            isEnabled = hasEntries
            isClickable = hasEntries
            isFocusable = hasEntries
        }
    }
}
