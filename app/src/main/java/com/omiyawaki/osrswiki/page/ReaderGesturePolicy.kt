package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.settings.ReaderPreferences

enum class ReaderSwipeAction {
    BACK,
    CONTENTS
}

/** Pure policy kept separate from gesture ownership so a preference can never claim DOM input. */
object ReaderGesturePolicy {
    fun isEnabled(action: ReaderSwipeAction, preferences: ReaderPreferences): Boolean =
        when (action) {
            ReaderSwipeAction.BACK -> preferences.swipeRightBackEnabled
            ReaderSwipeAction.CONTENTS -> preferences.swipeLeftContentsEnabled
        }
}
