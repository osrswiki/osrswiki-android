package com.omiyawaki.osrswiki.undergroundmaps

import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmSelectorDebugState

/**
 * Standalone compatibility host for the production map fragment.
 *
 * The full app embeds [osrsUndergroundMapsFragment] in its persistent bottom-navigation host.
 * Keeping this thin activity preserves the dedicated map instrumentation and packaging contract.
 */
class osrsUndergroundMapsActivity : AppCompatActivity() {
    private val mapFragment: osrsUndergroundMapsFragment
        get() = requireNotNull(
            supportFragmentManager.findFragmentByTag(OSRS_MAP_FRAGMENT_TAG)
                as? osrsUndergroundMapsFragment
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        val container = FrameLayout(this).apply { id = R.id.osrs_underground_map_host }
        setContentView(container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(container.id, osrsUndergroundMapsFragment(), OSRS_MAP_FRAGMENT_TAG)
                .commitNow()
        }
    }

    fun reloadStyleForTesting(): Boolean = mapFragment.reloadStyleForTesting()
    fun selectRealmForTesting(realmId: String): Boolean =
        mapFragment.selectRealmForTesting(realmId)
    fun openRealmSelectorForTesting(): Boolean = mapFragment.openRealmSelectorForTesting()
    fun filterRealmSelectorForTesting(query: String): Boolean =
        mapFragment.filterRealmSelectorForTesting(query)
    fun dismissRealmSelectorForTesting() = mapFragment.dismissRealmSelectorForTesting()
    fun focusRealmSelectorSearchForTesting(): Boolean =
        mapFragment.focusRealmSelectorSearchForTesting()
    fun showErrorForTesting(message: String = OSRS_HOST_TEST_ERROR_MESSAGE) =
        mapFragment.showErrorForTesting(message)
    fun clearErrorForTesting() = mapFragment.clearErrorForTesting()
    fun realmSelectorStateForTesting(): osrsRealmSelectorDebugState? =
        mapFragment.realmSelectorStateForTesting()
    fun surfaceRealmIdForTesting(): String? = mapFragment.surfaceRealmIdForTesting()
    fun firstMultiPlaneRealmIdForTesting(): String? =
        mapFragment.firstMultiPlaneRealmIdForTesting()
    fun firstSinglePlaneRealmIdForTesting(): String? =
        mapFragment.firstSinglePlaneRealmIdForTesting()
    fun firstFourPlaneRealmIdForTesting(): String? =
        mapFragment.firstFourPlaneRealmIdForTesting()
    fun compactPlaneZeroRealmIdForTesting(): String? =
        mapFragment.compactPlaneZeroRealmIdForTesting()
    fun lowestPlaneForRealmForTesting(realmId: String): Int? =
        mapFragment.lowestPlaneForRealmForTesting(realmId)
    fun longestRealmIdForTesting(): String? = mapFragment.longestRealmIdForTesting()
    fun realmLinksDialogStateForTesting(): osrsRealmLinksDialogDebugState? =
        mapFragment.realmLinksDialogStateForTesting()
    fun lastLinkDialogPhaseForTesting(): osrsLinkDialogOpenPhase? =
        mapFragment.lastLinkDialogPhaseForTesting()
    fun openRealmLinksForTesting(): Boolean = mapFragment.openRealmLinksForTesting()
    fun linksActionAccessibilityTextForTesting(): String? =
        mapFragment.linksActionAccessibilityTextForTesting()
    fun selectPlaneForTesting(plane: Int): Boolean = mapFragment.selectPlaneForTesting(plane)
    fun moveCameraTargetForTesting(
        latitude: Double,
        longitude: Double,
        zoom: Double? = null,
        bearing: Double? = null,
        tilt: Double? = null
    ): Boolean = mapFragment.moveCameraTargetForTesting(
        latitude = latitude,
        longitude = longitude,
        zoom = zoom,
        bearing = bearing,
        tilt = tilt
    )
    fun startZoomMomentumForTesting(
        velocityLevelsPerSecond: Double,
        focalXPx: Int,
        focalYPx: Int
    ): Boolean = mapFragment.startZoomMomentumForTesting(
        velocityLevelsPerSecond = velocityLevelsPerSecond,
        focalXPx = focalXPx,
        focalYPx = focalYPx
    )
    fun selectAuthoritativeLinkForTesting(
        linkId: String,
        traversalDirection: osrsRealmLinkTraversalDirection? = null,
        maximumViewportExtentDp: Double? = null
    ): Boolean = mapFragment.selectAuthoritativeLinkForTesting(
        linkId = linkId,
        traversalDirection = traversalDirection,
        maximumViewportExtentDp = maximumViewportExtentDp
    )
    fun debugStateForTesting(): osrsMapDiagnostics = mapFragment.debugStateForTesting()

    private companion object {
        const val OSRS_MAP_FRAGMENT_TAG = "osrs_underground_map_fragment"
        const val OSRS_HOST_TEST_ERROR_MESSAGE =
            "Direction 3 deterministic error surface\nSelector obstruction verification"
    }
}
