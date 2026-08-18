package com.omiyawaki.osrswiki.undergroundmaps

import android.graphics.PointF
import android.graphics.Point
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsRealmRepository
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsStagedRealmAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCatalog
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsCameraCenterEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsCameraClampCallbackGuard
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsCameraReleaseSpeed
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsCameraStatesEquivalent
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsCameraTargetsEquivalent
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsClampCameraToEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsDampedSpringAxisState
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsDampedSpringIsSettled
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsDecayZoomMomentumVelocity
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsElasticAxisPosition
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsFiniteRealmMinimumZoom
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRelativeLinkZoomForAssets
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsResolveMapLibreLongitudeRepresentation
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmGroupLabel
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmEndpointMapper
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkPosition
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmLinkTraversalDirection
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmRecord
import com.omiyawaki.osrswiki.undergroundmaps.model.isCanonicalSelectorRealm
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsSurfaceDefaultZoomForAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsStepDampedSpring
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsPinchZoomVelocityLevelsPerSecond
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_ZOOM_MOMENTUM_MINIMUM_RELEASE_VELOCITY
import com.omiyawaki.osrswiki.undergroundmaps.model.OSRS_ZOOM_MOMENTUM_STOP_VELOCITY
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsDefaultZoomForAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRasterComposition
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRasterCompositionFor
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRasterCompositionTransition
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRasterResourceIdentity
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsUndergroundMapDefaultView
import com.omiyawaki.osrswiki.undergroundmaps.model.rasterProjectionOrNull
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraState
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraPersistenceOwnership
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsInstalledCameraIdentity
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmAction
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmStateReducer
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmStateStore
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsRealmUiState
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinkCatalogCache
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinkRow
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialog
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmLinksDialogDebugState
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmPresentationCatalog
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmSelector
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmSelectorDebugState
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmSelectorIndex
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsRealmIdentityLayoutStateOrNull
import com.omiyawaki.osrswiki.undergroundmaps.ui.OSRS_COMPASS_RESET_DURATION_MILLIS
import com.omiyawaki.osrswiki.undergroundmaps.ui.OSRS_COMPASS_SIZE_DP
import com.omiyawaki.osrswiki.undergroundmaps.ui.OSRS_SELECTOR_BOTTOM_GAP_DP
import com.omiyawaki.osrswiki.undergroundmaps.ui.OSRS_SELECTOR_HORIZONTAL_MARGIN_DP
import com.omiyawaki.osrswiki.undergroundmaps.ui.osrsNorthResetCompassView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot

internal const val OSRS_TOP_LEFT_CONTROL_WIDTH_DP = 48
internal const val OSRS_TOP_LEFT_CONTROL_CORNER_RADIUS_DP = 22
internal const val OSRS_CONTROL_GESTURE_SAFETY_DP = 1
internal const val OSRS_REALM_LINKS_UI_ENABLED = false

internal fun osrsSafeDrawingEdgeMarginPx(
    systemBarInsetPx: Int,
    displayCutoutInsetPx: Int,
    visualMarginPx: Int
): Int = maxOf(systemBarInsetPx, displayCutoutInsetPx) + visualMarginPx

internal fun osrsSymmetricControlSideMarginPx(
    horizontalMarginPx: Int,
    systemBarSideInsetPx: Int,
    displayCutoutSideInsetPx: Int,
    systemGestureSideInsetPx: Int,
    gestureSafetyPx: Int
): Int = maxOf(
    horizontalMarginPx,
    systemBarSideInsetPx,
    displayCutoutSideInsetPx,
    systemGestureSideInsetPx + gestureSafetyPx
)

internal fun osrsSelectorTopObstructionPx(
    systemObstructionBottomPx: Int,
    controlSeparationPx: Int,
    floorBottomPx: Int?,
    linksBottomPx: Int?,
    statusBottomPx: Int?
): Int = maxOf(
    systemObstructionBottomPx,
    floorBottomPx?.plus(controlSeparationPx) ?: 0,
    linksBottomPx?.plus(controlSeparationPx) ?: 0,
    statusBottomPx?.plus(controlSeparationPx) ?: 0
)

internal fun osrsLinksTopMarginPx(
    systemTopInsetPx: Int,
    floorVisible: Boolean,
    floorBottomPx: Int,
    controlSeparationPx: Int,
    topMarginPx: Int = 16
): Int = if (floorVisible && floorBottomPx > 0) {
    floorBottomPx + controlSeparationPx
} else {
    systemTopInsetPx + topMarginPx
}

internal fun osrsHorizontalRangesOverlapWithSeparation(
    selectorLeftPx: Int,
    selectorRightPx: Int,
    controlLeftPx: Int,
    controlRightPx: Int,
    separationPx: Int
): Boolean =
    selectorLeftPx < controlRightPx + separationPx &&
        selectorRightPx > controlLeftPx - separationPx

class osrsUndergroundMapsFragment : Fragment() {
    private lateinit var rootView: FrameLayout
    private lateinit var mapView: MapView
    private lateinit var compassView: osrsNorthResetCompassView
    private lateinit var selectorButton: MaterialButton
    private lateinit var linksButton: MaterialButton
    private lateinit var floorControlCard: MaterialCardView
    private lateinit var floorRow: LinearLayout
    private lateinit var floorUpButton: ImageButton
    private lateinit var floorCurrentText: TextView
    private lateinit var floorDownButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var progress: LinearProgressIndicator

