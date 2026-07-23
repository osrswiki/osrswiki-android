package com.omiyawaki.osrswiki.undergroundmaps.ui

import android.text.TextUtils
import android.widget.TextView

internal const val OSRS_REALM_IDENTITY_MAX_LINES = 3

/** Allows long identities to grow, with an explicit ellipsis if a narrower viewport still clips. */
internal fun TextView.osrsApplyRealmIdentityLayout() {
    maxLines = OSRS_REALM_IDENTITY_MAX_LINES
    ellipsize = TextUtils.TruncateAt.END
}

internal data class osrsRealmIdentityLayoutState(
    val textLength: Int,
    val lineCount: Int,
    val lastVisibleEnd: Int,
    val ellipsisCount: Int,
    val honest: Boolean
)

/** Distinguishes complete or explicitly ellipsized text from the silent-clipping regression. */
internal fun TextView.osrsRealmIdentityLayoutStateOrNull(): osrsRealmIdentityLayoutState? {
    val textLayout = layout ?: return null
    if (textLayout.lineCount == 0) return null
    val lastLine = minOf(textLayout.lineCount, maxLines) - 1
    val lastVisibleEnd = textLayout.getLineVisibleEnd(lastLine)
    val ellipsisCount = textLayout.getEllipsisCount(lastLine)
    return osrsRealmIdentityLayoutState(
        textLength = text.length,
        lineCount = textLayout.lineCount,
        lastVisibleEnd = lastVisibleEnd,
        ellipsisCount = ellipsisCount,
        honest = lastVisibleEnd >= text.length || ellipsisCount > 0
    )
}
