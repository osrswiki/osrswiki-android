package com.omiyawaki.osrswiki.activity

import android.view.View
import androidx.annotation.MainThread
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap
import kotlin.math.max

/**
 * Applies edge-to-edge system-bar padding without accumulating padding when insets are dispatched
 * repeatedly. Activities using adjustResize should keep [ImeInsetHandling.RESIZE] so the IME is
 * handled by the window resize and is not counted a second time as view padding.
 */
object EdgeToEdgeInsetCoordinator {
    enum class ImeInsetHandling {
        RESIZE,
        PADDING
    }

    data class Policy(
        val applyStatusBar: Boolean = true,
        val applyNavigationBar: Boolean = true,
        val imeInsetHandling: ImeInsetHandling = ImeInsetHandling.RESIZE
    )

    internal data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class Installation(
        val basePadding: Padding,
        var policy: Policy
    )

    private val installations = WeakHashMap<View, Installation>()

    internal fun maxPerEdge(first: Insets, second: Insets): Insets = Insets.of(
        max(first.left, second.left),
        max(first.top, second.top),
        max(first.right, second.right),
        max(first.bottom, second.bottom)
    )

    @MainThread
    fun apply(root: View, policy: Policy = Policy()) {
        val installation = installations[root] ?: Installation(
            basePadding = Padding(
                left = root.paddingLeft,
                top = root.paddingTop,
                right = root.paddingRight,
                bottom = root.paddingBottom
            ),
            policy = policy
        ).also { installations[root] = it }
        installation.policy = policy

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val resolved = resolvePadding(
                base = installation.basePadding,
                systemBars = Padding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom),
                statusBars = Padding(statusBars.left, statusBars.top, statusBars.right, statusBars.bottom),
                navigationBars = Padding(
                    navigationBars.left,
                    navigationBars.top,
                    navigationBars.right,
                    navigationBars.bottom
                ),
                displayCutout = Padding(
                    displayCutout.left,
                    displayCutout.top,
                    displayCutout.right,
                    displayCutout.bottom
                ),
                ime = Padding(ime.left, ime.top, ime.right, ime.bottom),
                policy = installation.policy
            )
            view.setPadding(resolved.left, resolved.top, resolved.right, resolved.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    internal fun resolvePadding(
        base: Padding,
        systemBars: Padding,
        statusBars: Padding,
        navigationBars: Padding,
        ime: Padding,
        policy: Policy,
        displayCutout: Padding = Padding(0, 0, 0, 0)
    ): Padding {
        val navigationBottom = when {
            !policy.applyNavigationBar -> displayCutout.bottom
            policy.imeInsetHandling == ImeInsetHandling.PADDING ->
                max(max(navigationBars.bottom, displayCutout.bottom), ime.bottom)
            else -> max(navigationBars.bottom, displayCutout.bottom)
        }
        return Padding(
            left = base.left + max(systemBars.left, displayCutout.left),
            top = base.top + max(
                if (policy.applyStatusBar) statusBars.top else 0,
                displayCutout.top
            ),
            right = base.right + max(systemBars.right, displayCutout.right),
            bottom = base.bottom + navigationBottom
        )
    }
}
