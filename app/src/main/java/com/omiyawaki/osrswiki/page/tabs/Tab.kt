package com.omiyawaki.osrswiki.page.tabs

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents an individual open tab within the application.
 * Each tab maintains its own navigation history (backstack).
 *
 * @property id A unique identifier for this tab session.
 * @property backStack The list of [PageBackStackItem]s representing the navigation history for this tab.
 * The last item in the list is the currently displayed page.
 */
@Serializable
data class Tab(
    // A unique ID for the tab, useful if needing to differentiate tabs beyond list position.
    // Not strictly present in Wikipedia's Tab.kt as a persisted field, but can be useful.
    val id: String = UUID.randomUUID().toString(),
    val backStack: MutableList<PageBackStackItem> = mutableListOf()
) {
    /**
     * The currently active [PageBackStackItem] in this tab, or null if the backstack is empty.
     */
    val currentTabInfo: PageBackStackItem?
        get() = backStack.lastOrNull()

    /**
     * Checks if it's possible to navigate backward in this tab's history.
     */
    fun canGoBack(): Boolean {
        return backStack.size > 1
    }

    /**
     * Checks if it's possible to navigate forward (not implemented in this basic version,
     * as forward history is typically managed by popping from a separate forwardStack or by index).
     * For simplicity, Wikipedia's Tab manages only a backStack and re-constructs forward on demand if needed,
     * or relies on new page loads.
     */
    // fun canGoForward(): Boolean { ... }

    /**
     * Pushes a new item onto the backstack.
     * This is typically called when navigating to a new page.
     */
    fun push(item: PageBackStackItem) {
        // In a more complex scenario with forward history, pushing a new item
        // would clear any existing forward stack.
        backStack.add(item)
    }

    /**
     * Pops the current item from the backstack.
     * This is typically called when navigating back.
     * @return The item that was at the top of the stack (the current page before popping),
     * or null if the stack was empty or had only one item (should not happen if canGoBack() is true).
     */
    fun pop(): PageBackStackItem? {
        if (canGoBack()) {
            return backStack.removeAt(backStack.size - 1)
        }
        return null
    }

    /**
     * Peeks at the item that would become current if pop() was called.
     * @return The item that would be next on navigating back, or null if cannot go back.
     */
    fun peekPrevious(): PageBackStackItem? {
        return if (canGoBack()) {
            backStack[backStack.size - 2]
        } else {
            null
        }
    }

    /**
     * Clears the entire backstack for this tab.
     */
    fun clearBackStack() {
        backStack.clear()
    }
}