    private lateinit var repository: osrsRealmRepository
    private lateinit var stateStore: osrsRealmStateStore
    private val reducer = osrsRealmStateReducer()
    private val cameraPersistenceOwnership = osrsCameraPersistenceOwnership()
    private var state: osrsRealmUiState? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var switchJob: Job? = null
    private var appCameraTargetSettleRunnable: Runnable? = null
    private var compassCameraUpdateRunnable: Runnable? = null
    private var compassResetRefreshRunnable: Runnable? = null
    private var realmSelector: osrsRealmSelector? = null
    private var linksDialog: osrsRealmLinksDialog? = null
    private var realmPresentations: osrsRealmPresentationCatalog? = null
    private var realmLinkCatalogCache: osrsRealmLinkCatalogCache? = null
    private var realmSelectorIndex: osrsRealmSelectorIndex? = null
    private var styleGeneration = 0
    private var installedRequestId = Long.MIN_VALUE
    private var installedStyleGeneration = Int.MIN_VALUE
    private var activeSourceId: String? = null
    private var activeLayerId: String? = null
    private var activeRasterRealmId: String? = null
    private var activeRasterResources: List<osrsRasterResourceIdentity> = emptyList()
    private var activeStagedAssets: Map<Int, osrsStagedRealmAsset> = emptyMap()
    private var activeRasterComposition: osrsRasterComposition? = null
    private var lastRasterTransition: osrsRasterCompositionTransition? = null
    private var stagedAssetSha256: String? = null
    private var stagedAssetPath: String? = null
    private var switchRequestedAtNanos: Long? = null
    private var switchCompletedAtNanos: Long? = null
    private var lastStageNanos: Long? = null
    private var lastRenderMarker: String = "not-rendered"
    private var lastError: String? = null
    private var lastSelectorFilterNanos: Long? = null
    private var lastSelectorToggleNanos: Long? = null
    private var lastSelectorOutsideDismissNanos: Long? = null
    private var selectorOutsideDismissCount = 0
    private var lastLinkDialogOpenNanos: Long? = null
    private var coldLinkDialogOpenNanos: Long? = null
    private var lastLinkFilterNanos: Long? = null
    private var lastLinkFilterResultCount: Int? = null
    private var lastLinkDialogPhase: osrsLinkDialogOpenPhase? = null
    private var linkDialogOpenOrdinal = 0
    private var savedRealmId: String? = null
    private var savedPlane: Int? = null
    private var restoredSelectorExpanded = false
    private var restoredSelectorQuery = ""
    private var restoredSelectorSearchFocused = false
    private var systemBarInsets: Insets = Insets.NONE
    private var displayCutoutInsets: Insets = Insets.NONE
    private var systemGestureInsets: Insets = Insets.NONE
    private var imeBottomInset = 0
    private var imeVisible = false
    private var compactLandscapeImeChrome = false
    private var lastLinkNavigation: osrsLinkNavigationState? = null
    private var lastCameraPersistenceMarker: String = "not-attempted"
    private var activeCameraEnvelopeBinding: osrsActiveCameraEnvelopeBinding? = null
    private val cameraClampCallbackGuard = osrsCameraClampCallbackGuard()
    private var lastRequestedCameraTarget: osrsCameraTarget? = null
    private var lastFinalCameraTarget: osrsCameraTarget? = null
    private var lastCameraClampState: String = "inactive"
    private var lastObservedMapLibreLongitude: Double? = null
    private var cameraGestureTouchActive = false
    private var cameraGesturePanDetected = false
    private var cameraGestureHadMultiplePointers = false
    private var cameraGestureDownX = 0f
    private var cameraGestureDownY = 0f
    private val cameraGestureTouchSlop by lazy {
        ViewConfiguration.get(mapView.context).scaledTouchSlop.toFloat()
    }
    private var cameraVelocityTracker: VelocityTracker? = null
    private var cameraPinchLastSpan = 0.0
    private var cameraPinchLastEventMillis = 0L
    private var cameraPinchReleaseVelocityLevelsPerSecond = 0.0
    private var cameraPinchFocalPoint: Point? = null
    private var cameraPinchFocalCoordinate: osrsCameraTarget? = null
    private var cameraZoomMomentumFocalDriftPx: Double? = null
    private var cameraZoomVelocityLevelsPerSecond = 0.0
    private var cameraZoomMomentumStartZoom: Double? = null
    private var cameraZoomMomentumPeakContinuation = 0.0
    private var lastCameraZoomMomentumDurationNanos: Long? = null
    private var lastCameraZoomMomentumFrameCount = 0
    private var cameraEdgePhysicsPhase = "idle"
    private var cameraEdgePhysicsFrameCallback: Choreographer.FrameCallback? = null
    private var cameraEdgePhysicsLastFrameNanos = 0L
    private var cameraEdgePhysicsStartedNanos: Long? = null
    private var cameraEdgePhysicsFrameCount = 0
    private var cameraEdgeVelocityXPxPerSecond = 0.0
    private var cameraEdgeVelocityYPxPerSecond = 0.0
    private var cameraSpringLatitude: osrsDampedSpringAxisState? = null
    private var cameraSpringLongitude: osrsDampedSpringAxisState? = null
    private var cameraSpringTarget: osrsCameraTarget? = null
    private var cameraEdgePeakLatitudeOvershoot = 0.0
    private var cameraEdgePeakLongitudeOvershoot = 0.0
    private var lastCameraEdgeBounceDurationNanos: Long? = null
    private var lastCameraEdgeBounceFrameCount = 0
    private var cameraEdgePhysicsApplying = false
    private var activeCopySafeMinZoom: Double? = null
    private var lastCameraClampNanos: Long? = null
    private var cameraClampCount = 0
    private val switchDurationsNanos = ArrayDeque<Long>()
    private val simpleControlDurationsNanos = ArrayDeque<Long>()
    private val repeatedLinkDialogDurationsNanos = ArrayDeque<Long>()
    private val cameraClampDurationsNanos = ArrayDeque<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        MapLibre.getInstance(requireContext().applicationContext)
        repository = osrsRealmRepository(requireContext())
        stateStore = osrsRealmStateStore(requireContext())
        savedRealmId = savedInstanceState?.getString(OSRS_SAVED_REALM_ID)
        savedPlane = savedInstanceState?.takeIf { it.containsKey(OSRS_SAVED_PLANE) }
            ?.getInt(OSRS_SAVED_PLANE)
        restoredSelectorExpanded =
            savedInstanceState?.getBoolean(OSRS_SAVED_SELECTOR_EXPANDED) ?: false
        restoredSelectorQuery =
            savedInstanceState?.getString(OSRS_SAVED_SELECTOR_QUERY).orEmpty()
        restoredSelectorSearchFocused =
            savedInstanceState?.getBoolean(OSRS_SAVED_SELECTOR_SEARCH_FOCUSED) ?: false

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        buildContentView()
        mapView.onCreate(savedInstanceState)
        registerMapDiagnostics()
        initializeMapLibre()
        loadManifest()
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installSelectorBackBehavior()
    }

    private fun buildContentView() {
        val context = requireContext()
        rootView = FrameLayout(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.osrs_map_background))
            clipChildren = false
            clipToPadding = false
        }
        mapView = MapView(context).apply {
            id = R.id.osrs_underground_map
            contentDescription = getString(R.string.map_content_description)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        rootView.addView(mapView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        compassView = osrsNorthResetCompassView(context).apply {
            id = R.id.osrs_map_compass
            contentDescription = getString(R.string.map_compass_description)
            setOnClickListener { resetMapBearingToNorth() }
        }
        rootView.addView(compassView, FrameLayout.LayoutParams(
            dp(OSRS_COMPASS_SIZE_DP),
            dp(OSRS_COMPASS_SIZE_DP),
            Gravity.TOP or Gravity.END
        ))

        linksButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            id = R.id.osrs_realm_links
            text = null
            icon = ContextCompat.getDrawable(
                context,
                R.drawable.osrs_ic_search
            )
            iconTint = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.osrs_parchment)
            )
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            isAllCaps = false
            minWidth = dp(48)
            minimumWidth = dp(48)
            minHeight = dp(48)
            minimumHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            iconPadding = 0
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.osrs_map_control_surface)
            )
            strokeColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.osrs_underground_parchment_dark)
            )
            strokeWidth = dp(1)
            cornerRadius = dp(OSRS_TOP_LEFT_CONTROL_CORNER_RADIUS_DP)
            visibility = View.GONE
            isEnabled = OSRS_REALM_LINKS_UI_ENABLED
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (OSRS_REALM_LINKS_UI_ENABLED) {
                setOnClickListener { showRealmLinks() }
            }
        }
        statusText = TextView(context).apply {
            id = R.id.osrs_map_status
            text = getString(R.string.reading_manifest)
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.osrs_parchment))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(ContextCompat.getColor(context, R.color.osrs_map_control_surface))
            contentDescription = text
            visibility = View.GONE
        }
        progress = LinearProgressIndicator(context).apply {
            id = R.id.osrs_map_progress
            isIndeterminate = true
        }
        rootView.addView(progress, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(3),
            Gravity.TOP
        ))
        rootView.addView(statusText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })
        rootView.addView(linksButton, FrameLayout.LayoutParams(
            dp(OSRS_TOP_LEFT_CONTROL_WIDTH_DP),
            dp(OSRS_MINIMUM_TOUCH_TARGET_DP),
            Gravity.TOP or Gravity.START
        ))

        floorUpButton = osrsFloorButton(
            id = R.id.osrs_floor_up,
            icon = R.drawable.osrs_ic_arrow_up
        )
        floorCurrentText = TextView(context).apply {
            id = R.id.osrs_floor_current
            gravity = Gravity.CENTER
            textSize = 17f
            setTextColor(ContextCompat.getColor(context, R.color.osrs_parchment))
            setPadding(0, dp(4), 0, dp(4))
            minWidth = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
        }
        floorDownButton = osrsFloorButton(
            id = R.id.osrs_floor_down,
            icon = R.drawable.osrs_ic_arrow_down
        )
        floorRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // The edge buttons must meet the card outline so their pressed-state fill reaches
            // the rounded top and bottom instead of leaving a dark strip around the highlight.
            setPadding(0, 0, 0, 0)
            addView(floorUpButton, LinearLayout.LayoutParams(
                dp(OSRS_MINIMUM_TOUCH_TARGET_DP),
                dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
            ))
            addView(floorCurrentText, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(floorDownButton, LinearLayout.LayoutParams(
                dp(OSRS_MINIMUM_TOUCH_TARGET_DP),
                dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
            ))
        }
        floorControlCard = MaterialCardView(context).apply {
            id = R.id.osrs_floor_controls
            radius = dp(OSRS_TOP_LEFT_CONTROL_CORNER_RADIUS_DP).toFloat()
            cardElevation = dp(4).toFloat()
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(context, R.color.osrs_underground_parchment_dark)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.osrs_map_control_surface))
            contentDescription = getString(R.string.floor_controls_description)
            visibility = View.GONE
            addView(floorRow)
        }
        rootView.addView(floorControlCard, FrameLayout.LayoutParams(
            dp(OSRS_TOP_LEFT_CONTROL_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            displayCutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            systemGestureInsets = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            updateCompactLandscapeImeChrome(
                compact = imeVisible && rootView.width > rootView.height
            )
            (progress.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = systemBarInsets.top
                progress.layoutParams = this
            }
            (statusText.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = if (compactLandscapeImeChrome) {
                    0
                } else {
                    systemBarInsets.top + dp(12)
                }
                statusText.layoutParams = this
            }
            (floorControlCard.layoutParams as FrameLayout.LayoutParams).apply {
                val topVisualMargin = osrsSafeDrawingEdgeMarginPx(
                    systemBarInsets.top,
                    displayCutoutInsets.top,
                    dp(OSRS_SELECTOR_BOTTOM_GAP_DP)
                )
                topMargin = topVisualMargin
                leftMargin = osrsSymmetricControlSideMarginPx(
                    horizontalMarginPx = dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP),
                    systemBarSideInsetPx = systemBarInsets.left,
                    displayCutoutSideInsetPx = displayCutoutInsets.left,
                    systemGestureSideInsetPx = systemGestureInsets.left,
                    gestureSafetyPx = dp(OSRS_CONTROL_GESTURE_SAFETY_DP)
                )
                floorControlCard.layoutParams = this
            }
            (linksButton.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.TOP or Gravity.START
                val topVisualMargin = osrsSafeDrawingEdgeMarginPx(
                    systemBarInsets.top,
                    displayCutoutInsets.top,
                    dp(OSRS_SELECTOR_BOTTOM_GAP_DP)
                )
                leftMargin = osrsSymmetricControlSideMarginPx(
                    horizontalMarginPx = dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP),
                    systemBarSideInsetPx = systemBarInsets.left,
                    displayCutoutSideInsetPx = displayCutoutInsets.left,
                    systemGestureSideInsetPx = systemGestureInsets.left,
                    gestureSafetyPx = dp(OSRS_CONTROL_GESTURE_SAFETY_DP)
                )
                linksButton.layoutParams = this
            }
            updateTopLeftControlGeometry()
            updateCompassGeometry()
            updateRealmSelectorGeometry()
            insets
        }
        rootView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTopLeftControlGeometry()
            updateActiveCopySafeMinZoom()
            updateRealmSelectorGeometry()
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    /**
     * At large font scales, a non-extract landscape IME leaves roughly 104 dp of active height.
     * Temporarily using the already edge-to-edge status-bar band provides enough room for the
     * selector's two 48 dp rows plus the visible error separation. The full error remains the
     * accessibility label while its visual banner is explicitly ellipsized to one line.
     */
    private fun updateCompactLandscapeImeChrome(compact: Boolean) {
        if (compactLandscapeImeChrome == compact) return
        compactLandscapeImeChrome = compact
        val insetsController = WindowCompat.getInsetsController(requireActivity().window, rootView)
        if (compact) {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            statusText.maxLines = 1
            statusText.includeFontPadding = false
            statusText.gravity = Gravity.CENTER_VERTICAL
            statusText.setPadding(dp(12), 0, dp(12), 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                statusText,
                OSRS_COMPACT_STATUS_MIN_TEXT_SP,
                OSRS_STATUS_TEXT_SP,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            floorRow.setPadding(0, 0, 0, 0)
            floorCurrentText.includeFontPadding = false
            floorCurrentText.gravity = Gravity.CENTER
            floorCurrentText.setPadding(0, 0, 0, 0)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                floorCurrentText,
                OSRS_COMPACT_FLOOR_MIN_TEXT_SP,
                OSRS_FLOOR_TEXT_SP,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
            TextViewCompat.setAutoSizeTextTypeWithDefaults(
                statusText,
                TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
            )
            statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, OSRS_STATUS_TEXT_SP.toFloat())
            statusText.maxLines = 2
            statusText.includeFontPadding = true
            statusText.gravity = Gravity.NO_GRAVITY
            statusText.setPadding(dp(12), dp(8), dp(12), dp(8))
            floorRow.setPadding(0, 0, 0, 0)
            TextViewCompat.setAutoSizeTextTypeWithDefaults(
                floorCurrentText,
                TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
            )
            floorCurrentText.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                OSRS_FLOOR_TEXT_SP.toFloat()
            )
            floorCurrentText.includeFontPadding = true
            floorCurrentText.gravity = Gravity.CENTER
            floorCurrentText.setPadding(0, dp(4), 0, dp(4))
        }
        (statusText.layoutParams as FrameLayout.LayoutParams).apply {
            height = if (compact) dp(OSRS_COMPACT_STATUS_HEIGHT_DP) else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            topMargin = if (compact) 0 else systemBarInsets.top + dp(12)
            statusText.layoutParams = this
        }
        (floorCurrentText.layoutParams as LinearLayout.LayoutParams).apply {
            height = if (compact) dp(OSRS_COMPACT_FLOOR_LABEL_HEIGHT_DP) else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            floorCurrentText.layoutParams = this
        }
        rootView.post {
            ViewCompat.requestApplyInsets(rootView)
            updateRealmSelectorGeometry()
        }
    }

    private fun osrsFloorButton(id: Int, icon: Int): ImageButton =
        ImageButton(requireContext()).apply {
            this.id = id
            minimumWidth = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
            minimumHeight = dp(OSRS_MINIMUM_TOUCH_TARGET_DP)
            setImageResource(icon)
            background = ContextCompat.getDrawable(
                requireContext(),
                android.R.drawable.list_selector_background
            )
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

    private fun installSelectorBackBehavior() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val selector = realmSelector
                    val selectorState = selector?.debugState()
                    when {
                        selectorState?.expanded == true && selectorState.imeVisible ->
                            selector.hideImeWithoutCollapsing()
                        selectorState?.expanded == true ->
                            selector.collapse(resetQuery = false)
                        else -> {
                            isEnabled = false
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            }
        )
    }

    private fun updateRealmSelectorGeometry() {
        // Layout/inset callbacks can remain queued while a scenario or host tab tears this
        // fragment down. They must not resolve density through a detached Fragment context.
        if (!isAdded || view == null || !::rootView.isInitialized) return
        // Insets can be dispatched before the first layout pass on Android 16. The layout-change
        // listener below will recompute the selector as soon as the viewport has a real width.
        if (rootView.width <= 0) return
        val separationPx = dp(OSRS_CONTROL_SEPARATION_DP)
        val projectedSelector = realmSelector?.projectedHorizontalBounds(rootView.width)
        fun View.obstructionBottomIfOverlapping(): Int? {
            val overlapsSelector = projectedSelector == null ||
                osrsHorizontalRangesOverlapWithSeparation(
                    selectorLeftPx = projectedSelector.left,
                    selectorRightPx = projectedSelector.right,
                    controlLeftPx = left,
                    controlRightPx = right,
                    separationPx = separationPx
                )
            return takeIf {
                it.visibility == View.VISIBLE && it.bottom > 0 && overlapsSelector
            }?.bottom
        }
        val topObstruction = osrsSelectorTopObstructionPx(
            systemObstructionBottomPx = osrsSafeDrawingEdgeMarginPx(
                systemBarInsets.top,
                displayCutoutInsets.top,
                dp(OSRS_SELECTOR_BOTTOM_GAP_DP)
            ),
            controlSeparationPx = separationPx,
            floorBottomPx = floorControlCard.obstructionBottomIfOverlapping(),
            linksBottomPx = linksButton.obstructionBottomIfOverlapping(),
            statusBottomPx = statusText.takeIf {
                it.visibility == View.VISIBLE && it.bottom > 0
            }?.bottom
        )
        realmSelector?.updateWindowGeometry(
            systemTopInsetPx = systemBarInsets.top,
            systemBottomInsetPx = systemBarInsets.bottom,
            imeBottomInsetPx = imeBottomInset,
            imeVisible = imeVisible,
            topObstructionPx = topObstruction
        )
    }

    private fun updateTopLeftControlGeometry() {
        if (!::linksButton.isInitialized || !::floorControlCard.isInitialized) return
        val params = linksButton.layoutParams as? FrameLayout.LayoutParams ?: return
        val floorVisible = floorControlCard.visibility == View.VISIBLE &&
            floorControlCard.height > 0
        val desiredTop = osrsLinksTopMarginPx(
            systemTopInsetPx = maxOf(systemBarInsets.top, displayCutoutInsets.top),
            floorVisible = floorVisible,
            floorBottomPx = floorControlCard.bottom,
            controlSeparationPx = dp(OSRS_CONTROL_SEPARATION_DP),
            topMarginPx = dp(OSRS_SELECTOR_BOTTOM_GAP_DP)
        )
        val desiredLeft = osrsSymmetricControlSideMarginPx(
            horizontalMarginPx = dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP),
            systemBarSideInsetPx = systemBarInsets.left,
            displayCutoutSideInsetPx = displayCutoutInsets.left,
            systemGestureSideInsetPx = systemGestureInsets.left,
            gestureSafetyPx = dp(OSRS_CONTROL_GESTURE_SAFETY_DP)
        )
        if (
            params.gravity != (Gravity.TOP or Gravity.START) ||
            params.topMargin != desiredTop ||
            params.leftMargin != desiredLeft
        ) {
            params.gravity = Gravity.TOP or Gravity.START
            params.topMargin = desiredTop
            params.leftMargin = desiredLeft
            params.rightMargin = 0
            linksButton.layoutParams = params
        }
    }

    private fun updateCompassGeometry() {
        if (!::compassView.isInitialized) return
        (compassView.layoutParams as? FrameLayout.LayoutParams)?.apply {
            val topVisualMargin = osrsSafeDrawingEdgeMarginPx(
                systemBarInsets.top,
                displayCutoutInsets.top,
                dp(OSRS_SELECTOR_BOTTOM_GAP_DP)
            )
            gravity = Gravity.TOP or Gravity.END
            leftMargin = 0
            topMargin = topVisualMargin
            rightMargin = osrsSymmetricControlSideMarginPx(
                horizontalMarginPx = dp(OSRS_SELECTOR_HORIZONTAL_MARGIN_DP),
                systemBarSideInsetPx = systemBarInsets.right,
                displayCutoutSideInsetPx = displayCutoutInsets.right,
                systemGestureSideInsetPx = systemGestureInsets.right,
                gestureSafetyPx = dp(OSRS_CONTROL_GESTURE_SAFETY_DP)
            )
            bottomMargin = 0
            compassView.layoutParams = this
        }
    }

    private fun initializeMapLibre() {
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
                // MapLibre's fling has no finite-edge spring hook. Direct dragging remains native,
                // while release inertia and edge return are app-owned and frame-driven below.
                isFlingVelocityAnimationEnabled = false
            }
            // MapLibre Android 11.12.1 has no public renderWorldCopies switch. The finite
            // envelope is therefore enforced on every camera callback and at every write.
            updateCompassGeometry()
            updateCompassFromCamera()
            loadBaseStyle(mapLibreMap)
            mapLibreMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    cancelAppCameraTargetSettle()
                }
            }
            mapLibreMap.addOnCameraMoveListener {
                when {
                    cameraEdgePhysicsApplying -> Unit
                    cameraGestureTouchActive -> applyElasticCameraEnvelopeDuringGesture()
                    cameraEdgePhysicsPhase != "idle" -> Unit
                    else -> enforceActiveCameraEnvelope("camera-move")
                }
                scheduleCompassUpdateFromSettledCamera()
            }
            mapLibreMap.addOnCameraIdleListener {
                completeCameraIdle()
            }
            installCameraEdgeGestureObserver()
        }
    }

    private fun loadBaseStyle(mapLibreMap: MapLibreMap) {
        cameraPersistenceOwnership.clear()
        mapLibreMap.setStyle(Style.Builder().fromJson(OSRS_BASE_STYLE_JSON)) { loadedStyle ->
            styleGeneration += 1
            style = loadedStyle
            activeSourceId = null
            activeLayerId = null
            activeRasterResources = emptyList()
            activeRasterComposition = null
            lastRasterTransition = null
            activeCameraEnvelopeBinding = null
            activeCopySafeMinZoom = null
            lastCameraClampState = "inactive-style-reload"
            state = state?.let { reducer.reduce(it, osrsRealmAction.StyleReloaded) }
            switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
            switchCompletedAtNanos = null
            attemptInstallActiveAsset()
        }
    }

    private fun loadManifest() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                initializeCatalog(repository.loadCatalog())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                showFailure("Manifest rejected", failure)
            }
        }
    }

    private fun initializeCatalog(catalog: osrsRealmCatalog) {
        realmPresentations = osrsRealmPresentationCatalog(catalog.manifest.realms)
        realmLinkCatalogCache = osrsRealmLinkCatalogCache(catalog, realmPresentations!!)
        realmSelectorIndex = osrsRealmSelectorIndex(catalog.sections, realmPresentations!!)
        state = reducer.initial(
            catalog = catalog,
            persisted = stateStore.load(),
            restoredRealmId = savedRealmId,
            restoredPlane = savedPlane
        )
        check(
            catalog.selectorCount == catalog.manifest.realms.count { it.isCanonicalSelectorRealm }
        ) {
            "Selector does not exactly match the canonical manifest records"
        }
        val current = requireNotNull(state)
        val currentPresentation = realmPresentations!!.get(current.activeRealm)
        realmSelector = osrsRealmSelector(
            context = requireContext(),
            host = rootView,
            selectorIndex = requireNotNull(realmSelectorIndex),
            initialActiveRealmId = current.activeRealmId,
            initialVisibleName = currentPresentation.visibleName,
            initialAccessibilityName = currentPresentation.accessibilityName,
            onRealmSelected = ::selectRealm,
            onFilterMeasured = { query, resultCount, elapsed ->
                lastSelectorFilterNanos = elapsed
                recordSimpleControl(elapsed)
                Log.d(
                    OSRS_LOG_TAG,
                    "selector_filter queryLength=${query.length} results=$resultCount appNanos=$elapsed"
                )
                if (::selectorButton.isInitialized) renderDiagnostics()
            },
            onToggleMeasured = { expanded, elapsed ->
                lastSelectorToggleNanos = elapsed
                recordSimpleControl(elapsed)
                Log.d(
                    OSRS_LOG_TAG,
                    "selector_toggle expanded=$expanded appNanos=$elapsed"
                )
                if (::selectorButton.isInitialized) renderDiagnostics()
            },
            onOutsideDismissMeasured = { elapsed ->
                lastSelectorOutsideDismissNanos = elapsed
                selectorOutsideDismissCount += 1
                recordSimpleControl(elapsed)
                Log.d(
                    OSRS_LOG_TAG,
                    "selector_outside_dismiss count=$selectorOutsideDismissCount appNanos=$elapsed"
                )
                renderDiagnostics()
            },
            onExpandedChanged = {
                updateRealmSelectorGeometry()
                if (::selectorButton.isInitialized) renderDiagnostics()
            }
        ).also { selector ->
            selectorButton = selector.baseButton
            selector.restore(
                expanded = restoredSelectorExpanded,
                query = restoredSelectorQuery,
                searchFocused = restoredSelectorSearchFocused
            )
        }
        updateControls()
        stateStore.save(state!!)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        attemptInstallActiveAsset()
    }

    private fun showRealmSelector() {
        if (state == null || realmSelectorIndex == null) return
        val started = SystemClock.elapsedRealtimeNanos()
        realmSelector?.expand()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun selectRealm(realm: osrsRealmRecord) {
        val current = state ?: return
        if (realm.id == current.activeRealmId) return
        val started = SystemClock.elapsedRealtimeNanos()
        persistCurrentCamera()
        state = reducer.reduce(state!!, osrsRealmAction.SelectRealm(realm.id))
        stateStore.save(state!!)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        lastError = null
        updateControls()
        attemptInstallActiveAsset()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun selectPlane(plane: Int) {
        val current = state ?: return
        if (plane == current.activePlane || plane !in current.activeRealm.planes) return
        val started = SystemClock.elapsedRealtimeNanos()
        persistCurrentCamera()
        state = reducer.reduce(state!!, osrsRealmAction.SelectPlane(plane))
        stateStore.save(state!!)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        lastError = null
        updateControls()
        attemptInstallActiveAsset()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun showRealmLinks() {
        val current = state ?: return
        val catalogCache = realmLinkCatalogCache ?: return
        val started = SystemClock.elapsedRealtimeNanos()
        val catalogStarted = SystemClock.elapsedRealtimeNanos()
        val linkCatalogLookup = catalogCache.get(current.activeRealm)
        val catalogNanos = SystemClock.elapsedRealtimeNanos() - catalogStarted
        val linkCatalog = linkCatalogLookup.catalog

        val priorDialogCleanupStarted = SystemClock.elapsedRealtimeNanos()
        val dialogReused = linksDialog?.realmId == current.activeRealmId
        if (!dialogReused) {
            linksDialog?.dismiss()
            linksDialog = null
        }
        val priorDialogCleanupNanos =
            SystemClock.elapsedRealtimeNanos() - priorDialogCleanupStarted

        val controller = linksDialog ?: osrsRealmLinksDialog(
            context = requireContext(),
            links = linkCatalog,
            onLinkSelected = { row -> selectAuthoritativeLink(row) },
            onFilterMeasured = { query, resultCount, elapsed ->
                lastLinkFilterNanos = elapsed
                lastLinkFilterResultCount = resultCount
                recordSimpleControl(elapsed)
                Log.d(
                    OSRS_LOG_TAG,
                    "link_filter queryLength=${query.length} results=$resultCount appNanos=$elapsed"
                )
                renderDiagnostics()
            }
        ).also { linksDialog = it }
        val showResult = controller.show()
        val appNanos = SystemClock.elapsedRealtimeNanos() - started
        val classifiedNanos = catalogNanos +
            priorDialogCleanupNanos +
            showResult.viewConstructionNanos +
            showResult.initialFilterNanos +
            showResult.initialRowConversionNanos +
            showResult.initialAdapterSubmissionNanos +
            showResult.materialShowNanos
        val unclassifiedNanos = appNanos - classifiedNanos
        check(unclassifiedNanos >= 0L) { "Link dialog phase accounting exceeded total time" }
        linkDialogOpenOrdinal += 1
        lastLinkDialogPhase = osrsLinkDialogOpenPhase(
            ordinal = linkDialogOpenOrdinal,
            cold = linkDialogOpenOrdinal == 1,
            realmId = current.activeRealmId,
            availableCount = linkCatalog.availableRows.size,
            unavailableCount = linkCatalog.unavailableCount,
            catalogCacheHit = linkCatalogLookup.cacheHit,
            catalogNanos = catalogNanos,
            catalogBuildNanos = if (linkCatalogLookup.cacheHit) 0L else catalogNanos,
            priorDialogCleanupNanos = priorDialogCleanupNanos,
            dialogReused = dialogReused,
            viewConstructionNanos = showResult.viewConstructionNanos,
            initialFilterNanos = showResult.initialFilterNanos,
            initialRowConversionNanos = showResult.initialRowConversionNanos,
            initialAdapterSubmissionNanos = showResult.initialAdapterSubmissionNanos,
            initialUpdateStrategy = showResult.initialUpdateStrategy,
            initialFilterObserverNanos = 0L,
            materialShowNanos = showResult.materialShowNanos,
            unclassifiedNanos = unclassifiedNanos,
            appNanos = appNanos,
            showingAfterReturn = showResult.showingAfterReturn
        )
        lastLinkDialogOpenNanos = appNanos
        val phase = requireNotNull(lastLinkDialogPhase)
        if (phase.cold) {
            coldLinkDialogOpenNanos = appNanos
        } else {
            repeatedLinkDialogDurationsNanos.addLast(appNanos)
            while (repeatedLinkDialogDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) {
                repeatedLinkDialogDurationsNanos.removeFirst()
            }
            recordSimpleControl(appNanos)
        }
        Log.d(
            OSRS_LOG_TAG,
            "link_dialog_phase candidate=${current.catalog.manifest.candidate} " +
                "ordinal=${phase.ordinal} cold=${phase.cold} realmId=${phase.realmId} " +
                "available=${phase.availableCount} unavailable=${phase.unavailableCount} " +
                "catalogCacheHit=${phase.catalogCacheHit} catalogNanos=${phase.catalogNanos} " +
                "catalogBuildNanos=${phase.catalogBuildNanos} " +
                "priorDialogCleanupNanos=${phase.priorDialogCleanupNanos} " +
                "dialogReused=${phase.dialogReused} " +
                "viewConstructionNanos=${phase.viewConstructionNanos} " +
                "initialFilterNanos=${phase.initialFilterNanos} " +
                "initialRowConversionNanos=${phase.initialRowConversionNanos} " +
                "initialAdapterSubmissionNanos=${phase.initialAdapterSubmissionNanos} " +
                "initialUpdateStrategy=${phase.initialUpdateStrategy} " +
                "initialFilterObserverNanos=${phase.initialFilterObserverNanos} " +
                "materialShowNanos=${phase.materialShowNanos} " +
                "unclassifiedNanos=${phase.unclassifiedNanos} appNanos=${phase.appNanos} " +
                "showingAfterReturn=${phase.showingAfterReturn}"
        )
        Log.d(
            OSRS_LOG_TAG,
            "link_dialog_open available=${linkCatalog.availableRows.size} " +
                "unavailable=${linkCatalog.unavailableCount} appNanos=$appNanos"
        )
        renderDiagnostics()
    }

    private fun selectAuthoritativeLink(
        row: osrsRealmLinkRow,
        maximumViewportExtentDpOverride: Double? = null
    ) {
        val current = state ?: return
        val link = row.link
        if (!link.authoritative || link.availability != "available") return
        val sourceAsset = current.activeAsset
        val sourceCamera = currentActiveCameraState() ?: return
        val targetId = row.targetRealm.id
        val targetPlane = row.targetPosition.plane
        if (targetPlane !in row.targetRealm.planes) return
        val targetAsset = row.targetRealm.assetForPlane(targetPlane) ?: return
        val zoomPolicy = osrsRelativeLinkZoomForAssets(
            currentZoom = sourceCamera.zoom,
            sourceAsset = sourceAsset,
            targetAsset = targetAsset
        )
        val started = SystemClock.elapsedRealtimeNanos()
        @Suppress("UNUSED_VARIABLE")
        val unusedLegacyViewportOverride = maximumViewportExtentDpOverride
        persistCurrentCamera()
        var next = reducer.reduce(state!!, osrsRealmAction.SelectRealm(targetId))
        next = reducer.reduce(next, osrsRealmAction.SelectPlane(targetPlane))
        val destinationCamera = osrsCameraState(
            latitude = row.destination.latitude,
            longitude = row.destination.longitude,
            zoom = zoomPolicy.finalTargetZoom,
            bearing = sourceCamera.bearing,
            tilt = sourceCamera.tilt
        )
        next = reducer.reduce(next, osrsRealmAction.CameraChanged(destinationCamera))
        state = next
        lastLinkNavigation = osrsLinkNavigationState(
            linkId = link.id,
            linkSideKey = row.side.key,
            traversalDirection = row.side.traversalDirection,
            targetRealmId = targetId,
            targetPlane = targetPlane,
            targetGameX = row.targetPosition.x,
            targetGameY = row.targetPosition.y,
            mappedLatitude = row.destination.latitude,
            mappedLongitude = row.destination.longitude,
            mappedZoom = zoomPolicy.finalTargetZoom,
            sourceZoom = zoomPolicy.sourceZoom,
            sourceNativeMaxZoom = zoomPolicy.sourceNativeMaxZoom,
            targetNativeMaxZoom = zoomPolicy.targetNativeMaxZoom,
            relativeZoom = zoomPolicy.relativeZoom,
            requestedZoom = zoomPolicy.requestedTargetZoom,
            finalZoom = zoomPolicy.finalTargetZoom,
            clampState = zoomPolicy.clampState,
            matchingLayoutCount = row.destination.matchingLayoutCount,
            appliedMarker = "requested@${SystemClock.elapsedRealtimeNanos()}"
        )
        stateStore.save(next)
        switchRequestedAtNanos = SystemClock.elapsedRealtimeNanos()
        switchCompletedAtNanos = null
        lastError = null
        updateControls()
        if (next.switchRequestId == current.switchRequestId) {
            applyCamera(destinationCamera)
            markLinkCameraApplied(targetId, targetPlane, "current-style")
            switchCompletedAtNanos = SystemClock.elapsedRealtimeNanos()
            recordSwitchDuration(switchCompletedAtNanos!! - switchRequestedAtNanos!!)
            lastRenderMarker = "link-camera-applied-awaiting-render"
            renderDiagnostics()
        } else {
            attemptInstallActiveAsset()
        }
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - started)
    }

    private fun currentActiveCameraState(): osrsCameraState? {
        val camera = map?.cameraPosition ?: return null
        val target = camera.target ?: return null
        return clampCameraForActiveEnvelope(
            osrsCameraState(
                latitude = target.latitude,
                longitude = target.longitude,
                zoom = camera.zoom,
                bearing = camera.bearing,
                tilt = camera.tilt
            ),
            "link-source-camera"
        )
    }

    private fun attemptInstallActiveAsset() {
        val current = state ?: return
        val loadedStyle = style ?: return
        if (installedRequestId == current.switchRequestId && installedStyleGeneration == styleGeneration) return
        val requestId = current.switchRequestId
        val generation = styleGeneration
        val realm = current.activeRealm
        val composition = osrsRasterCompositionFor(realm, current.activePlane)
        val reusableStagedAssets = if (activeRasterRealmId == realm.id) {
            activeStagedAssets
        } else {
            emptyMap()
        }
        progress.visibility = View.VISIBLE
        if (::selectorButton.isInitialized) selectorButton.isEnabled = true
        switchJob?.cancel()
        switchJob = lifecycleScope.launch {
            try {
                // Every canonical file needed by the next visible composition is ready before the
                // style is touched. Plane 0 remains reusable across same-realm floor switches.
                val stagedAssets = composition.layersBottomToTop.associate { layer ->
                    val reusable = reusableStagedAssets[layer.plane]?.takeIf {
                        it.sha256 == layer.asset.mbtilesSha256
                    }
                    layer.plane to (reusable ?: repository.stage(layer.asset))
                }
                val latest = state
                if (latest?.switchRequestId != requestId || styleGeneration != generation || style !== loadedStyle) {
                    return@launch
                }
                installRasterComposition(
                    loadedStyle = loadedStyle,
                    realm = realm,
                    composition = composition,
                    stagedAssets = stagedAssets,
                    requestId = requestId,
                    generation = generation
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                val latest = state
                if (latest?.switchRequestId == requestId && styleGeneration == generation) {
                    showFailure("Could not open ${realm.canonicalName}", failure)
                }
            }
        }
    }

    private fun installRasterComposition(
        loadedStyle: Style,
        realm: osrsRealmRecord,
        composition: osrsRasterComposition,
        stagedAssets: Map<Int, osrsStagedRealmAsset>,
        requestId: Long,
        generation: Int
    ) {
        val appStarted = switchRequestedAtNanos ?: SystemClock.elapsedRealtimeNanos()
        val desiredResources = composition.layersBottomToTop.map { layer ->
            osrsRasterResourceIdentity(generation, realm.id, layer)
        }
        val transition = osrsRasterCompositionTransition(
            previousBottomToTop = activeRasterResources,
            desiredBottomToTop = desiredResources
        )
        check(transition.replacementPreparedBeforeRemoval) {
            "Raster transition would remove a desired source or layer"
        }
        val assetsByPlane = composition.layersBottomToTop.associateBy { it.plane }
        val addedSources = mutableListOf<String>()
        val addedLayers = mutableListOf<String>()
        val priorOpacities = activeRasterResources.associate { it.layerId to it.opacity }

        try {
            // Prepare every source first, then every layer bottom-to-top. No old resource is
            // removed until the full replacement composition exists and has its final opacity.
            desiredResources.forEach { resource ->
                if (loadedStyle.getSource(resource.sourceId) == null) {
                    val layer = assetsByPlane.getValue(resource.plane)
                    val staged = stagedAssets.getValue(resource.plane)
                    loadedStyle.addSource(
                        RasterSource(
                            resource.sourceId,
                            "mbtiles://${staged.file.absolutePath}",
                            layer.asset.tileSize
                        )
                    )
                    addedSources += resource.sourceId
                }
            }
            desiredResources.forEach { resource ->
                if (loadedStyle.getLayer(resource.layerId) == null) {
                    loadedStyle.addLayer(
                        RasterLayer(resource.layerId, resource.sourceId).withProperties(
                            PropertyFactory.visibility(Property.VISIBLE),
                            PropertyFactory.rasterOpacity(resource.opacity),
                            PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                            PropertyFactory.rasterFadeDuration(0f)
                        )
                    )
                    addedLayers += resource.layerId
                }
            }
            desiredResources.forEach { resource ->
                check(loadedStyle.getSource(resource.sourceId) != null) {
                    "Prepared raster source disappeared: ${resource.sourceId}"
                }
                val layer = requireNotNull(loadedStyle.getLayer(resource.layerId)) {
                    "Prepared raster layer disappeared: ${resource.layerId}"
                }
                layer.setProperties(
                    PropertyFactory.visibility(Property.VISIBLE),
                    PropertyFactory.rasterOpacity(resource.opacity),
                    PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                    PropertyFactory.rasterFadeDuration(0f)
                )
            }

            transition.obsoleteLayersTopToBottom.forEach { obsolete ->
                if (loadedStyle.getLayer(obsolete.layerId) != null) {
                    loadedStyle.removeLayer(obsolete.layerId)
                }
            }
            transition.obsoleteSources.forEach { obsolete ->
                if (loadedStyle.getSource(obsolete.sourceId) != null) {
                    loadedStyle.removeSource(obsolete.sourceId)
                }
            }
        } catch (failure: Throwable) {
            addedLayers.asReversed().forEach { layerId ->
                runCatching {
                    if (loadedStyle.getLayer(layerId) != null) loadedStyle.removeLayer(layerId)
                }
            }
            addedSources.asReversed().forEach { sourceId ->
                runCatching {
                    if (loadedStyle.getSource(sourceId) != null) loadedStyle.removeSource(sourceId)
                }
            }
            priorOpacities.forEach { (layerId, opacity) ->
                loadedStyle.getLayer(layerId)?.setProperties(
                    PropertyFactory.rasterOpacity(opacity)
                )
            }
            throw failure
        }

        val selectedResource = desiredResources.single { it.selected }
        val selectedLayer = composition.layersBottomToTop.single { it.selected }
        val selectedStaged = stagedAssets.getValue(composition.selectedPlane)
        activeLayerId = selectedResource.layerId
        activeSourceId = selectedResource.sourceId
        activeRasterRealmId = realm.id
        activeRasterResources = desiredResources
        activeStagedAssets = stagedAssets
        activeRasterComposition = composition
        lastRasterTransition = transition

        installedRequestId = requestId
        installedStyleGeneration = generation
        stagedAssetSha256 = selectedStaged.sha256
        stagedAssetPath = selectedLayer.asset.mbtilesPath
        lastStageNanos = selectedStaged.elapsedNanos
        configureRealmCamera(
            realm,
            selectedLayer.asset,
            composition.layersBottomToTop.map { it.asset },
            osrsInstalledCameraIdentity(
                realmId = realm.id,
                plane = composition.selectedPlane,
                requestId = requestId,
                styleGeneration = generation
            )
        )
        switchCompletedAtNanos = SystemClock.elapsedRealtimeNanos()
        val duration = switchCompletedAtNanos!! - appStarted
        recordSwitchDuration(duration)
        progress.visibility = View.GONE
        lastError = null
        lastRenderMarker = "installed-awaiting-render"
        map?.triggerRepaint()
        updateControls()
        Log.i(
            OSRS_LOG_TAG,
            "realm_switch id=${realm.id} plane=${composition.selectedPlane} " +
                "visiblePlanes=${desiredResources.joinToString(",") { it.plane.toString() }} " +
                "selectedSource=${selectedResource.sourceId} appNanos=$duration " +
                "stageNanos=${selectedStaged.elapsedNanos} " +
                "reused=${selectedStaged.reusedVerifiedCopy || transition.reused.any { it.selected }} " +
                "sha256=${selectedStaged.sha256}"
        )
    }

    private fun configureRealmCamera(
        realm: osrsRealmRecord,
        asset: osrsRealmAsset,
        visibleAssets: List<osrsRealmAsset>,
        identity: osrsInstalledCameraIdentity
    ) {
        val mapLibreMap = map ?: return
        val envelope = osrsCameraCenterEnvelope.fromVisibleAssets(visibleAssets)
        val minimumZoom = effectiveMinimumZoom(asset, envelope)
        // Every regenerated realm has four-sided transparent canvas padding. Keep native target
        // bounds disabled so each content edge can reach the drawable center; the app-owned
        // callback clamp remains the single finite-envelope authority on both platforms.
        mapLibreMap.setLatLngBoundsForCameraTarget(null)
        mapLibreMap.setMinZoomPreference(minimumZoom)
        mapLibreMap.setMaxZoomPreference(osrsRealmCameraEnvelope.maxZoom(asset))
        activeCopySafeMinZoom = minimumZoom
        val remembered = state?.cameras?.get(realm.id)
            ?.takeIf {
                it.isFinite() &&
                    it.zoom in minimumZoom..osrsRealmCameraEnvelope.maxZoom(asset)
            }
            ?.let { osrsClampCameraToEnvelope(it, envelope).final }
        if (remembered != null) {
            val latest = state ?: return
            if (!cameraPersistenceOwnership.markInstalled(identity, latest, styleGeneration)) return
            bindActiveCameraEnvelope(identity, envelope)
            applyCamera(remembered)
            markLinkCameraApplied(realm.id, asset.plane, "installed-style")
        } else {
            mapView.post {
                val latest = state ?: return@post
                if (
                    installedRequestId == identity.requestId &&
                    installedStyleGeneration == identity.styleGeneration &&
                    cameraPersistenceOwnership.markInstalled(
                        identity,
                        latest,
                        styleGeneration
                    )
                ) {
                    bindActiveCameraEnvelope(identity, envelope)
                    if (
                        realm.isSurface &&
                        asset.plane == osrsUndergroundMapDefaultView.PLANE
                    ) {
                        applyCamera(defaultSurfaceCamera(realm, asset, envelope))
                    } else {
                        applyCamera(
                            osrsCameraState(
                                latitude = (envelope.south + envelope.north) / 2.0,
                                longitude = (envelope.west + envelope.east) / 2.0,
                                zoom = osrsDefaultZoomForAsset(asset).coerceIn(
                                    minimumZoom,
                                    osrsRealmCameraEnvelope.maxZoom(asset)
                                )
                            )
                        )
                    }
                    markLinkCameraApplied(realm.id, asset.plane, "installed-style")
                }
            }
        }
    }

    private fun defaultSurfaceCamera(
        realm: osrsRealmRecord,
        asset: osrsRealmAsset,
        envelope: osrsCameraCenterEnvelope
    ): osrsCameraState {
        val projection = state?.catalog?.manifest?.rasterProjectionOrNull()
        val mapped = projection?.let { rasterProjection ->
            osrsRealmEndpointMapper(rasterProjection).map(
                realm,
                osrsRealmLinkPosition(
                    plane = osrsUndergroundMapDefaultView.PLANE,
                    x = osrsUndergroundMapDefaultView.GAME_X.toInt(),
                    y = osrsUndergroundMapDefaultView.GAME_Y.toInt()
                )
            )
        }
        if (mapped != null) {
            val scaleCompatibleZoom = osrsSurfaceDefaultZoomForAsset(asset)
            return osrsCameraState(
                latitude = mapped.latitude,
                longitude = mapped.longitude,
                zoom = scaleCompatibleZoom.coerceIn(
                    effectiveMinimumZoom(asset, envelope),
                    osrsRealmCameraEnvelope.maxZoom(asset)
                )
            )
        }
        Log.w(OSRS_LOG_TAG, "surface_default_mapping_failed; fitting finite envelope")
        return osrsCameraState(
            latitude = (envelope.south + envelope.north) / 2.0,
            longitude = (envelope.west + envelope.east) / 2.0,
            zoom = effectiveMinimumZoom(asset, envelope)
        )
    }

    private fun applyCamera(camera: osrsCameraState) {
        // A direct app-owned camera write begins a new camera operation. Gesture locks are
        // deliberately retained across early MapLibre idle echoes, then retired here.
        cancelCameraEdgePhysics("app-camera")
        map?.uiSettings?.isScrollGesturesEnabled = true
        val finalCamera = clampCameraForActiveEnvelope(camera, "apply-camera")
        map?.cancelAllVelocityAnimations()
        map?.cancelTransitions()
        setMapCamera(finalCamera)
        scheduleAppCameraTargetSettle(finalCamera)
        scheduleCompassUpdateFromSettledCamera()
    }

    private fun bindActiveCameraEnvelope(
        identity: osrsInstalledCameraIdentity,
        envelope: osrsCameraCenterEnvelope
    ) {
        activeCameraEnvelopeBinding = osrsActiveCameraEnvelopeBinding(identity, envelope)
        cancelAppCameraTargetSettle()
        cancelCameraEdgePhysics("envelope-rebind")
        lastRequestedCameraTarget = null
        lastFinalCameraTarget = null
        lastObservedMapLibreLongitude = null
        map?.uiSettings?.isScrollGesturesEnabled = true
        lastCameraClampState = "active:${identity.realmId}:${identity.plane}"
    }

    private fun effectiveMinimumZoom(
        asset: osrsRealmAsset,
        envelope: osrsCameraCenterEnvelope
    ): Double {
        val baseMinimum = osrsRealmCameraEnvelope.minZoom(asset)
        val viewportWidthPx = rootView.width.takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val viewportHeightPx = rootView.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        return osrsFiniteRealmMinimumZoom(
            baseMinimumZoom = baseMinimum,
            envelope = envelope,
            viewportWidth = viewportWidthPx.toDouble(),
            viewportHeight = viewportHeightPx.toDouble()
        )
    }

    private fun updateActiveCopySafeMinZoom() {
        val current = state ?: return
        val composition = activeRasterComposition ?: return
        val envelope = activeCameraEnvelope() ?: return
        val selectedAsset = composition.layersBottomToTop.single { it.selected }.asset
        val minimumZoom = effectiveMinimumZoom(selectedAsset, envelope)
        activeCopySafeMinZoom = minimumZoom
        map?.setMinZoomPreference(minimumZoom)
    }

    private fun activeCameraEnvelope(): osrsCameraCenterEnvelope? {
        val current = state ?: return null
        val installed = cameraPersistenceOwnership.authorization(current, styleGeneration)
            ?: return null
        return activeCameraEnvelopeBinding
            ?.takeIf { it.identity == installed }
            ?.envelope
    }

    private fun clampCameraForActiveEnvelope(
        camera: osrsCameraState,
        phase: String
    ): osrsCameraState {
        val envelope = activeCameraEnvelope() ?: return camera
        val started = SystemClock.elapsedRealtimeNanos()
        val result = osrsClampCameraToEnvelope(camera, envelope)
        if (result.clamped) {
            lastRequestedCameraTarget = osrsCameraTarget(
                latitude = result.requested.latitude,
                longitude = result.requested.longitude
            )
            lastFinalCameraTarget = osrsCameraTarget(
                latitude = result.final.latitude,
                longitude = result.final.longitude
            )
            lastCameraClampState = "clamped:$phase"
        }
        if (result.clamped) cameraClampCount += 1
        val elapsed = SystemClock.elapsedRealtimeNanos() - started
        lastCameraClampNanos = elapsed
        cameraClampDurationsNanos.addLast(elapsed)
        while (cameraClampDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) {
            cameraClampDurationsNanos.removeFirst()
        }
        return result.final
    }

    /** Observes the native pan without consuming it; MapLibre still owns direct manipulation. */
    private fun installCameraEdgeGestureObserver() {
        fun beginObservedCameraGesture() {
            if (cameraGestureTouchActive) return
            cancelAppCameraTargetSettle()
            cancelCameraEdgePhysics("gesture-began")
            cameraGestureTouchActive = true
            cameraGesturePanDetected = true
            cameraEdgePhysicsPhase = "gesture"
        }

        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cameraGestureTouchActive = false
                    cameraGesturePanDetected = false
                    cameraGestureHadMultiplePointers = false
                    cameraGestureDownX = event.x
                    cameraGestureDownY = event.y
                    cameraPinchLastSpan = 0.0
                    cameraPinchLastEventMillis = 0L
                    cameraPinchReleaseVelocityLevelsPerSecond = 0.0
                    cameraPinchFocalPoint = null
                    cameraPinchFocalCoordinate = null
                    cameraZoomMomentumFocalDriftPx = null
                    cameraVelocityTracker?.recycle()
                    cameraVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    cameraGestureHadMultiplePointers = true
                    cameraPinchLastSpan = pointerSpan(event)
                    cameraPinchLastEventMillis = event.eventTime
                    cameraPinchReleaseVelocityLevelsPerSecond = 0.0
                    cameraPinchFocalPoint = pointerFocalPoint(event)
                    cameraVelocityTracker?.addMovement(event)
                    beginObservedCameraGesture()
                }

                MotionEvent.ACTION_MOVE -> {
                    cameraVelocityTracker?.addMovement(event)
                    if (event.pointerCount > 1) {
                        cameraGestureHadMultiplePointers = true
                        recordPinchZoomVelocity(event)
                        cameraPinchFocalPoint = pointerFocalPoint(event)
                        beginObservedCameraGesture()
                    } else if (!cameraGesturePanDetected && hypot(
                            (event.x - cameraGestureDownX).toDouble(),
                            (event.y - cameraGestureDownY).toDouble()
                        ) > cameraGestureTouchSlop
                    ) {
                        beginObservedCameraGesture()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val wasActive = cameraGestureTouchActive
                    val velocityX: Double
                    val velocityY: Double
                    if (wasActive) {
                        cameraVelocityTracker?.addMovement(event)
                        cameraVelocityTracker?.computeCurrentVelocity(
                            1000,
                            resources.displayMetrics.density * OSRS_EDGE_MAXIMUM_RELEASE_VELOCITY_DP_PER_SECOND
                        )
                        val pointerId = event.getPointerId(0)
                        velocityX = cameraVelocityTracker?.getXVelocity(pointerId)?.toDouble() ?: 0.0
                        velocityY = cameraVelocityTracker?.getYVelocity(pointerId)?.toDouble() ?: 0.0
                    } else {
                        velocityX = 0.0
                        velocityY = 0.0
                    }
                    val allowInertia = wasActive && !cameraGestureHadMultiplePointers
                    val wasPinch = cameraGestureHadMultiplePointers
                    val zoomVelocity = cameraPinchReleaseVelocityLevelsPerSecond
                    cameraVelocityTracker?.recycle()
                    cameraVelocityTracker = null
                    cameraGestureTouchActive = false
                    cameraGesturePanDetected = false
                    cameraGestureHadMultiplePointers = false
                    if (wasActive) {
                        mapView.postOnAnimation {
                            if (wasPinch) {
                                beginCameraZoomRelease(zoomVelocity)
                            } else {
                                beginCameraEdgeRelease(velocityX, velocityY, allowInertia)
                            }
                        }
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    val wasActive = cameraGestureTouchActive
                    cameraVelocityTracker?.recycle()
                    cameraVelocityTracker = null
                    cameraGestureTouchActive = false
                    cameraGesturePanDetected = false
                    cameraGestureHadMultiplePointers = false
                    cameraPinchLastSpan = 0.0
                    cameraPinchLastEventMillis = 0L
                    cameraPinchReleaseVelocityLevelsPerSecond = 0.0
                    if (wasActive) {
                        mapView.postOnAnimation {
                            beginCameraEdgeRelease(0.0, 0.0, allowInertia = false)
                        }
                    }
                }
            }
            false
        }
    }

    private fun pointerSpan(event: MotionEvent): Double {
        if (event.pointerCount < 2) return 0.0
        return hypot(
            (event.getX(1) - event.getX(0)).toDouble(),
            (event.getY(1) - event.getY(0)).toDouble()
        )
    }

    private fun pointerFocalPoint(event: MotionEvent): Point? {
        if (event.pointerCount < 2) return null
        return Point(
            ((event.getX(0) + event.getX(1)) / 2f).toInt(),
            ((event.getY(0) + event.getY(1)) / 2f).toInt()
        )
    }

    private fun recordPinchZoomVelocity(event: MotionEvent) {
        val currentSpan = pointerSpan(event)
        val previousSpan = cameraPinchLastSpan
        val elapsedMillis = event.eventTime - cameraPinchLastEventMillis
        if (currentSpan > 0.0 && previousSpan > 0.0 && elapsedMillis in 1..100) {
            val instantaneous = osrsPinchZoomVelocityLevelsPerSecond(
                previousSpan = previousSpan,
                currentSpan = currentSpan,
                elapsedSeconds = elapsedMillis / 1_000.0
            )
            // MapLibre/UIAutomator may emit a few stationary pointer samples immediately before
            // lifting. Ignore that event-tail noise and retain a smoothed recent gesture velocity,
            // matching the short history window used by native scale recognizers.
            if (kotlin.math.abs(instantaneous) >= 0.005) {
                cameraPinchReleaseVelocityLevelsPerSecond =
                    0.15 * instantaneous + 0.85 * cameraPinchReleaseVelocityLevelsPerSecond
            }
        }
        cameraPinchLastSpan = currentSpan
        cameraPinchLastEventMillis = event.eventTime
    }

    private fun currentResolvedCameraState(): osrsCameraState? {
        val position = map?.cameraPosition ?: return null
        val target = position.target ?: return null
        val longitude = lastObservedMapLibreLongitude?.let { previous ->
            osrsResolveMapLibreLongitudeRepresentation(previous, target.longitude)
        } ?: target.longitude
        return osrsCameraState(
            latitude = target.latitude,
            longitude = longitude,
            zoom = position.zoom,
            bearing = position.bearing,
            tilt = position.tilt
        )
    }

    private fun applyElasticCameraEnvelopeDuringGesture(): Boolean {
        val envelope = activeCameraEnvelope() ?: return false
        val requested = currentResolvedCameraState() ?: return false
        val elastic = requested.copy(
            latitude = osrsElasticAxisPosition(
                requested.latitude,
                envelope.south,
                envelope.north
            ),
            longitude = osrsElasticAxisPosition(
                requested.longitude,
                envelope.west,
                envelope.east
            )
        )
        recordCameraEdgeOvershoot(elastic, envelope)
        lastRequestedCameraTarget = osrsCameraTarget(requested.latitude, requested.longitude)
        lastFinalCameraTarget = osrsCameraTarget(elastic.latitude, elastic.longitude)
        lastCameraClampState = if (
            requested.latitude in envelope.south..envelope.north &&
            requested.longitude in envelope.west..envelope.east
        ) {
            "gesture:inside"
        } else {
            "gesture:elastic"
        }
        lastObservedMapLibreLongitude = elastic.longitude
        if (osrsCameraTargetsEquivalent(elastic, requested)) return false
        return setCameraFromEdgePhysics(elastic)
    }

    private fun beginCameraEdgeRelease(
        velocityXPixelsPerSecond: Double,
        velocityYPixelsPerSecond: Double,
        allowInertia: Boolean
    ) {
        val envelope = activeCameraEnvelope() ?: run {
            cancelCameraEdgePhysics("release-no-envelope")
            return
        }
        val current = currentResolvedCameraState() ?: run {
            cancelCameraEdgePhysics("release-no-camera")
            return
        }
        map?.cancelAllVelocityAnimations()
        map?.cancelTransitions()
        cameraEdgePhysicsStartedNanos = SystemClock.elapsedRealtimeNanos()
        cameraEdgePhysicsFrameCount = 0
        cameraEdgePeakLatitudeOvershoot = 0.0
        cameraEdgePeakLongitudeOvershoot = 0.0
        recordCameraEdgeOvershoot(current, envelope)
        val strict = osrsClampCameraToEnvelope(current, envelope).final
        if (!osrsCameraTargetsEquivalent(current, strict)) {
            val (latitudeVelocity, longitudeVelocity) = cameraCoordinateVelocityFromPixels(
                current,
                velocityXPixelsPerSecond,
                velocityYPixelsPerSecond
            )
            startCameraEdgeSpring(
                current = current,
                target = strict,
                latitudeVelocity = latitudeVelocity,
                longitudeVelocity = longitudeVelocity
            )
            return
        }
        val speed = osrsCameraReleaseSpeed(
            velocityXPixelsPerSecond,
            velocityYPixelsPerSecond
        )
        if (allowInertia && speed >= dp(OSRS_EDGE_MINIMUM_RELEASE_SPEED_DP_PER_SECOND).toDouble()) {
            cameraEdgeVelocityXPxPerSecond = velocityXPixelsPerSecond
            cameraEdgeVelocityYPxPerSecond = velocityYPixelsPerSecond
            cameraEdgePhysicsPhase = "inertia"
            lastCameraClampState = "edge-physics:inertia"
            scheduleCameraEdgePhysicsFrame()
        } else {
            finishCameraEdgePhysics(strict, "release-settled")
        }
    }

    private fun beginCameraZoomRelease(velocityLevelsPerSecond: Double) {
        val current = currentResolvedCameraState() ?: run {
            cancelCameraEdgePhysics("zoom-release-no-camera")
            return
        }
        cameraPinchFocalCoordinate = cameraPinchFocalPoint?.let { focalPoint ->
            map?.projection?.fromScreenLocation(
                PointF(focalPoint.x.toFloat(), focalPoint.y.toFloat())
            )?.let { coordinate ->
                osrsCameraTarget(
                    latitude = coordinate.latitude,
                    longitude = osrsResolveMapLibreLongitudeRepresentation(
                        current.longitude,
                        coordinate.longitude
                    )
                )
            }
        }
        cameraZoomMomentumFocalDriftPx = null
        map?.cancelAllVelocityAnimations()
        map?.cancelTransitions()
        val velocity = velocityLevelsPerSecond.coerceIn(
            -OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY,
            OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY
        )
        cameraEdgePhysicsStartedNanos = SystemClock.elapsedRealtimeNanos()
        cameraEdgePhysicsFrameCount = 0
        cameraZoomMomentumStartZoom = current.zoom
        cameraZoomMomentumPeakContinuation = 0.0
        if (kotlin.math.abs(velocity) < OSRS_ZOOM_MOMENTUM_MINIMUM_RELEASE_VELOCITY) {
            cancelCameraEdgePhysics("zoom-release-settled", retainLastResult = true)
            persistCurrentCamera()
            renderDiagnostics()
            return
        }
        cameraZoomVelocityLevelsPerSecond = velocity
        cameraEdgePhysicsPhase = "zoom-inertia"
        lastCameraClampState = "zoom-physics:inertia"
        scheduleCameraEdgePhysicsFrame()
    }

    private fun cameraCoordinateVelocityFromPixels(
        current: osrsCameraState,
        velocityXPixelsPerSecond: Double,
        velocityYPixelsPerSecond: Double
    ): Pair<Double, Double> {
        val projection = map?.projection ?: return 0.0 to 0.0
        val probeSeconds = 1.0 / 120.0
        val projected = projection.fromScreenLocation(
            PointF(
                mapView.width / 2f - (velocityXPixelsPerSecond * probeSeconds).toFloat(),
                mapView.height / 2f - (velocityYPixelsPerSecond * probeSeconds).toFloat()
            )
        )
        val resolvedLongitude = osrsResolveMapLibreLongitudeRepresentation(
            current.longitude,
            projected.longitude
        )
        return (projected.latitude - current.latitude) / probeSeconds to
            (resolvedLongitude - current.longitude) / probeSeconds
    }

    private fun startCameraEdgeSpring(
        current: osrsCameraState,
        target: osrsCameraState,
        latitudeVelocity: Double,
        longitudeVelocity: Double
    ) {
        cameraSpringLatitude = osrsDampedSpringAxisState(current.latitude, latitudeVelocity)
        cameraSpringLongitude = osrsDampedSpringAxisState(current.longitude, longitudeVelocity)
        cameraSpringTarget = osrsCameraTarget(target.latitude, target.longitude)
        cameraEdgePhysicsPhase = "spring"
        lastCameraClampState = "edge-physics:spring"
        scheduleCameraEdgePhysicsFrame()
    }

    private fun scheduleCameraEdgePhysicsFrame() {
        val choreographer = Choreographer.getInstance()
        val callback = cameraEdgePhysicsFrameCallback ?: Choreographer.FrameCallback { frameNanos ->
            stepCameraEdgePhysics(frameNanos)
        }.also { cameraEdgePhysicsFrameCallback = it }
        choreographer.removeFrameCallback(callback)
        choreographer.postFrameCallback(callback)
    }

    private fun stepCameraEdgePhysics(frameNanos: Long) {
        if (cameraEdgePhysicsPhase == "idle" || cameraGestureTouchActive) return
        val previous = cameraEdgePhysicsLastFrameNanos
        cameraEdgePhysicsLastFrameNanos = frameNanos
        if (previous == 0L) {
            scheduleCameraEdgePhysicsFrame()
            return
        }
        val elapsedSeconds = ((frameNanos - previous) / 1_000_000_000.0)
            .coerceIn(OSRS_EDGE_MINIMUM_FRAME_SECONDS, OSRS_EDGE_MAXIMUM_FRAME_SECONDS)
        cameraEdgePhysicsFrameCount += 1
        when (cameraEdgePhysicsPhase) {
            "inertia" -> stepCameraEdgeInertia(elapsedSeconds)
            "spring" -> stepCameraEdgeSpring(elapsedSeconds)
            "zoom-inertia" -> stepCameraZoomInertia(elapsedSeconds)
        }
        if (cameraEdgePhysicsPhase != "idle") scheduleCameraEdgePhysicsFrame()
    }

    private fun stepCameraEdgeInertia(elapsedSeconds: Double) {
        val envelope = activeCameraEnvelope() ?: run {
            cancelCameraEdgePhysics("inertia-no-envelope")
            return
        }
        val current = currentResolvedCameraState() ?: run {
            cancelCameraEdgePhysics("inertia-no-camera")
            return
        }
        val projection = map?.projection ?: return
        val projected = projection.fromScreenLocation(
            PointF(
                mapView.width / 2f -
                    (cameraEdgeVelocityXPxPerSecond * elapsedSeconds).toFloat(),
                mapView.height / 2f -
                    (cameraEdgeVelocityYPxPerSecond * elapsedSeconds).toFloat()
            )
        )
        val requested = current.copy(
            latitude = projected.latitude,
            longitude = osrsResolveMapLibreLongitudeRepresentation(
                current.longitude,
                projected.longitude
            )
        )
        val strict = osrsClampCameraToEnvelope(requested, envelope).final
        if (!osrsCameraTargetsEquivalent(requested, strict)) {
            val elastic = requested.copy(
                latitude = osrsElasticAxisPosition(
                    requested.latitude,
                    envelope.south,
                    envelope.north
                ),
                longitude = osrsElasticAxisPosition(
                    requested.longitude,
                    envelope.west,
                    envelope.east
                )
            )
            setCameraFromEdgePhysics(elastic)
            recordCameraEdgeOvershoot(elastic, envelope)
            startCameraEdgeSpring(
                current = elastic,
                target = strict,
                latitudeVelocity = (elastic.latitude - current.latitude) / elapsedSeconds,
                longitudeVelocity = (elastic.longitude - current.longitude) / elapsedSeconds
            )
            return
        }
        setCameraFromEdgePhysics(requested)
        val decay = exp(-OSRS_EDGE_INERTIA_DECELERATION_PER_SECOND * elapsedSeconds)
        cameraEdgeVelocityXPxPerSecond *= decay
        cameraEdgeVelocityYPxPerSecond *= decay
        lastCameraClampState = "edge-physics:inertia"
        if (
            osrsCameraReleaseSpeed(
                cameraEdgeVelocityXPxPerSecond,
                cameraEdgeVelocityYPxPerSecond
            ) < dp(OSRS_EDGE_INERTIA_STOP_SPEED_DP_PER_SECOND).toDouble()
        ) {
            finishCameraEdgePhysics(strict, "inertia-settled")
        }
    }

    private fun stepCameraEdgeSpring(elapsedSeconds: Double) {
        val envelope = activeCameraEnvelope() ?: run {
            cancelCameraEdgePhysics("spring-no-envelope")
            return
        }
        val current = currentResolvedCameraState() ?: run {
            cancelCameraEdgePhysics("spring-no-camera")
            return
        }
        val target = cameraSpringTarget ?: run {
            cancelCameraEdgePhysics("spring-no-target")
            return
        }
        val latitude = osrsStepDampedSpring(
            requireNotNull(cameraSpringLatitude),
            target.latitude,
            elapsedSeconds
        )
        val longitude = osrsStepDampedSpring(
            requireNotNull(cameraSpringLongitude),
            target.longitude,
            elapsedSeconds
        )
        cameraSpringLatitude = latitude
        cameraSpringLongitude = longitude
        val next = current.copy(latitude = latitude.position, longitude = longitude.position)
        setCameraFromEdgePhysics(next)
        recordCameraEdgeOvershoot(next, envelope)
        lastCameraClampState = "edge-physics:spring"
        val latitudeSettled = osrsDampedSpringIsSettled(
            latitude,
            target.latitude,
            envelope.north - envelope.south
        )
        val longitudeSettled = osrsDampedSpringIsSettled(
            longitude,
            target.longitude,
            envelope.east - envelope.west
        )
        if (latitudeSettled && longitudeSettled) {
            finishCameraEdgePhysics(
                current.copy(latitude = target.latitude, longitude = target.longitude),
                "spring-settled"
            )
        }
    }

    private fun stepCameraZoomInertia(elapsedSeconds: Double) {
        val mapLibreMap = map ?: run {
            cancelCameraEdgePhysics("zoom-inertia-no-map")
            return
        }
        val current = currentResolvedCameraState() ?: run {
            cancelCameraEdgePhysics("zoom-inertia-no-camera")
            return
        }
        val requestedZoom = current.zoom + cameraZoomVelocityLevelsPerSecond * elapsedSeconds
        val nextZoom = requestedZoom.coerceIn(
            mapLibreMap.minZoomLevel,
            mapLibreMap.maxZoomLevel
        )
        cameraEdgePhysicsApplying = true
        try {
            // CameraUpdateFactory.zoomBy(delta, point) makes the coordinate currently under
            // `point` the camera target. Repeating that update for inertia compounds an intended
            // zoom into a severe pan. Match the iOS path instead: apply the next zoom around the
            // existing center, then translate the center once so the release-time geographic
            // anchor remains under the same screen focal point.
            setMapCamera(current.copy(zoom = nextZoom))
            val focalPoint = cameraPinchFocalPoint
            val focalCoordinate = cameraPinchFocalCoordinate
            if (focalPoint != null && focalCoordinate != null) {
                val projection = mapLibreMap.projection
                val rendered = projection.toScreenLocation(
                    LatLng(focalCoordinate.latitude, focalCoordinate.longitude)
                )
                val adjusted = projection.fromScreenLocation(
                    PointF(
                        mapView.width / 2f + rendered.x - focalPoint.x,
                        mapView.height / 2f + rendered.y - focalPoint.y
                    )
                )
                val requested = current.copy(
                    latitude = adjusted.latitude,
                    longitude = osrsResolveMapLibreLongitudeRepresentation(
                        current.longitude,
                        adjusted.longitude
                    ),
                    zoom = nextZoom
                )
                val envelope = activeCameraEnvelope()
                val final = if (envelope != null) {
                    osrsClampCameraToEnvelope(requested, envelope).final
                } else {
                    requested
                }
                setMapCamera(final)
            }
        } finally {
            cameraEdgePhysicsApplying = false
        }
        val live = currentResolvedCameraState() ?: current.copy(zoom = nextZoom)
        val envelope = activeCameraEnvelope()
        val next = if (envelope != null) osrsClampCameraToEnvelope(live, envelope).final else live
        if (!osrsCameraStatesEquivalent(live, next)) setCameraFromEdgePhysics(next)
        recordCameraZoomMomentumFocalDrift()
        cameraZoomMomentumStartZoom?.let { start ->
            cameraZoomMomentumPeakContinuation = maxOf(
                cameraZoomMomentumPeakContinuation,
                kotlin.math.abs(nextZoom - start)
            )
        }
        cameraZoomVelocityLevelsPerSecond = osrsDecayZoomMomentumVelocity(
            cameraZoomVelocityLevelsPerSecond,
            elapsedSeconds
        )
        lastCameraClampState = "zoom-physics:inertia"
        val reachedLimit = kotlin.math.abs(nextZoom - requestedZoom) > 0.000_001
        if (reachedLimit ||
            kotlin.math.abs(cameraZoomVelocityLevelsPerSecond) < OSRS_ZOOM_MOMENTUM_STOP_VELOCITY
        ) {
            lastCameraZoomMomentumFrameCount = cameraEdgePhysicsFrameCount
            lastCameraZoomMomentumDurationNanos = cameraEdgePhysicsStartedNanos?.let {
                SystemClock.elapsedRealtimeNanos() - it
            }
            cameraPinchFocalPoint = null
            cancelCameraEdgePhysics("zoom-inertia-settled", retainLastResult = true)
            persistCurrentCamera()
            scheduleCompassUpdateFromSettledCamera()
            renderDiagnostics()
        }
    }

    private fun recordCameraZoomMomentumFocalDrift() {
        val focalPoint = cameraPinchFocalPoint ?: return
        val focalCoordinate = cameraPinchFocalCoordinate ?: return
        val rendered = map?.projection?.toScreenLocation(
            LatLng(focalCoordinate.latitude, focalCoordinate.longitude)
        ) ?: return
        cameraZoomMomentumFocalDriftPx = hypot(
            (rendered.x - focalPoint.x).toDouble(),
            (rendered.y - focalPoint.y).toDouble()
        )
    }

    private fun setCameraFromEdgePhysics(camera: osrsCameraState): Boolean {
        lastObservedMapLibreLongitude = camera.longitude
        cameraEdgePhysicsApplying = true
        return try {
            setMapCamera(camera)
        } finally {
            cameraEdgePhysicsApplying = false
        }
    }

    private fun recordCameraEdgeOvershoot(
        camera: osrsCameraState,
        envelope: osrsCameraCenterEnvelope
    ) {
        cameraEdgePeakLatitudeOvershoot = maxOf(
            cameraEdgePeakLatitudeOvershoot,
            envelope.south - camera.latitude,
            camera.latitude - envelope.north,
            0.0
        )
        cameraEdgePeakLongitudeOvershoot = maxOf(
            cameraEdgePeakLongitudeOvershoot,
            envelope.west - camera.longitude,
            camera.longitude - envelope.east,
            0.0
        )
    }

    private fun finishCameraEdgePhysics(camera: osrsCameraState, reason: String) {
        setCameraFromEdgePhysics(camera)
        lastObservedMapLibreLongitude = camera.longitude
        lastCameraClampState = "edge-physics:$reason"
        lastCameraEdgeBounceFrameCount = cameraEdgePhysicsFrameCount
        lastCameraEdgeBounceDurationNanos = cameraEdgePhysicsStartedNanos?.let {
            SystemClock.elapsedRealtimeNanos() - it
        }
        cancelCameraEdgePhysics(reason, retainLastResult = true)
        scheduleAppCameraTargetSettle(camera)
        persistCurrentCamera()
        scheduleCompassUpdateFromSettledCamera()
        renderDiagnostics()
    }

    private fun cancelCameraEdgePhysics(
        reason: String,
        retainLastResult: Boolean = false
    ) {
        cameraEdgePhysicsFrameCallback?.let {
            Choreographer.getInstance().removeFrameCallback(it)
        }
        cameraEdgePhysicsLastFrameNanos = 0L
        cameraEdgePhysicsPhase = "idle"
        cameraEdgeVelocityXPxPerSecond = 0.0
        cameraEdgeVelocityYPxPerSecond = 0.0
        cameraZoomVelocityLevelsPerSecond = 0.0
        cameraSpringLatitude = null
        cameraSpringLongitude = null
        cameraSpringTarget = null
        cameraEdgePhysicsApplying = false
        if (!retainLastResult) {
            cameraEdgePhysicsStartedNanos = null
            cameraEdgePhysicsFrameCount = 0
            cameraEdgePeakLatitudeOvershoot = 0.0
            cameraEdgePeakLongitudeOvershoot = 0.0
            cameraZoomMomentumStartZoom = null
            cameraZoomMomentumPeakContinuation = 0.0
            cameraPinchFocalPoint = null
            cameraPinchFocalCoordinate = null
            cameraZoomMomentumFocalDriftPx = null
            lastCameraClampState = "edge-physics-cancelled:$reason"
        }
    }

    private fun setMapCamera(camera: osrsCameraState): Boolean {
        val mapLibreMap = map ?: return false
        lastObservedMapLibreLongitude = camera.longitude
        return cameraClampCallbackGuard.run {
            mapLibreMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(camera.latitude, camera.longitude))
                .zoom(camera.zoom)
                .bearing(camera.bearing)
                .tilt(camera.tilt)
                .build()
        }
    }

    /**
     * MapLibre can synchronously acknowledge an app-owned antimeridian clamp while retaining the
     * preceding out-of-envelope target until the next frame. Verify the live target after the
     * callback stack settles and reapply it for a bounded number of frames. A real user gesture
     * cancels this work immediately, so the verification can never fight direct manipulation.
     */
    private fun scheduleAppCameraTargetSettle(expected: osrsCameraState) {
        cancelAppCameraTargetSettle()
        var checks = 0
        var consecutiveMatches = 0
        lateinit var settle: Runnable
        settle = Runnable {
            if (appCameraTargetSettleRunnable !== settle) return@Runnable
            val mapLibreMap = map ?: run {
                appCameraTargetSettleRunnable = null
                return@Runnable
            }
            val live = mapLibreMap.cameraPosition
            val target = live.target ?: run {
                appCameraTargetSettleRunnable = null
                return@Runnable
            }
            val observed = osrsCameraState(
                latitude = target.latitude,
                longitude = osrsResolveMapLibreLongitudeRepresentation(
                    expected.longitude,
                    target.longitude
                ),
                zoom = live.zoom,
                bearing = live.bearing,
                tilt = live.tilt
            )
            checks += 1
            if (osrsCameraStatesEquivalent(expected, observed)) {
                lastObservedMapLibreLongitude = expected.longitude
                consecutiveMatches += 1
                if (consecutiveMatches >= OSRS_APP_CAMERA_SETTLE_REQUIRED_MATCHES) {
                    appCameraTargetSettleRunnable = null
                } else {
                    rootView.postDelayed(settle, OSRS_APP_CAMERA_SETTLE_DELAY_MILLIS)
                }
                return@Runnable
            }
            consecutiveMatches = 0
            mapLibreMap.cancelAllVelocityAnimations()
            mapLibreMap.cancelTransitions()
            setMapCamera(expected)
            if (checks < OSRS_APP_CAMERA_SETTLE_MAX_CHECKS) {
                rootView.postDelayed(settle, OSRS_APP_CAMERA_SETTLE_DELAY_MILLIS)
            } else {
                appCameraTargetSettleRunnable = null
            }
        }
        appCameraTargetSettleRunnable = settle
        rootView.postOnAnimation(settle)
    }

    private fun cancelAppCameraTargetSettle() {
        appCameraTargetSettleRunnable?.let(rootView::removeCallbacks)
        appCameraTargetSettleRunnable = null
    }

    private fun enforceActiveCameraEnvelope(phase: String): Boolean {
        val cameraPosition = map?.cameraPosition ?: return false
        val target = cameraPosition.target ?: return false
        val resolvedLongitude = lastObservedMapLibreLongitude?.let { previous ->
            osrsResolveMapLibreLongitudeRepresentation(previous, target.longitude)
        } ?: target.longitude
        val requested = osrsCameraState(
            latitude = target.latitude,
            longitude = resolvedLongitude,
            zoom = cameraPosition.zoom,
            bearing = cameraPosition.bearing,
            tilt = cameraPosition.tilt
        )
        val finalCamera = clampCameraForActiveEnvelope(requested, phase)
        lastObservedMapLibreLongitude = finalCamera.longitude
        if (osrsCameraTargetsEquivalent(finalCamera, requested)) return false
        map?.cancelAllVelocityAnimations()
        map?.cancelTransitions()
        return setMapCamera(finalCamera)
    }

    private fun completeCameraIdle() {
        if (cameraGestureTouchActive || cameraEdgePhysicsPhase != "idle") {
            scheduleCompassUpdateFromSettledCamera()
            renderDiagnostics()
            return
        }
        persistCurrentCamera()
        compassCameraUpdateRunnable?.let(rootView::removeCallbacks)
        compassCameraUpdateRunnable = null
        updateCompassFromCamera()
        lastRenderMarker = "camera-idle@${SystemClock.elapsedRealtimeNanos()}"
        renderDiagnostics()
    }

    /**
     * Coalesces compass rendering onto the next frame after MapLibre has finished the current
     * camera callback. Keeping view invalidation and north-facing fade scheduling out of the
     * finite-envelope callback is important at the antimeridian: MapLibre can synchronously echo
     * +180 while the app is applying the equivalent -180 clamp.
     */
    private fun scheduleCompassUpdateFromSettledCamera() {
        compassCameraUpdateRunnable?.let(rootView::removeCallbacks)
        lateinit var update: Runnable
        update = Runnable {
            if (compassCameraUpdateRunnable !== update) return@Runnable
            compassCameraUpdateRunnable = null
            updateCompassFromCamera()
        }
        compassCameraUpdateRunnable = update
        rootView.postOnAnimation(update)
    }

    private fun updateCompassFromCamera() {
        val bearing = map?.cameraPosition?.bearing ?: return
        compassView.updateBearing(bearing)
    }

    private fun resetMapBearingToNorth() {
        val mapLibreMap = map ?: return
        cancelCameraEdgePhysics("compass-reset")
        val startedAt = SystemClock.elapsedRealtimeNanos()
        mapLibreMap.easeCamera(
            CameraUpdateFactory.bearingTo(0.0),
            OSRS_COMPASS_RESET_DURATION_MILLIS.toInt()
        )
        refreshCompassThroughNorthReset()
        recordSimpleControl(SystemClock.elapsedRealtimeNanos() - startedAt)
    }

    private fun refreshCompassThroughNorthReset() {
        compassResetRefreshRunnable?.let(rootView::removeCallbacks)
        val deadline = SystemClock.uptimeMillis() + OSRS_COMPASS_RESET_DURATION_MILLIS +
            OSRS_COMPASS_RESET_REFRESH_SETTLE_MILLIS
        lateinit var refresh: Runnable
        refresh = Runnable {
            updateCompassFromCamera()
            renderDiagnostics()
            if (SystemClock.uptimeMillis() < deadline) {
                rootView.postOnAnimation(refresh)
            } else if (compassResetRefreshRunnable === refresh) {
                compassResetRefreshRunnable = null
            }
        }
        compassResetRefreshRunnable = refresh
        rootView.postOnAnimation(refresh)
    }

    private fun markLinkCameraApplied(realmId: String, plane: Int, phase: String) {
        val navigation = lastLinkNavigation ?: return
        if (navigation.targetRealmId != realmId || navigation.targetPlane != plane) return
        lastLinkNavigation = navigation.copy(
            appliedMarker = "camera-applied-$phase@${SystemClock.elapsedRealtimeNanos()}"
        )
    }

    private fun persistCurrentCamera() {
        val currentState = state ?: return
        val installedIdentity = cameraPersistenceOwnership.authorization(
            currentState,
            styleGeneration
        ) ?: run {
            lastCameraPersistenceMarker =
                "skipped-unowned:${currentState.activeRealmId}:${currentState.activePlane}:" +
                    "${currentState.switchRequestId}:$styleGeneration"
            return
        }
        val camera = map?.cameraPosition ?: return
        val target = camera.target ?: return
        val resolvedLongitude = lastObservedMapLibreLongitude?.let { previous ->
            osrsResolveMapLibreLongitudeRepresentation(previous, target.longitude)
        } ?: target.longitude
        val requestedSnapshot = osrsCameraState(
            latitude = target.latitude,
            longitude = resolvedLongitude,
            zoom = camera.zoom,
            bearing = camera.bearing,
            tilt = camera.tilt
        )
        val snapshot = clampCameraForActiveEnvelope(
            requestedSnapshot,
            "persist-camera"
        )
        if (!osrsCameraTargetsEquivalent(snapshot, requestedSnapshot)) {
            setMapCamera(snapshot)
        }
        lastObservedMapLibreLongitude = snapshot.longitude
        if (!snapshot.isWithin(currentState.activeAsset)) return
        state = reducer.reduce(
            currentState,
            osrsRealmAction.InstalledCameraChanged(
                realmId = installedIdentity.realmId,
                plane = installedIdentity.plane,
                requestId = installedIdentity.requestId,
                camera = snapshot
            )
        )
        lastCameraPersistenceMarker =
            "persisted:${installedIdentity.realmId}:${installedIdentity.plane}:" +
                "${installedIdentity.requestId}:${installedIdentity.styleGeneration}"
        stateStore.save(state!!)
    }

    private fun updateControls() {
        val current = state ?: return
        val realm = current.activeRealm
        val presentation = realmPresentations?.get(realm)
        val visibleRealmName = presentation?.visibleName ?: realm.canonicalName
        val accessibleRealmName = presentation?.accessibilityName ?: realm.canonicalName
        realmSelector?.updateActiveRealm(
            realmId = realm.id,
            visibleName = visibleRealmName,
            accessibilityName = accessibleRealmName
        )
        linksButton.visibility = View.GONE
        linksButton.isEnabled = false
        linksButton.contentDescription = null
        val orderedPlanes = realm.planes.sorted()
        if (orderedPlanes.size > 1) {
            floorControlCard.visibility = View.VISIBLE
            val activeIndex = orderedPlanes.indexOf(current.activePlane)
            val previousPlane = orderedPlanes.getOrNull(activeIndex - 1)
            val nextPlane = orderedPlanes.getOrNull(activeIndex + 1)
            floorCurrentText.text = getString(R.string.floor_number, current.activePlane)
            floorCurrentText.contentDescription = getString(
                R.string.floor_current_description,
                current.activePlane,
                accessibleRealmName
            )
            configureFloorButton(
                button = floorUpButton,
                actionablePlane = nextPlane,
                enabledDescription = getString(
                    R.string.floor_up_description,
                    accessibleRealmName
                )
            )
            configureFloorButton(
                button = floorDownButton,
                actionablePlane = previousPlane,
                enabledDescription = getString(
                    R.string.floor_down_description,
                    accessibleRealmName
                )
            )
        } else {
            floorControlCard.visibility = View.GONE
        }
        rootView.post {
            updateTopLeftControlGeometry()
            updateRealmSelectorGeometry()
        }
        renderDiagnostics()
    }

    private fun configureFloorButton(
        button: ImageButton,
        actionablePlane: Int?,
        enabledDescription: String
    ) {
        val actionable = actionablePlane != null
        button.alpha = if (actionable) 1f else 0.4f
        button.isEnabled = actionable
        button.isClickable = actionable
        button.isFocusable = actionable
        button.contentDescription = enabledDescription
        button.importantForAccessibility = if (actionable) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        button.setOnClickListener {
            actionablePlane?.let(::selectPlane)
        }
    }

    private fun registerMapDiagnostics() {
        mapView.addOnDidFinishRenderingMapListener { fully ->
            if (fully) {
                lastRenderMarker = "map-fully-rendered@${SystemClock.elapsedRealtimeNanos()}"
                renderDiagnostics()
            }
        }
        mapView.addOnDidBecomeIdleListener {
            lastRenderMarker = "map-idle@${SystemClock.elapsedRealtimeNanos()}"
            renderDiagnostics()
        }
        mapView.addOnDidFailLoadingMapListener { message ->
            lastError = message
            renderDiagnostics()
            Log.e(OSRS_LOG_TAG, "map_load_failed message=$message")
        }
    }

    private fun renderDiagnostics() {
        val diagnostics = debugStateForTesting()
        if (diagnostics.error != null) {
            statusText.text = diagnostics.error
            statusText.contentDescription = diagnostics.error
            statusText.visibility = View.VISIBLE
        } else {
            statusText.visibility = View.GONE
            statusText.text = ""
            statusText.contentDescription = null
        }
        statusText.post(::updateRealmSelectorGeometry)
    }

    private fun showFailure(prefix: String, failure: Throwable) {
        progress.visibility = View.GONE
        lastError = "$prefix: ${failure.message ?: failure.javaClass.simpleName}"
        Log.e(OSRS_LOG_TAG, lastError, failure)
        renderDiagnostics()
    }

    fun reloadStyleForTesting(): Boolean {
        val mapLibreMap = map ?: return false
        persistCurrentCamera()
        loadBaseStyle(mapLibreMap)
        return true
    }

    fun selectRealmForTesting(realmId: String): Boolean {
        val realm = state?.catalog?.byId?.get(realmId) ?: return false
        selectRealm(realm)
        return true
    }

    fun openRealmSelectorForTesting(): Boolean {
        if (state == null || realmSelectorIndex == null) return false
        showRealmSelector()
        return realmSelector?.debugState()?.expanded == true
    }

    fun filterRealmSelectorForTesting(query: String): Boolean {
        val search = realmSelector?.search ?: return false
        search.setText(query)
        search.setSelection(search.text.length)
        return true
    }

    fun dismissRealmSelectorForTesting() {
        realmSelector?.collapse(resetQuery = true)
    }

    fun focusRealmSelectorSearchForTesting(): Boolean =
        realmSelector?.focusSearch() == true

    fun showErrorForTesting(message: String = OSRS_TEST_ERROR_MESSAGE) {
        lastError = message
        renderDiagnostics()
    }

    fun clearErrorForTesting() {
        lastError = null
        renderDiagnostics()
    }

    fun realmSelectorStateForTesting(): osrsRealmSelectorDebugState? =
        realmSelector?.debugState()

    fun surfaceRealmIdForTesting(): String? =
        state?.catalog?.surface?.id

    fun firstMultiPlaneRealmIdForTesting(): String? =
        state?.catalog?.manifest?.realms
            ?.firstOrNull { it.planes.size > 1 }
            ?.id

    fun firstSinglePlaneRealmIdForTesting(): String? =
        state?.catalog?.manifest?.realms
            ?.firstOrNull { it.planes.size == 1 }
            ?.id

    fun firstFourPlaneRealmIdForTesting(): String? =
        state?.catalog?.manifest?.realms
            ?.firstOrNull {
                !it.isSurface && it.planes.containsAll(listOf(0, 1, 2, 3))
            }
            ?.id

    fun compactPlaneZeroRealmIdForTesting(): String? =
        state?.catalog?.manifest?.realms
            ?.asSequence()
            ?.filter { !it.isSurface && 0 in it.planes }
            ?.minByOrNull { realm ->
                val asset = requireNotNull(realm.assetForPlane(0))
                (asset.east - asset.west) * (asset.north - asset.south)
            }
            ?.id

    fun lowestPlaneForRealmForTesting(realmId: String): Int? =
        state?.catalog?.byId?.get(realmId)?.planes?.minOrNull()

    fun longestRealmIdForTesting(): String? {
        val current = state ?: return null
        val presentations = realmPresentations ?: return null
        return current.catalog.manifest.realms.maxWithOrNull(
            compareBy<osrsRealmRecord>(
                { presentations[it].visibleName.length },
                { it.id }
            )
        )?.id
    }

    fun realmLinksDialogStateForTesting(): osrsRealmLinksDialogDebugState? =
        linksDialog?.debugState()

    fun lastLinkDialogPhaseForTesting(): osrsLinkDialogOpenPhase? = lastLinkDialogPhase

    fun openRealmLinksForTesting(): Boolean {
        val current = state ?: return false
        if (current.activeRealm.links.isEmpty()) return false
        showRealmLinks()
        return linksDialog?.debugState()?.isShowing == true
    }

    fun linksActionAccessibilityTextForTesting(): String? =
        linksButton.contentDescription?.toString()

    fun selectPlaneForTesting(plane: Int): Boolean {
        val current = state ?: return false
        if (plane !in current.activeRealm.planes) return false
        selectPlane(plane)
        return true
    }

    fun moveCameraTargetForTesting(
        latitude: Double,
        longitude: Double,
        zoom: Double? = null,
        bearing: Double? = null,
        tilt: Double? = null
    ): Boolean {
        val current = map?.cameraPosition ?: return false
        applyCamera(
            osrsCameraState(
                latitude = latitude,
                longitude = longitude,
                zoom = zoom ?: current.zoom,
                bearing = bearing ?: current.bearing,
                tilt = tilt ?: current.tilt
            )
        )
        persistCurrentCamera()
        renderDiagnostics()
        return true
    }

    fun startZoomMomentumForTesting(
        velocityLevelsPerSecond: Double,
        focalXPx: Int,
        focalYPx: Int
    ): Boolean {
        if (map == null || focalXPx !in 0..mapView.width || focalYPx !in 0..mapView.height) {
            return false
        }
        cameraPinchFocalPoint = Point(focalXPx, focalYPx)
        beginCameraZoomRelease(velocityLevelsPerSecond)
        return cameraEdgePhysicsPhase == "zoom-inertia"
    }

    fun selectAuthoritativeLinkForTesting(
        linkId: String,
        traversalDirection: osrsRealmLinkTraversalDirection? = null,
        maximumViewportExtentDp: Double? = null
    ): Boolean {
        val current = state ?: return false
        val catalogCache = realmLinkCatalogCache ?: return false
        val candidates = catalogCache.get(current.activeRealm).catalog
            .availableRows
            .filter { it.link.id == linkId }
        val row = if (traversalDirection == null) {
            candidates.singleOrNull()
        } else {
            candidates.singleOrNull { it.side.traversalDirection == traversalDirection }
        } ?: return false
        selectAuthoritativeLink(row, maximumViewportExtentDp)
        return true
    }

    fun debugStateForTesting(): osrsMapDiagnostics {
        val current = state
        val camera = map?.cameraPosition
        val asset = current?.activeAsset
        val cameraEnvelope = activeCameraEnvelope()
        val visibleAssets = activeRasterComposition?.layersBottomToTop?.map { it.asset }.orEmpty()
        val visibleCanvasSize = visibleAssets.map { it.canvasSize }.distinct().singleOrNull()
        val visibleUnionWidthPx = visibleAssets.takeIf { it.isNotEmpty() }?.let { assets ->
            assets.maxOf { it.contentPixelBounds[2] } -
                assets.minOf { it.contentPixelBounds[0] }
        }
        val visibleUnionHeightPx = visibleAssets.takeIf { it.isNotEmpty() }?.let { assets ->
            assets.maxOf { it.contentPixelBounds[3] } -
                assets.minOf { it.contentPixelBounds[1] }
        }
        val cameraScreenPoint = camera?.target?.let { target ->
            map?.projection?.toScreenLocation(target)
        }
        val styleLayerOrder = style?.layers?.map { it.id }.orEmpty()
        val activeLayerPositions = activeRasterResources.map { resource ->
            styleLayerOrder.indexOf(resource.layerId)
        }
        val activeSelectorButton = if (::selectorButton.isInitialized) selectorButton else null
        val identityLayout = activeSelectorButton?.osrsRealmIdentityLayoutStateOrNull()
        val statusLayout = statusText.layout
        val statusLastLine = statusLayout?.takeIf { it.lineCount > 0 }?.let {
            minOf(it.lineCount, statusText.maxLines) - 1
        }
        val selectorState = realmSelector?.debugState()
        val selectorBounds = selectorState?.bounds
        val selectorAndFloorSeparated = if (
            floorControlCard.visibility == View.VISIBLE &&
            floorControlCard.bottom > 0 &&
            selectorBounds != null
        ) {
            selectorBounds.top >= floorControlCard.bottom + dp(OSRS_CONTROL_SEPARATION_DP) ||
                !osrsHorizontalRangesOverlapWithSeparation(
                    selectorLeftPx = selectorBounds.left,
                    selectorRightPx = selectorBounds.right,
                    controlLeftPx = floorControlCard.left,
                    controlRightPx = floorControlCard.right,
                    separationPx = dp(OSRS_CONTROL_SEPARATION_DP)
                )
        } else {
            null
        }
        val selectorAndLinksSeparated = if (
            linksButton.visibility == View.VISIBLE &&
            linksButton.bottom > 0 &&
            selectorBounds != null
        ) {
            selectorBounds.top >= linksButton.bottom + dp(OSRS_CONTROL_SEPARATION_DP) ||
                !osrsHorizontalRangesOverlapWithSeparation(
                    selectorLeftPx = selectorBounds.left,
                    selectorRightPx = selectorBounds.right,
                    controlLeftPx = linksButton.left,
                    controlRightPx = linksButton.right,
                    separationPx = dp(OSRS_CONTROL_SEPARATION_DP)
                )
        } else {
            null
        }
        val selectorAndStatusSeparated = if (
            statusText.visibility == View.VISIBLE &&
            statusText.bottom > 0 &&
            selectorBounds != null
        ) {
            selectorBounds.top >= statusText.bottom + dp(OSRS_CONTROL_SEPARATION_DP)
        } else {
            null
        }
        return osrsMapDiagnostics(
            candidate = current?.catalog?.manifest?.candidate,
            activeRealmId = current?.activeRealmId,
            activeRealmName = current?.activeRealm?.canonicalName,
            activeRealmDisplayName = current?.activeRealm?.let { realm ->
                realmPresentations?.get(realm)?.visibleName ?: realm.canonicalName
            },
            activeGroup = current?.activeRealm?.group,
            activeGroupLabel = current?.activeRealm?.group?.let(::osrsRealmGroupLabel),
            activePlane = current?.activePlane,
            activeSwitchRequestId = current?.switchRequestId,
            sourceId = activeSourceId,
            layerId = activeLayerId,
            visiblePlanesBottomToTop = activeRasterResources.map { it.plane },
            visibleSourceIdsBottomToTop = activeRasterResources.map { it.sourceId },
            visibleLayerIdsBottomToTop = activeRasterResources.map { it.layerId },
            visibleRasterOpacitiesBottomToTop = activeRasterResources.map { it.opacity },
            styleLayerOrder = styleLayerOrder,
            visibleLayerOrderMatchesStyle = activeLayerPositions.all { it >= 0 } &&
                activeLayerPositions.zipWithNext().all { (lower, upper) -> lower < upper },
            canonicalPlaneZeroAvailable =
                activeRasterComposition?.canonicalPlaneZeroAvailable,
            planeZeroResourceReused = lastRasterTransition?.reused?.any { it.plane == 0 },
            replacementPreparedBeforeRemoval =
                lastRasterTransition?.replacementPreparedBeforeRemoval,
            stagedAssetSha256ByPlane = activeStagedAssets
                .toSortedMap()
                .mapValues { it.value.sha256 },
            stagedAssetSha256 = stagedAssetSha256,
            stagedAssetPath = stagedAssetPath,
            realmBounds = asset?.contentLatlonBounds,
            cameraCenterEnvelope = cameraEnvelope?.let {
                listOf(it.west, it.south, it.east, it.north)
            },
            cameraVisibleCompositionCanvasSize = visibleCanvasSize,
            cameraVisibleCompositionHorizontalPaddingPx = if (
                visibleCanvasSize != null && visibleUnionWidthPx != null
            ) {
                visibleCanvasSize - visibleUnionWidthPx
            } else {
                null
            },
            cameraVisibleCompositionVerticalPaddingPx = if (
                visibleCanvasSize != null && visibleUnionHeightPx != null
            ) {
                visibleCanvasSize - visibleUnionHeightPx
            } else {
                null
            },
            cameraVisibleCompositionLongitudeSpanDegrees = cameraEnvelope?.let {
                it.east - it.west
            },
            cameraCopySafeMinZoom = activeCopySafeMinZoom,
            cameraRequestedLatitude = lastRequestedCameraTarget?.latitude,
            cameraRequestedLongitude = lastRequestedCameraTarget?.longitude,
            cameraFinalLatitude = lastFinalCameraTarget?.latitude,
            cameraFinalLongitude = lastFinalCameraTarget?.longitude,
            cameraClampState = lastCameraClampState,
            cameraClampCount = cameraClampCount,
            cameraClampSuppressedCallbacks = cameraClampCallbackGuard.suppressedCallbacks,
            centerEdgeOverflowEnabled = cameraEnvelope != null,
            horizontalWrapEnabled = false,
            cameraEdgePhysicsPhase = cameraEdgePhysicsPhase,
            cameraEdgePhysicsActive = cameraEdgePhysicsPhase != "idle",
            cameraEdgePhysicsFrameCount = cameraEdgePhysicsFrameCount,
            cameraEdgePeakLatitudeOvershoot = cameraEdgePeakLatitudeOvershoot,
            cameraEdgePeakLongitudeOvershoot = cameraEdgePeakLongitudeOvershoot,
            cameraPinchReleaseVelocityLevelsPerSecond =
                cameraPinchReleaseVelocityLevelsPerSecond,
            cameraZoomMomentumPeakContinuation = cameraZoomMomentumPeakContinuation,
            cameraZoomMomentumFocalDriftPx = cameraZoomMomentumFocalDriftPx,
            lastCameraZoomMomentumDurationNanos = lastCameraZoomMomentumDurationNanos,
            lastCameraZoomMomentumFrameCount = lastCameraZoomMomentumFrameCount,
            lastCameraEdgeBounceDurationNanos = lastCameraEdgeBounceDurationNanos,
            lastCameraEdgeBounceFrameCount = lastCameraEdgeBounceFrameCount,
            lastCameraClampNanos = lastCameraClampNanos,
            cameraClampP95Nanos = percentile95(cameraClampDurationsNanos),
            cameraLatitude = camera?.target?.latitude,
            cameraLongitude = camera?.target?.longitude,
            cameraZoom = camera?.zoom,
            cameraBearing = camera?.bearing,
            cameraTilt = camera?.tilt,
            compassLeftPx = compassView.left,
            compassTopPx = compassView.top,
            compassRightPx = compassView.right,
            compassBottomPx = compassView.bottom,
            compassVisible = compassView.visibility == View.VISIBLE,
            compassNormalizedBearing = compassView.normalizedBearingForTesting(),
            compassNeedleRotationDegrees = compassView.needleRotationForTesting(),
            compassWholeViewRotationDegrees = compassView.rotation,
            compassFacingNorth = compassView.facingNorthForTesting(),
            compassFadePending = compassView.fadePendingForTesting(),
            compassRightVisualMarginPx = rootView.width - compassView.right,
            mapDrawableCenterXPx = mapView.width / 2f,
            mapDrawableCenterYPx = mapView.height / 2f,
            cameraTargetScreenXPx = cameraScreenPoint?.x,
            cameraTargetScreenYPx = cameraScreenPoint?.y,
            styleGeneration = styleGeneration,
            installedCameraRealmId = cameraPersistenceOwnership.installedIdentity?.realmId,
            installedCameraPlane = cameraPersistenceOwnership.installedIdentity?.plane,
            installedCameraRequestId = cameraPersistenceOwnership.installedIdentity?.requestId,
            installedCameraStyleGeneration = cameraPersistenceOwnership.installedIdentity?.styleGeneration,
            cameraPersistenceMarker = lastCameraPersistenceMarker,
            renderMarker = lastRenderMarker,
            switchRequestedAtNanos = switchRequestedAtNanos,
            switchCompletedAtNanos = switchCompletedAtNanos,
            lastAppOwnedSwitchNanos = switchRequestedAtNanos?.let { start ->
                switchCompletedAtNanos?.minus(start)
            },
            lastStageNanos = lastStageNanos,
            switchP95Nanos = percentile95(switchDurationsNanos),
            simpleControlP95Nanos = percentile95(simpleControlDurationsNanos),
            lastSelectorFilterNanos = lastSelectorFilterNanos,
            lastSelectorToggleNanos = lastSelectorToggleNanos,
            lastSelectorOutsideDismissNanos = lastSelectorOutsideDismissNanos,
            selectorOutsideDismissCount = selectorOutsideDismissCount,
            lastLinkDialogOpenNanos = lastLinkDialogOpenNanos,
            coldLinkDialogOpenNanos = coldLinkDialogOpenNanos,
            repeatedLinkDialogP95Nanos = percentile95(repeatedLinkDialogDurationsNanos),
            lastLinkFilterNanos = lastLinkFilterNanos,
            lastLinkFilterResultCount = lastLinkFilterResultCount,
            fontScale = resources.configuration.fontScale,
            screenWidthDp = resources.configuration.screenWidthDp,
            screenHeightDp = resources.configuration.screenHeightDp,
            selectorIdentityAccessibilityText = activeSelectorButton?.contentDescription?.toString(),
            selectorIdentityTextLength = identityLayout?.textLength,
            selectorIdentityLineCount = identityLayout?.lineCount,
            selectorIdentityMaxLines = activeSelectorButton?.maxLines ?: 0,
            selectorIdentityLastVisibleEnd = identityLayout?.lastVisibleEnd,
            selectorIdentityEllipsisCount = identityLayout?.ellipsisCount,
            selectorIdentityHonest = identityLayout?.honest,
            selectorIdentityWidthPx = activeSelectorButton?.width ?: 0,
            selectorIdentityHeightPx = activeSelectorButton?.height ?: 0,
            selectorExpanded = selectorState?.expanded,
            selectorImeVisible = selectorState?.imeVisible,
            selectorSearchFocused = selectorState?.searchFocused,
            selectorQuery = selectorState?.query,
            selectorVisibleResultCount = selectorState?.visibleResultCount,
            selectorTopObstructionPx = selectorState?.topObstructionPx,
            selectorOutsideDismissAvailable = selectorState?.outsideDismissAvailable,
            selectorLeftPx = selectorBounds?.left,
            selectorTopPx = selectorBounds?.top,
            selectorRightPx = selectorBounds?.right,
            selectorBottomPx = selectorBounds?.bottom,
            selectorBaseRowTopPx = selectorState?.baseRowTop,
            selectorBaseRowBottomPx = selectorState?.baseRowBottom,
            selectorSearchLeftPx = selectorState?.searchLeft,
            selectorSearchTopPx = selectorState?.searchTop,
            selectorSearchRightPx = selectorState?.searchRight,
            selectorSearchBottomPx = selectorState?.searchBottom,
            selectorSearchClickable = realmSelector?.search?.isClickable,
            selectorSearchFocusable = realmSelector?.search?.isFocusable,
            selectorListLeftPx = selectorState?.listLeft,
            selectorListTopPx = selectorState?.listTop,
            selectorListRightPx = selectorState?.listRight,
            selectorListBottomPx = selectorState?.listBottom,
            selectorFirstResultLeftPx = selectorState?.firstResultLeft,
            selectorFirstResultTopPx = selectorState?.firstResultTop,
            selectorFirstResultRightPx = selectorState?.firstResultRight,
            selectorFirstResultBottomPx = selectorState?.firstResultBottom,
            selectorFirstResultClickable = selectorState?.firstResultClickable,
            selectorFirstResultFocusable = selectorState?.firstResultFocusable,
            selectorFirstResultText = selectorState?.firstResultText,
            selectorFirstResultAccessibilityText =
                selectorState?.firstResultAccessibilityText,
            selectorActiveViewportBottomGapPx = selectorBounds?.let { bounds ->
                val activeBottomInset = if (imeVisible) {
                    maxOf(systemBarInsets.bottom, imeBottomInset)
                } else {
                    systemBarInsets.bottom
                }
                rootView.height - activeBottomInset - bounds.bottom
            },
            floorControlVisible = floorControlCard.visibility == View.VISIBLE,
            floorControlLeftPx = floorControlCard.left,
            floorControlTopPx = floorControlCard.top,
            floorControlRightPx = floorControlCard.right,
            floorControlBottomPx = floorControlCard.bottom,
            floorUpWidthPx = floorUpButton.width,
            floorUpHeightPx = floorUpButton.height,
            floorDownWidthPx = floorDownButton.width,
            floorDownHeightPx = floorDownButton.height,
            floorButtonsSeparated = floorUpButton.bottom <= floorDownButton.top,
            linksButtonLeftPx = linksButton.left,
            linksButtonTopPx = linksButton.top,
            linksButtonRightPx = linksButton.right,
            linksButtonBottomPx = linksButton.bottom,
            linksButtonCornerRadiusPx = linksButton.cornerRadius,
            floorControlCornerRadiusPx = floorControlCard.radius,
            floorLeftVisualMarginPx = floorControlCard.left,
            realmLinksUiEnabled = OSRS_REALM_LINKS_UI_ENABLED,
            realmLinksActionVisible = linksButton.visibility == View.VISIBLE,
            topAndFloorControlsSeparated = selectorAndFloorSeparated,
            selectorAndLinksSeparated = selectorAndLinksSeparated,
            selectorAndStatusSeparated = selectorAndStatusSeparated,
            statusVisible = statusText.visibility == View.VISIBLE,
            statusTopPx = statusText.top,
            statusBottomPx = statusText.bottom,
            statusTextLength = statusText.text.length,
            statusLastVisibleEnd = statusLastLine?.let { line ->
                statusLayout?.getLineVisibleEnd(line)
            },
            statusEllipsisCount = statusLastLine?.let { line ->
                statusLayout?.getEllipsisCount(line)
            },
            statusAccessibilityText = statusText.contentDescription?.toString(),
            compactLandscapeImeChrome = compactLandscapeImeChrome,
            selectorStatusSeparationPx = if (
                statusText.visibility == View.VISIBLE &&
                statusText.bottom > 0 &&
                selectorBounds != null
            ) {
                selectorBounds.top - statusText.bottom
            } else {
                null
            },
            manifestRealmCount = current?.catalog?.realmCount,
            selectorRealmCount = current?.catalog?.selectorCount,
            manifestAssetNonblank = asset?.nonblank,
            availableLinkCount = current?.activeRealm?.let { realm ->
                realm.links
                    .asSequence()
                    .filter { it.authoritative && it.availability == "available" }
                    .sumOf { it.endpointSidesFor(realm.id).size }
            },
            unavailableLinkCount = current?.activeRealm?.links?.count {
                !it.authoritative || it.availability != "available"
            },
            selectedLinkId = lastLinkNavigation?.linkId,
            selectedLinkSideKey = lastLinkNavigation?.linkSideKey,
            selectedLinkTraversalDirection = lastLinkNavigation?.traversalDirection?.name,
            linkTargetRealmId = lastLinkNavigation?.targetRealmId,
            linkTargetPlane = lastLinkNavigation?.targetPlane,
            linkTargetGameX = lastLinkNavigation?.targetGameX,
            linkTargetGameY = lastLinkNavigation?.targetGameY,
            linkMappedLatitude = lastLinkNavigation?.mappedLatitude,
            linkMappedLongitude = lastLinkNavigation?.mappedLongitude,
            linkMappedZoom = lastLinkNavigation?.mappedZoom,
            linkSourceZoom = lastLinkNavigation?.sourceZoom,
            linkSourceNativeMaxZoom = lastLinkNavigation?.sourceNativeMaxZoom,
            linkTargetNativeMaxZoom = lastLinkNavigation?.targetNativeMaxZoom,
            linkRelativeZoom = lastLinkNavigation?.relativeZoom,
            linkRequestedZoom = lastLinkNavigation?.requestedZoom,
            linkFinalZoom = lastLinkNavigation?.finalZoom,
            linkZoomClampState = lastLinkNavigation?.clampState,
            linkMatchingLayoutCount = lastLinkNavigation?.matchingLayoutCount,
            linkAppliedMarker = lastLinkNavigation?.appliedMarker,
            error = lastError
        )
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        cancelCameraEdgePhysics("pause")
        enforceActiveCameraEnvelope("pause")
        persistCurrentCamera()
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        realmSelector?.release()
        linksDialog?.dismiss()
        switchJob?.cancel()
        cancelCameraEdgePhysics("destroy")
        cameraVelocityTracker?.recycle()
        cameraVelocityTracker = null
        mapView.setOnTouchListener(null)
        cancelAppCameraTargetSettle()
        compassCameraUpdateRunnable?.let(rootView::removeCallbacks)
        compassCameraUpdateRunnable = null
        compassResetRefreshRunnable?.let(rootView::removeCallbacks)
        compassResetRefreshRunnable = null
        compassView.release()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistCurrentCamera()
        state?.let {
            outState.putString(OSRS_SAVED_REALM_ID, it.activeRealmId)
            outState.putInt(OSRS_SAVED_PLANE, it.activePlane)
        }
        realmSelector?.debugState()?.let { selectorState ->
            outState.putBoolean(OSRS_SAVED_SELECTOR_EXPANDED, selectorState.expanded)
            outState.putString(OSRS_SAVED_SELECTOR_QUERY, selectorState.query)
            outState.putBoolean(
                OSRS_SAVED_SELECTOR_SEARCH_FOCUSED,
                selectorState.searchFocused
            )
        }
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    private fun recordSwitchDuration(nanos: Long) {
        switchDurationsNanos.addLast(nanos)
        while (switchDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) switchDurationsNanos.removeFirst()
    }

    private fun recordSimpleControl(nanos: Long) {
        simpleControlDurationsNanos.addLast(nanos)
        while (simpleControlDurationsNanos.size > OSRS_PERFORMANCE_SAMPLE_LIMIT) simpleControlDurationsNanos.removeFirst()
    }

    private fun percentile95(samples: Collection<Long>): Long? {
        if (samples.isEmpty()) return null
        val sorted = samples.sorted()
        val index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(sorted.indices)
        return sorted[index]
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val OSRS_LOG_TAG = "osrsUndergroundMaps"
        private const val OSRS_SAVED_REALM_ID = "osrs.saved.realm.id"
        private const val OSRS_SAVED_PLANE = "osrs.saved.realm.plane"
        private const val OSRS_SAVED_SELECTOR_EXPANDED = "osrs.saved.selector.expanded"
        private const val OSRS_SAVED_SELECTOR_QUERY = "osrs.saved.selector.query"
        private const val OSRS_SAVED_SELECTOR_SEARCH_FOCUSED =
            "osrs.saved.selector.search.focused"
        private const val OSRS_PERFORMANCE_SAMPLE_LIMIT = 100
        private const val OSRS_EDGE_MAXIMUM_RELEASE_VELOCITY_DP_PER_SECOND = 12_000
        private const val OSRS_EDGE_MINIMUM_RELEASE_SPEED_DP_PER_SECOND = 80
        private const val OSRS_EDGE_INERTIA_STOP_SPEED_DP_PER_SECOND = 12
        private const val OSRS_EDGE_INERTIA_DECELERATION_PER_SECOND = 5.2
        private const val OSRS_EDGE_MINIMUM_FRAME_SECONDS = 1.0 / 240.0
        private const val OSRS_EDGE_MAXIMUM_FRAME_SECONDS = 1.0 / 30.0
        private const val OSRS_APP_CAMERA_SETTLE_DELAY_MILLIS = 50L
        private const val OSRS_APP_CAMERA_SETTLE_MAX_CHECKS = 20
        private const val OSRS_APP_CAMERA_SETTLE_REQUIRED_MATCHES = 2
        private const val OSRS_COMPASS_RESET_REFRESH_SETTLE_MILLIS = 32L
        private const val OSRS_MINIMUM_TOUCH_TARGET_DP = 48
        private const val OSRS_CONTROL_SEPARATION_DP = 12
        private const val OSRS_STATUS_TEXT_SP = 11
        private const val OSRS_COMPACT_STATUS_MIN_TEXT_SP = 6
        private const val OSRS_COMPACT_STATUS_HEIGHT_DP = 16
        private const val OSRS_FLOOR_TEXT_SP = 17
        private const val OSRS_COMPACT_FLOOR_MIN_TEXT_SP = 6
        private const val OSRS_COMPACT_FLOOR_LABEL_HEIGHT_DP = 16
        private const val OSRS_TEST_ERROR_MESSAGE =
            "Direction 3 deterministic error surface\nSelector obstruction verification"
        private val OSRS_BASE_STYLE_JSON = """
            {
              "version": 8,
              "name": "OSRS Underground Maps Local Raster",
              "sources": {},
              "layers": [
                {
                  "id": "osrs-background",
                  "type": "background",
                  "paint": { "background-color": "#000000" }
                }
              ]
            }
        """.trimIndent()
    }
}

data class osrsLinkDialogOpenPhase(
    val ordinal: Int,
    val cold: Boolean,
    val realmId: String,
    val availableCount: Int,
    val unavailableCount: Int,
    val catalogCacheHit: Boolean,
    val catalogNanos: Long,
    val catalogBuildNanos: Long,
    val priorDialogCleanupNanos: Long,
    val dialogReused: Boolean,
    val viewConstructionNanos: Long,
    val initialFilterNanos: Long,
    val initialRowConversionNanos: Long,
    val initialAdapterSubmissionNanos: Long,
    val initialUpdateStrategy: String,
    val initialFilterObserverNanos: Long,
    val materialShowNanos: Long,
    val unclassifiedNanos: Long,
    val appNanos: Long,
    val showingAfterReturn: Boolean
) {
    val reconciledNanos: Long
        get() = catalogNanos +
            priorDialogCleanupNanos +
            viewConstructionNanos +
            initialFilterNanos +
            initialRowConversionNanos +
            initialAdapterSubmissionNanos +
            materialShowNanos +
            unclassifiedNanos
}

data class osrsMapDiagnostics(
    val candidate: String?,
    val activeRealmId: String?,
    val activeRealmName: String?,
    val activeRealmDisplayName: String?,
    val activeGroup: String?,
    val activeGroupLabel: String?,
    val activePlane: Int?,
    val activeSwitchRequestId: Long?,
    val sourceId: String?,
    val layerId: String?,
    val visiblePlanesBottomToTop: List<Int>,
    val visibleSourceIdsBottomToTop: List<String>,
    val visibleLayerIdsBottomToTop: List<String>,
    val visibleRasterOpacitiesBottomToTop: List<Float>,
    val styleLayerOrder: List<String>,
    val visibleLayerOrderMatchesStyle: Boolean,
    val canonicalPlaneZeroAvailable: Boolean?,
    val planeZeroResourceReused: Boolean?,
    val replacementPreparedBeforeRemoval: Boolean?,
    val stagedAssetSha256ByPlane: Map<Int, String>,
    val stagedAssetSha256: String?,
    val stagedAssetPath: String?,
    val realmBounds: List<Double>?,
    val cameraCenterEnvelope: List<Double>?,
    val cameraVisibleCompositionCanvasSize: Int?,
    val cameraVisibleCompositionHorizontalPaddingPx: Int?,
    val cameraVisibleCompositionVerticalPaddingPx: Int?,
    val cameraVisibleCompositionLongitudeSpanDegrees: Double?,
    val cameraCopySafeMinZoom: Double?,
    val cameraRequestedLatitude: Double?,
    val cameraRequestedLongitude: Double?,
    val cameraFinalLatitude: Double?,
    val cameraFinalLongitude: Double?,
    val cameraClampState: String,
    val cameraClampCount: Int,
    val cameraClampSuppressedCallbacks: Int,
    val centerEdgeOverflowEnabled: Boolean,
    val horizontalWrapEnabled: Boolean,
    val cameraEdgePhysicsPhase: String,
    val cameraEdgePhysicsActive: Boolean,
    val cameraEdgePhysicsFrameCount: Int,
    val cameraEdgePeakLatitudeOvershoot: Double,
    val cameraEdgePeakLongitudeOvershoot: Double,
    val cameraPinchReleaseVelocityLevelsPerSecond: Double,
    val cameraZoomMomentumPeakContinuation: Double,
    val cameraZoomMomentumFocalDriftPx: Double?,
    val lastCameraZoomMomentumDurationNanos: Long?,
    val lastCameraZoomMomentumFrameCount: Int,
    val lastCameraEdgeBounceDurationNanos: Long?,
    val lastCameraEdgeBounceFrameCount: Int,
    val lastCameraClampNanos: Long?,
    val cameraClampP95Nanos: Long?,
    val cameraLatitude: Double?,
    val cameraLongitude: Double?,
    val cameraZoom: Double?,
    val cameraBearing: Double?,
    val cameraTilt: Double?,
    val compassLeftPx: Int,
    val compassTopPx: Int,
    val compassRightPx: Int,
    val compassBottomPx: Int,
    val compassVisible: Boolean,
    val compassNormalizedBearing: Double,
    val compassNeedleRotationDegrees: Float,
    val compassWholeViewRotationDegrees: Float,
    val compassFacingNorth: Boolean,
    val compassFadePending: Boolean,
    val compassRightVisualMarginPx: Int,
    val mapDrawableCenterXPx: Float,
    val mapDrawableCenterYPx: Float,
    val cameraTargetScreenXPx: Float?,
    val cameraTargetScreenYPx: Float?,
    val styleGeneration: Int,
    val installedCameraRealmId: String?,
    val installedCameraPlane: Int?,
    val installedCameraRequestId: Long?,
    val installedCameraStyleGeneration: Int?,
    val cameraPersistenceMarker: String,
    val renderMarker: String,
    val switchRequestedAtNanos: Long?,
    val switchCompletedAtNanos: Long?,
    val lastAppOwnedSwitchNanos: Long?,
    val lastStageNanos: Long?,
    val switchP95Nanos: Long?,
    val simpleControlP95Nanos: Long?,
    val lastSelectorFilterNanos: Long?,
    val lastSelectorToggleNanos: Long?,
    val lastSelectorOutsideDismissNanos: Long?,
    val selectorOutsideDismissCount: Int,
    val lastLinkDialogOpenNanos: Long?,
    val coldLinkDialogOpenNanos: Long?,
    val repeatedLinkDialogP95Nanos: Long?,
    val lastLinkFilterNanos: Long?,
    val lastLinkFilterResultCount: Int?,
    val fontScale: Float,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val selectorIdentityAccessibilityText: String?,
    val selectorIdentityTextLength: Int?,
    val selectorIdentityLineCount: Int?,
    val selectorIdentityMaxLines: Int,
    val selectorIdentityLastVisibleEnd: Int?,
    val selectorIdentityEllipsisCount: Int?,
    val selectorIdentityHonest: Boolean?,
    val selectorIdentityWidthPx: Int,
    val selectorIdentityHeightPx: Int,
    val selectorExpanded: Boolean?,
    val selectorImeVisible: Boolean?,
    val selectorSearchFocused: Boolean?,
    val selectorQuery: String?,
    val selectorVisibleResultCount: Int?,
    val selectorTopObstructionPx: Int?,
    val selectorOutsideDismissAvailable: Boolean?,
    val selectorLeftPx: Int?,
    val selectorTopPx: Int?,
    val selectorRightPx: Int?,
    val selectorBottomPx: Int?,
    val selectorBaseRowTopPx: Int?,
    val selectorBaseRowBottomPx: Int?,
    val selectorSearchLeftPx: Int?,
    val selectorSearchTopPx: Int?,
    val selectorSearchRightPx: Int?,
    val selectorSearchBottomPx: Int?,
    val selectorSearchClickable: Boolean?,
    val selectorSearchFocusable: Boolean?,
    val selectorListLeftPx: Int?,
    val selectorListTopPx: Int?,
    val selectorListRightPx: Int?,
    val selectorListBottomPx: Int?,
    val selectorFirstResultLeftPx: Int?,
    val selectorFirstResultTopPx: Int?,
    val selectorFirstResultRightPx: Int?,
    val selectorFirstResultBottomPx: Int?,
    val selectorFirstResultClickable: Boolean?,
    val selectorFirstResultFocusable: Boolean?,
    val selectorFirstResultText: String?,
    val selectorFirstResultAccessibilityText: String?,
    val selectorActiveViewportBottomGapPx: Int?,
    val floorControlVisible: Boolean,
    val floorControlLeftPx: Int,
    val floorControlTopPx: Int,
    val floorControlRightPx: Int,
    val floorControlBottomPx: Int,
    val floorUpWidthPx: Int,
    val floorUpHeightPx: Int,
    val floorDownWidthPx: Int,
    val floorDownHeightPx: Int,
    val floorButtonsSeparated: Boolean,
    val linksButtonLeftPx: Int,
    val linksButtonTopPx: Int,
    val linksButtonRightPx: Int,
    val linksButtonBottomPx: Int,
    val linksButtonCornerRadiusPx: Int,
    val floorControlCornerRadiusPx: Float,
    val floorLeftVisualMarginPx: Int,
    val realmLinksUiEnabled: Boolean,
    val realmLinksActionVisible: Boolean,
    val topAndFloorControlsSeparated: Boolean?,
    val selectorAndLinksSeparated: Boolean?,
    val selectorAndStatusSeparated: Boolean?,
    val statusVisible: Boolean,
    val statusTopPx: Int,
    val statusBottomPx: Int,
    val statusTextLength: Int,
    val statusLastVisibleEnd: Int?,
    val statusEllipsisCount: Int?,
    val statusAccessibilityText: String?,
    val compactLandscapeImeChrome: Boolean,
    val selectorStatusSeparationPx: Int?,
    val manifestRealmCount: Int?,
    val selectorRealmCount: Int?,
    val manifestAssetNonblank: Boolean?,
    val availableLinkCount: Int?,
    val unavailableLinkCount: Int?,
    val selectedLinkId: String?,
    val selectedLinkSideKey: String?,
    val selectedLinkTraversalDirection: String?,
    val linkTargetRealmId: String?,
    val linkTargetPlane: Int?,
    val linkTargetGameX: Int?,
    val linkTargetGameY: Int?,
    val linkMappedLatitude: Double?,
    val linkMappedLongitude: Double?,
    val linkMappedZoom: Double?,
    val linkSourceZoom: Double?,
    val linkSourceNativeMaxZoom: Int?,
    val linkTargetNativeMaxZoom: Int?,
    val linkRelativeZoom: Double?,
    val linkRequestedZoom: Double?,
    val linkFinalZoom: Double?,
    val linkZoomClampState: String?,
    val linkMatchingLayoutCount: Int?,
    val linkAppliedMarker: String?,
    val error: String?
)

private data class osrsLinkNavigationState(
    val linkId: String,
    val linkSideKey: String,
    val traversalDirection: osrsRealmLinkTraversalDirection,
    val targetRealmId: String,
    val targetPlane: Int,
    val targetGameX: Int,
    val targetGameY: Int,
    val mappedLatitude: Double,
    val mappedLongitude: Double,
    val mappedZoom: Double,
    val sourceZoom: Double,
    val sourceNativeMaxZoom: Int,
    val targetNativeMaxZoom: Int,
    val relativeZoom: Double,
    val requestedZoom: Double,
    val finalZoom: Double,
    val clampState: String,
    val matchingLayoutCount: Int,
    val appliedMarker: String
)

private data class osrsActiveCameraEnvelopeBinding(
    val identity: osrsInstalledCameraIdentity,
    val envelope: osrsCameraCenterEnvelope
)

private data class osrsCameraTarget(
    val latitude: Double,
    val longitude: Double
)
