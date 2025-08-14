package com.omiyawaki.osrswiki.ui.common

import android.view.View

/**
 * Defines the desired toolbar behavior for a fragment.
 */
enum class ToolbarPolicy {
    /** The toolbar should be hidden. */
    HIDDEN,
    /** The toolbar should be visible and collapse with content scrolling. */
    COLLAPSIBLE_WITH_CONTENT
}

/**
 * Interface for fragments to provide their desired toolbar policy.
 */
interface FragmentToolbarPolicyProvider {
    fun getToolbarPolicy(): ToolbarPolicy
}
