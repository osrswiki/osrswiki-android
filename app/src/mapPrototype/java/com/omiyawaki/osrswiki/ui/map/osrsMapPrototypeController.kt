package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import com.omiyawaki.osrswiki.util.log.L
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class osrsMapPrototypeController(
    private val binding: FragmentMapBinding,
    private val context: Context,
    private val mapProvider: () -> MapLibreMap?,
    private val logTag: String,
    restoredState: Bundle? = null
) {
    private var prototypeLabelsVisible = restoredState?.getBoolean(STATE_LABELS, true) ?: true
    private var prototypePoisVisible = restoredState?.getBoolean(STATE_POIS, true) ?: true
    private var prototypeLinksVisible = restoredState?.getBoolean(STATE_LINKS, true) ?: true
    private val highlightedCategories = restoredState
        ?.getStringArrayList(STATE_HIGHLIGHTED_CATEGORIES)
        .orEmpty()
        .mapNotNull { value -> osrsMapCategory.entries.firstOrNull { it.value == value } }
        .toMutableSet()
    private var prototypeReferenceStopIndex = restoredState?.getInt(STATE_REFERENCE_STOP, 3) ?: 3
    private var keyPanelVisible = restoredState?.getBoolean(STATE_KEY_PANEL, true) ?: true
    private var overviewVisible = restoredState?.getBoolean(STATE_OVERVIEW, true) ?: true
    private var activeSurfaceId = restoredState?.getString(STATE_SURFACE_ID) ?: SURFACE_GIELINOR
    private var initialViewportApplied = false
    private var initialViewportScheduled = false
    private var viewportPaddingGeneration = 0
    private var viewportPaddingTopPx = 0
    private var viewportPaddingBottomPx = 0
    private var pendingViewportCameraOverride: osrsMapCameraState? = null
    private var pendingViewportOnApplied: (() -> Unit)? = null
    private var controlsPrepared = false
    private var overlayView: osrsMapPrototypeOverlayView? = null
    private var cameraMoveListener: MapLibreMap.OnCameraMoveListener? = null
    private var cameraIdleListener: MapLibreMap.OnCameraIdleListener? = null
    private var mapClickListener: MapLibreMap.OnMapClickListener? = null
    private var lastControlDurationMs: Double? = if (restoredState?.containsKey(STATE_CONTROL_DURATION) == true) {
        restoredState.getDouble(STATE_CONTROL_DURATION)
    } else {
        null
    }
    private val history = mutableListOf<osrsMapNavigationSnapshot>()
    private var currentNavigationSnapshot = restoredState
        ?.getBundle(STATE_CURRENT_NAVIGATION)
        ?.navigationSnapshot()
    private val restoredCamera = currentNavigationSnapshot?.cameraState() ?: restoredState?.cameraState()
    private var currentSearchQuery = restoredState?.getString(STATE_SEARCH_QUERY).orEmpty()
    private var statusText = restoredState?.getString(STATE_STATUS_TEXT)
    private var statusContentDescription = restoredState?.getString(STATE_STATUS_CONTENT_DESCRIPTION)
    private var statusVisible = restoredState?.getBoolean(STATE_STATUS_VISIBLE, statusText != null)
        ?: (statusText != null)
    private var overviewInteractionActive = false
    private var applyingNavigationSnapshot = false
    private var lastHitFeatureId: String? = restoredState?.getString(STATE_LAST_HIT_FEATURE)
    private var lastActionDescription: String? = restoredState?.getString(STATE_LAST_ACTION_DESCRIPTION)

    init {
        restoredState?.navigationHistory()?.let(history::addAll)
    }

    fun prepare() {
        adoptLoadingPreview()
        applyResponsiveControlGeometry()
        binding.prototypeProductControls.visibility = View.VISIBLE
        binding.prototypeDebugControls.visibility = View.VISIBLE
        applyKeyPanelVisibility()
        binding.prototypeOverview.visibility = if (overviewVisible) View.VISIBLE else View.GONE
        binding.mapMarkerInfoButton.visibility = View.GONE
        if (!controlsPrepared) {
            setupLayerControls()
            setupSearch()
            setupKeyPanel()
            setupSurfaceSelector()
            setupOverview()
            setupHistory()
            controlsPrepared = true
        }
        setSearchQuery(currentSearchQuery)
        applyStatusState()
        updateControlLabels()
        updateHistoryButton()
    }

    private fun applyResponsiveControlGeometry() {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).roundToInt()

        val floorParams = binding.floorControls.layoutParams as ConstraintLayout.LayoutParams
        val overviewParams = binding.prototypeOverview.layoutParams as ConstraintLayout.LayoutParams
        if (isLandscape()) {
            binding.floorControls.orientation = LinearLayout.HORIZONTAL
            binding.floorControlText.layoutParams = LinearLayout.LayoutParams(dp(40), dp(48))
            floorParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            floorParams.startToEnd = R.id.prototype_overview
            floorParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            floorParams.marginEnd = 0
            floorParams.topToBottom = R.id.prototype_product_controls
            overviewParams.width = dp(96)
            overviewParams.height = dp(64)
        } else {
            binding.floorControls.orientation = LinearLayout.VERTICAL
            binding.floorControlText.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            floorParams.startToEnd = ConstraintLayout.LayoutParams.UNSET
            floorParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            floorParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            floorParams.marginEnd = dp(16)
            floorParams.topToBottom = R.id.prototype_key_panel
            overviewParams.width = dp(168)
            overviewParams.height = dp(116)
        }
        binding.floorControls.layoutParams = floorParams
        binding.prototypeOverview.layoutParams = overviewParams
    }

    fun enable(maplibreMap: MapLibreMap) {
        prepare()
        ensureOverlayView(maplibreMap)
        installCameraListeners(maplibreMap)
        installHitTesting(maplibreMap)
        if (!initialViewportApplied && !initialViewportScheduled) {
            initialViewportScheduled = true
            val camera = restoredCamera ?: osrsMapCameraState(
                osrsMapPrototypeOverlay.initialCenter().latitude,
                osrsMapPrototypeOverlay.initialCenter().longitude,
                osrsMapPrototypeOverlay.initialZoom
            )
            scheduleMapViewportPadding(
                maplibreMap = maplibreMap,
                cameraOverride = camera
            ) {
                initialViewportApplied = true
                initialViewportScheduled = false
                initializeOrSyncNavigationSnapshot()
            }
        } else {
            scheduleMapViewportPadding(maplibreMap = maplibreMap)
        }
        applyCurrentVisibility()
        binding.prototypeOverview.cameraChanged()
    }

    fun disable(maplibreMap: MapLibreMap?) {
        viewportPaddingGeneration += 1
        initialViewportScheduled = false
        pendingViewportCameraOverride = null
        pendingViewportOnApplied = null
        removeCameraListeners(maplibreMap)
        removeHitTesting(maplibreMap)
        overlayView?.let { binding.mapView.removeView(it) }
        overlayView = null
        binding.prototypeProductControls.visibility = View.GONE
        binding.prototypeDebugControls.visibility = View.GONE
        binding.prototypeKeyPanel.visibility = View.GONE
        binding.prototypeOverview.visibility = View.GONE
        if (controlsPrepared) {
            binding.mapMarkerInfoButton.visibility = View.VISIBLE
        }
    }

    fun onMapViewAttached() {
        binding.mapView.findViewById<ImageView>(R.id.map_prototype_loading_preview)?.bringToFront()
        overlayView?.bringToFront()
        refreshControlRenderNodes()
    }

    fun onFirstTerrainFrame(encodingMs: Double, renderingMs: Double) {
        val preview = binding.mapView.findViewById<View>(R.id.map_prototype_loading_preview)
        overlayView?.bringToFront()
        osrsMapPrototypePerformance.markFirstTerrain(encodingMs, renderingMs, fully = true)
        if (preview != null) {
            preview.postOnAnimation {
                if (preview.parent == null) return@postOnAnimation
                osrsMapPrototypePerformance.markPhase("terrain_preview_release_requested")
                (preview.parent as? ViewGroup)?.removeView(preview)
                osrsMapPrototypePerformance.markPhase("terrain_preview_removed_without_opacity")
                refreshControlRenderNodes()
            }
        } else {
            refreshControlRenderNodes()
        }
    }

    fun isInitialViewportReadyForTerrainHandoff(): Boolean {
        return initialViewportApplied && !initialViewportScheduled
    }

    fun loadingPreviewView(): View? {
        return binding.mapView.findViewById(R.id.map_prototype_loading_preview)
    }

    private fun refreshControlRenderNodes() {
        val controls = listOf(
            binding.prototypeProductControls,
            binding.floorControls,
            binding.prototypeOverview,
            binding.prototypeKeyPanel,
            binding.prototypeDebugControls
        )
        binding.root.postOnAnimation {
            fun invalidateTree(view: View) {
                when (view) {
                    is TextView -> {
                        val currentText = view.text
                        val currentHint = view.hint
                        view.text = null
                        view.text = currentText
                        view.hint = currentHint
                    }
                    is ImageView -> {
                        val drawable = view.drawable
                        view.setImageDrawable(null)
                        view.setImageDrawable(drawable)
                    }
                }
                view.refreshDrawableState()
                view.invalidate()
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) invalidateTree(view.getChildAt(index))
                }
            }
            controls.forEach { control ->
                control.requestLayout()
                invalidateTree(control)
            }
            binding.root.postInvalidateOnAnimation()
            osrsMapPrototypePerformance.markPhase("android_control_render_nodes_refreshed")
        }
    }

    fun saveState(outState: Bundle): Bundle {
        outState.putBoolean(STATE_LABELS, prototypeLabelsVisible)
        outState.putBoolean(STATE_POIS, prototypePoisVisible)
        outState.putBoolean(STATE_LINKS, prototypeLinksVisible)
        outState.putStringArrayList(
            STATE_HIGHLIGHTED_CATEGORIES,
            ArrayList(highlightedCategories.map { it.value }.sorted())
        )
        outState.putInt(STATE_REFERENCE_STOP, prototypeReferenceStopIndex)
        outState.putBoolean(STATE_KEY_PANEL, keyPanelVisible)
        outState.putBoolean(STATE_OVERVIEW, overviewVisible)
        outState.putString(STATE_SURFACE_ID, activeSurfaceId)
        currentSearchQuery = binding.prototypeSearchInput.text?.toString().orEmpty()
        outState.putString(STATE_SEARCH_QUERY, currentSearchQuery)
        outState.putString(STATE_STATUS_TEXT, statusText)
        outState.putString(STATE_STATUS_CONTENT_DESCRIPTION, statusContentDescription)
        outState.putBoolean(STATE_STATUS_VISIBLE, statusVisible)
        outState.putString(STATE_LAST_HIT_FEATURE, lastHitFeatureId)
        outState.putString(STATE_LAST_ACTION_DESCRIPTION, lastActionDescription)
        lastControlDurationMs?.let { outState.putDouble(STATE_CONTROL_DURATION, it) }
        mapProvider()?.cameraPosition?.let { camera ->
            camera.target?.let { target ->
                outState.putDouble(STATE_CAMERA_LAT, target.latitude)
                outState.putDouble(STATE_CAMERA_LON, target.longitude)
                outState.putDouble(STATE_CAMERA_ZOOM, camera.zoom)
                outState.putDouble(STATE_CAMERA_BEARING, camera.bearing)
                outState.putDouble(STATE_CAMERA_TILT, camera.tilt)
            }
        }
        syncCurrentNavigationCamera()
        currentNavigationSnapshot?.let { outState.putBundle(STATE_CURRENT_NAVIGATION, it.toBundle()) }
        outState.putBundle(STATE_HISTORY_V2, history.toBundle())
        return outState
    }

    fun viewportPaddingForHandoff(): osrsMapPrototypePadding {
        return osrsMapPrototypePadding(
            leftPx = 0,
            topPx = viewportPaddingTopPx,
            rightPx = 0,
            bottomPx = viewportPaddingBottomPx
        )
    }

    fun diagnosticsForTesting(): osrsMapPrototypeDiagnostics? {
        val maplibreMap = mapProvider() ?: return null
        ensureOverlayView(maplibreMap)
        if (!initialViewportApplied) {
            enable(maplibreMap)
        }
        applyCurrentVisibility()
        overlayView?.invalidate()
        val drawState = overlayView?.drawState().orEmpty()
        val rendered = drawState.values
            .groupBy({ it.feature.kind.value }, { it.feature.id })
            .mapValues { (_, ids) -> ids.distinct().sorted() }
        val source = osrsMapPrototypeOverlay.features
            .groupBy({ it.kind.value }, { it.id })
            .mapValues { (_, ids) -> ids.sorted() }
        val points = osrsMapPrototypeOverlay.features.associate { feature ->
            val point = maplibreMap.projection.toScreenLocation(feature.latLng())
            feature.id to osrsMapPrototypeScreenPoint(point.x, point.y)
        }
        val bounds = drawState.mapValues { (_, item) ->
            osrsMapPrototypeScreenBounds(
                item.bounds.left,
                item.bounds.top,
                item.bounds.right,
                item.bounds.bottom
            )
        }
        val virtualTargets = drawState.mapValues { (_, item) ->
            osrsMapPrototypeScreenBounds(
                item.actionBounds.left,
                item.actionBounds.top,
                item.actionBounds.right,
                item.actionBounds.bottom
            )
        }
        val safeLayout = safeLayout()
        val styleLayers = maplibreMap.style?.layers?.map { it.id }.orEmpty()
        return osrsMapPrototypeDiagnostics(
            cameraZoom = maplibreMap.cameraPosition.zoom,
            cameraLatitude = maplibreMap.cameraPosition.target?.latitude,
            cameraLongitude = maplibreMap.cameraPosition.target?.longitude,
            cameraBearing = maplibreMap.cameraPosition.bearing,
            cameraTilt = maplibreMap.cameraPosition.tilt,
            renderedFeatureIdsByKind = rendered,
            sourceFeatureIdsByKind = source,
            semanticLayersPresent = osrsMapPrototypeOverlay.semanticLayerIds.associateWith { overlayView != null },
            layerOrder = styleLayers + osrsMapPrototypeOverlay.semanticLayerIds,
            featureScreenPoints = points,
            renderedFeatureBounds = bounds,
            virtualTargetBounds = virtualTargets,
            mapContentBounds = osrsMapPrototypeScreenBounds(
                safeLayout.content.left,
                safeLayout.content.top,
                safeLayout.content.right,
                safeLayout.content.bottom
            ),
            viewportPaddingTopPx = viewportPaddingTopPx,
            viewportPaddingBottomPx = viewportPaddingBottomPx,
            semanticMetricsPx = overlayView?.semanticMetricsPx().orEmpty(),
            highlightedCategories = highlightedCategories.map { it.value }.sorted(),
            activeSurfaceId = activeSurfaceId,
            overviewVisible = overviewVisible,
            referenceStopPercent = currentReferenceStopPercent(maplibreMap.cameraPosition.zoom),
            featureActionMetadata = osrsMapPrototypeOverlay.features
                .filter { it.kind == osrsMapFeatureKind.MAP_LINK }
                .associate { feature ->
                    feature.id to "${feature.action.value}:${feature.destinationSurfaceId}:${feature.destinationSurface}"
                },
            lastControlDurationMs = lastControlDurationMs,
            lastHitFeatureId = lastHitFeatureId,
            lastActionDescription = lastActionDescription,
            searchQuery = binding.prototypeSearchInput.text?.toString().orEmpty(),
            statusText = statusText,
            statusContentDescription = statusContentDescription,
            accessibilityHostDescription = overlayView?.contentDescription?.toString().orEmpty(),
            accessibilityVisibleFeatureIds = overlayView?.accessibilityVisibleFeatureIds().orEmpty(),
            historyDepth = history.size,
            currentNavigationResultId = currentNavigationSnapshot?.resultId,
            elapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
    }

    fun setZoomForTesting(zoom: Double): Boolean {
        val maplibreMap = mapProvider() ?: return false
        maplibreMap.moveCamera(CameraUpdateFactory.zoomTo(zoom.coerceIn(0.0, 12.0)))
        overlayView?.invalidate()
        binding.prototypeOverview.cameraChanged()
        updateReferenceStopLabel()
        return true
    }

    fun panPrototypeAwayAndBackForTesting(): Boolean {
        val maplibreMap = mapProvider() ?: return false
        val original = maplibreMap.cameraPosition.toCameraState() ?: return false
        moveCameraTo(osrsMapPrototypeOverlay.gameToLatLng(3300.0, 3300.0), 8.1)
        moveCameraTo(
            LatLng(original.latitude, original.longitude),
            original.zoom,
            bearing = original.bearing,
            tilt = original.tilt
        )
        overlayView?.invalidate()
        return true
    }

    fun performFeatureActionForTesting(featureId: String): Boolean {
        val feature = osrsMapPrototypeOverlay.features.firstOrNull { it.id == featureId } ?: return false
        return performFeatureAction(feature, showToast = false)
    }

    fun hitFeatureIdForTesting(x: Float, y: Float): String? {
        return overlayView?.hitTest(PointF(x, y))?.id
    }

    fun performSearchForTesting(query: String): Boolean = performSearch(query, showKeyboard = false)

    fun toggleCategoryForTesting(category: osrsMapCategory): Boolean {
        if (category == osrsMapCategory.NONE) return false
        setCategoryHighlighted(category, category !in highlightedCategories)
        return true
    }

    fun setLayerVisibilityForTesting(
        labels: Boolean?,
        pois: Boolean?,
        links: Boolean?
    ): Boolean {
        labels?.let { prototypeLabelsVisible = it }
        pois?.let { prototypePoisVisible = it }
        links?.let { prototypeLinksVisible = it }
        applyCurrentVisibility()
        updateControlLabels()
        return true
    }

    fun setOverviewCenterForTesting(gameX: Double, gameY: Double): Boolean {
        val maplibreMap = mapProvider() ?: return false
        pushCurrentNavigation()
        moveCameraTo(
            osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY),
            maplibreMap.cameraPosition.zoom,
            syncNavigation = false
        )
        setSearchQuery("")
        showStatus(context.getString(R.string.map_semantic_overview_recentered))
        commitCurrentNavigation(resultId = NAVIGATION_RESULT_OVERVIEW)
        return true
    }

    fun setCameraForTesting(gameX: Double, gameY: Double, zoom: Double): Boolean {
        if (mapProvider() == null) return false
        moveCameraTo(osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY), zoom)
        return true
    }

    fun setCameraPoseForTesting(
        gameX: Double,
        gameY: Double,
        zoom: Double,
        bearing: Double,
        tilt: Double
    ): Boolean {
        if (mapProvider() == null) return false
        moveCameraTo(
            osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY),
            zoom,
            bearing = bearing,
            tilt = tilt
        )
        return true
    }

    fun restorePreviousCameraForTesting(): Boolean {
        return restorePreviousNavigation()
    }

    fun selectSurfaceForTesting(surfaceId: String): Boolean {
        val surface = osrsMapPrototypeOverlay.surfaceManifest.firstOrNull { it.id == surfaceId } ?: return false
        return selectSurface(surface)
    }

    private fun setupLayerControls() {
        binding.prototypeReferenceStop.setOnClickListener {
            measureControl("reference_stop") {
                val map = mapProvider() ?: return@measureControl
                val exactIndex = osrsMapPrototypeOverlay.referenceStops.indexOfFirst {
                    abs(it.mapLibreZoom - map.cameraPosition.zoom) <= REFERENCE_STOP_TOLERANCE
                }
                prototypeReferenceStopIndex = if (exactIndex >= 0) {
                    (exactIndex + 1) % osrsMapPrototypeOverlay.referenceStops.size
                } else {
                    osrsMapPrototypeOverlay.referenceStops.indexOf(osrsMapPrototypeOverlay.nearestReferenceStop(map.cameraPosition.zoom))
                }
                val stop = osrsMapPrototypeOverlay.referenceStops[prototypeReferenceStopIndex]
                val target = map.cameraPosition.target ?: osrsMapPrototypeOverlay.initialCenter()
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, stop.mapLibreZoom))
                overlayView?.invalidate()
                binding.prototypeOverview.cameraChanged()
                updateReferenceStopLabel()
            }
        }
        binding.prototypeToggleLabels.setOnClickListener {
            measureControl("labels") {
                prototypeLabelsVisible = !prototypeLabelsVisible
                applyCurrentVisibility()
                updateControlLabels()
            }
        }
        binding.prototypeTogglePois.setOnClickListener {
            measureControl("pois") {
                prototypePoisVisible = !prototypePoisVisible
                applyCurrentVisibility()
                updateControlLabels()
            }
        }
        binding.prototypeToggleLinks.setOnClickListener {
            measureControl("links") {
                prototypeLinksVisible = !prototypeLinksVisible
                applyCurrentVisibility()
                updateControlLabels()
            }
        }
    }

    private fun setupSearch() {
        binding.prototypeSearchInput.setAdapter(
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, osrsMapPrototypeOverlay.searchSuggestions)
        )
        binding.prototypeSearchInput.threshold = 0
        binding.prototypeSearchInput.setOnClickListener { binding.prototypeSearchInput.showDropDown() }
        binding.prototypeSearchInput.setOnItemClickListener { parent, _, position, _ ->
            performSearch(parent.getItemAtPosition(position).toString(), showKeyboard = false)
        }
        binding.prototypeSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.prototypeSearchInput.text?.toString().orEmpty(), showKeyboard = false)
                true
            } else {
                false
            }
        }
        binding.prototypeSearchButton.setOnClickListener {
            measureControl("search") {
                performSearch(binding.prototypeSearchInput.text?.toString().orEmpty(), showKeyboard = false)
            }
        }
    }

    private fun performSearch(query: String, showKeyboard: Boolean): Boolean {
        setSearchQuery(query)
        val result = osrsMapPrototypeOverlay.search(query)
        if (result == null) {
            showStatus(context.getString(R.string.map_semantic_search_no_result, query))
            if (!showKeyboard) hideKeyboard()
            L.d("$logTag: Prototype search miss: query=$query camera_preserved=true")
            return false
        }
        val map = mapProvider() ?: return false
        pushCurrentNavigation()
        moveCameraTo(
            result.latLng(),
            map.cameraPosition.zoom.coerceAtLeast(osrsMapPrototypeOverlay.initialZoom),
            syncNavigation = false
        )
        val displayName = result.name.removePrefix("SEM ")
        setSearchQuery(displayName)
        showStatus(context.getString(R.string.map_semantic_search_result, displayName))
        commitCurrentNavigation(resultId = result.id)
        if (!showKeyboard) hideKeyboard()
        L.d("$logTag: Prototype search hit: ${result.id} recenter=true")
        return true
    }

    private fun setupKeyPanel() {
        val checkBoxes = categoryCheckBoxes()
        for ((definition, checkBox) in checkBoxes) {
            checkBox.isChecked = definition.category in highlightedCategories
            checkBox.buttonTintList = categoryTint(definition.color)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                measureControl("key_${definition.category.value}") {
                    setCategoryHighlighted(definition.category, isChecked, updateCheckBox = false)
                }
            }
        }
        binding.prototypeKeyToggle.setOnClickListener {
            measureControl("key_panel") {
                if (isLandscape()) {
                    showLandscapeKeyMenu(binding.prototypeKeyToggle)
                } else {
                    keyPanelVisible = !keyPanelVisible
                    applyKeyPanelVisibility()
                    overlayView?.invalidate()
                }
            }
        }
    }

    private fun showLandscapeKeyMenu(anchor: View) {
        val menu = PopupMenu(context, anchor)
        osrsMapPrototypeOverlay.categoryManifest.forEachIndexed { index, definition ->
            menu.menu.add(0, index + 1, index, definition.title).apply {
                isCheckable = true
                isChecked = definition.category in highlightedCategories
            }
        }
        menu.setOnMenuItemClickListener { item ->
            val definition = osrsMapPrototypeOverlay.categoryManifest.getOrNull(item.itemId - 1)
                ?: return@setOnMenuItemClickListener false
            setCategoryHighlighted(definition.category, definition.category !in highlightedCategories)
            true
        }
        menu.show()
    }

    private fun applyKeyPanelVisibility() {
        binding.prototypeKeyPanel.visibility = if (keyPanelVisible && !isLandscape()) View.VISIBLE else View.GONE
    }

    private fun isLandscape(): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun setCategoryHighlighted(
        category: osrsMapCategory,
        highlighted: Boolean,
        updateCheckBox: Boolean = true
    ) {
        if (highlighted) highlightedCategories += category else highlightedCategories -= category
        if (updateCheckBox) {
            categoryCheckBoxes().firstOrNull { it.first.category == category }?.second?.isChecked = highlighted
        }
        applyCurrentVisibility()
    }

    private fun categoryCheckBoxes(): List<Pair<osrsMapCategoryDefinition, CheckBox>> {
        val byCategory = mapOf(
            osrsMapCategory.BANK to binding.prototypeKeyBanks,
            osrsMapCategory.TRANSPORTATION to binding.prototypeKeyTransport,
            osrsMapCategory.DUNGEON to binding.prototypeKeyDungeons,
            osrsMapCategory.GENERAL to binding.prototypeKeyPlaces
        )
        return osrsMapPrototypeOverlay.categoryManifest.mapNotNull { definition ->
            byCategory[definition.category]?.let { definition to it }
        }
    }

    private fun setupSurfaceSelector() {
        updateSurfaceSelectorAccessibility()
        binding.prototypeSurfaceSelector.setOnClickListener { anchor ->
            val menu = PopupMenu(context, anchor)
            osrsMapPrototypeOverlay.surfaceManifest.forEachIndexed { index, surface ->
                val title = if (surface.available) surface.title else "${surface.title} (unavailable)"
                menu.menu.add(0, index + 1, index, title).isEnabled = surface.available
            }
            menu.setOnMenuItemClickListener { item ->
                val surface = osrsMapPrototypeOverlay.surfaceManifest.getOrNull(item.itemId - 1)
                    ?: return@setOnMenuItemClickListener false
                selectSurface(surface)
            }
            menu.show()
        }
    }

    private fun selectSurface(surface: osrsMapSurfaceDefinition): Boolean {
        if (!surface.available) {
            showStatus(context.getString(R.string.map_semantic_surface_unavailable, surface.title, surface.availability))
            return false
        }
        activeSurfaceId = surface.id
        binding.prototypeSurfaceSelector.text = surface.title
        showStatus(surface.availability)
        updateSurfaceSelectorAccessibility()
        commitCurrentNavigation(resultId = "surface:${surface.id}")
        return true
    }

    private fun setupOverview() {
        binding.prototypeOverview.configure(
            mapProvider = mapProvider,
            onInteractionStarted = {
                if (!overviewInteractionActive) {
                    pushCurrentNavigation()
                    overviewInteractionActive = true
                }
            },
            onInteractionFinished = {
                overviewInteractionActive = false
            },
            onCenterRequested = { gameX, gameY ->
                val map = mapProvider() ?: return@configure
                moveCameraTo(
                    osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY),
                    map.cameraPosition.zoom,
                    syncNavigation = false
                )
                setSearchQuery("")
                val message = context.getString(R.string.map_semantic_overview_recentered)
                showStatus(message)
                commitCurrentNavigation(resultId = NAVIGATION_RESULT_OVERVIEW)
                binding.prototypeOverview.announceForAccessibility(message)
            }
        )
        binding.prototypeOverviewToggle.setOnClickListener {
            measureControl("overview") {
                overviewVisible = !overviewVisible
                binding.prototypeOverview.visibility = if (overviewVisible) View.VISIBLE else View.GONE
                updateControlLabels()
                overlayView?.invalidate()
            }
        }
    }

    private fun setupHistory() {
        binding.prototypeHistoryBack.setOnClickListener {
            measureControl("history_back") {
                restorePreviousNavigation()
            }
        }
    }

    private fun pushCurrentNavigation() {
        syncCurrentNavigationCamera()
        val state = currentNavigationSnapshot
            ?: snapshotFromCurrentCamera(
                query = currentSearchQuery,
                resultId = null,
                status = statusText,
                statusAccessibility = statusContentDescription
            )
            ?: return
        if (history.lastOrNull()?.approximatelyEquals(state) != true) {
            history += state.copy()
            while (history.size > MAX_HISTORY) history.removeAt(0)
        }
        updateHistoryButton()
    }

    private fun restorePreviousNavigation(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        applyNavigationSnapshot(previous)
        updateHistoryButton()
        return true
    }

    private fun applyNavigationSnapshot(snapshot: osrsMapNavigationSnapshot) {
        applyingNavigationSnapshot = true
        try {
            activeSurfaceId = snapshot.surfaceId
            moveCameraTo(
                target = LatLng(snapshot.latitude, snapshot.longitude),
                zoom = snapshot.zoom,
                bearing = snapshot.bearing,
                tilt = snapshot.tilt,
                syncNavigation = false
            )
            setSearchQuery(snapshot.query)
            statusText = snapshot.statusText
            statusContentDescription = snapshot.statusContentDescription
            statusVisible = snapshot.statusText != null
            applyStatusState()
            currentNavigationSnapshot = snapshot.copy()
            updateControlLabels()
        } finally {
            applyingNavigationSnapshot = false
        }
    }

    private fun commitCurrentNavigation(resultId: String?) {
        currentNavigationSnapshot = snapshotFromCurrentCamera(
            query = currentSearchQuery,
            resultId = resultId,
            status = statusText,
            statusAccessibility = statusContentDescription
        )
    }

    private fun syncCurrentNavigationCamera() {
        if (applyingNavigationSnapshot) return
        val camera = mapProvider()?.cameraPosition ?: return
        val target = camera.target ?: return
        currentNavigationSnapshot = currentNavigationSnapshot?.copy(
            latitude = target.latitude,
            longitude = target.longitude,
            zoom = camera.zoom,
            bearing = camera.bearing,
            tilt = camera.tilt,
            surfaceId = activeSurfaceId
        )
    }

    private fun snapshotFromCurrentCamera(
        query: String,
        resultId: String?,
        status: String?,
        statusAccessibility: String?
    ): osrsMapNavigationSnapshot? {
        val camera = mapProvider()?.cameraPosition ?: return null
        val target = camera.target ?: return null
        return osrsMapNavigationSnapshot(
            latitude = target.latitude,
            longitude = target.longitude,
            zoom = camera.zoom,
            bearing = camera.bearing,
            tilt = camera.tilt,
            surfaceId = activeSurfaceId,
            query = query,
            resultId = resultId,
            statusText = status,
            statusContentDescription = statusAccessibility
        )
    }

    private fun updateHistoryButton() {
        binding.prototypeHistoryBack.isEnabled = history.isNotEmpty()
        binding.prototypeHistoryBack.alpha = if (history.isNotEmpty()) 1f else 0.38f
    }

    private fun performFeatureAction(feature: osrsMapFeature, showToast: Boolean): Boolean {
        val map = mapProvider() ?: return false
        val handled = when (feature.action) {
            osrsMapAction.RECENTER -> {
                pushCurrentNavigation()
                moveCameraTo(feature.destinationLatLng(), map.cameraPosition.zoom, syncNavigation = false)
                setSearchQuery("")
                val status = context.getString(
                    R.string.map_semantic_feature_action,
                    feature.name,
                    "recenter -> ${feature.destinationSurface}"
                )
                showStatus(status)
                commitCurrentNavigation(resultId = feature.id)
                true
            }
            osrsMapAction.SWITCH_SURFACE -> {
                val surface = osrsMapPrototypeOverlay.surfaceManifest.firstOrNull {
                    it.id == feature.destinationSurfaceId
                }
                if (surface == null || !surface.available) {
                    showStatus(context.getString(R.string.map_semantic_feature_unavailable, feature.name))
                    false
                } else {
                    pushCurrentNavigation()
                    selectSurface(surface)
                }
            }
            osrsMapAction.UNKNOWN_PENDING_EVIDENCE -> {
                showStatus(context.getString(R.string.map_semantic_feature_unavailable, feature.name))
                false
            }
            osrsMapAction.NONE -> {
                showStatus(feature.name)
                true
            }
        }
        val message = "${feature.name}: action=${feature.action.value} destination_surface_id=${feature.destinationSurfaceId} destination=${feature.destinationSurface} handled=$handled"
        lastHitFeatureId = feature.id
        lastActionDescription = message
        L.d("$logTag: Prototype semantic hit: $message")
        if (showToast) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return handled
    }

    private fun moveCameraTo(
        target: LatLng,
        zoom: Double,
        bearing: Double? = null,
        tilt: Double? = null,
        syncNavigation: Boolean = true
    ) {
        val map = mapProvider() ?: return
        val current = map.cameraPosition
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(zoom.coerceIn(0.0, 12.0))
                    .bearing(bearing ?: current.bearing)
                    .tilt(tilt ?: current.tilt)
                    .build()
            )
        )
        overlayView?.invalidate()
        binding.prototypeOverview.cameraChanged()
        updateReferenceStopLabel()
        if (syncNavigation) syncCurrentNavigationCamera()
    }

    private fun ensureOverlayView(maplibreMap: MapLibreMap) {
        val existing = overlayView
        if (existing == null || existing.parent == null) {
            overlayView = osrsMapPrototypeOverlayView(
                context = context,
                mapProvider = mapProvider,
                safeLayoutProvider = ::safeLayout,
                onFeatureActivated = { feature ->
                    performFeatureAction(feature, showToast = false)
                },
                onSearchRequested = {
                    binding.prototypeSearchInput.requestFocus()
                    binding.prototypeSearchInput.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    binding.prototypeSearchInput.showDropDown()
                }
            ).also { view ->
                view.id = R.id.map_semantic_overlay
                binding.mapView.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
        overlayView?.bringToFront()
        installCameraListeners(maplibreMap)
    }

    private fun adoptLoadingPreview() {
        val preview = binding.root.rootView.findViewById<ImageView>(R.id.map_prototype_loading_preview) ?: return
        val parent = preview.parent as? ViewGroup
        if (parent !== binding.mapView) {
            parent?.removeView(preview)
            binding.mapView.addView(
                preview,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        preview.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun applyCurrentVisibility() {
        overlayView?.configureVisibility(
            labelsVisible = prototypeLabelsVisible,
            poisVisible = prototypePoisVisible,
            linksVisible = prototypeLinksVisible,
            highlightedCategories = if (prototypePoisVisible) highlightedCategories else emptySet()
        )
    }

    private fun installCameraListeners(maplibreMap: MapLibreMap) {
        if (cameraMoveListener != null) return
        cameraMoveListener = MapLibreMap.OnCameraMoveListener {
            overlayView?.invalidate()
            binding.prototypeOverview.cameraChanged()
        }
        cameraIdleListener = MapLibreMap.OnCameraIdleListener {
            overlayView?.invalidate()
            overlayView?.notifyAccessibilityCameraSettled()
            binding.prototypeOverview.cameraChanged()
            updateReferenceStopLabel()
            syncCurrentNavigationCamera()
            val camera = maplibreMap.cameraPosition
            L.d(
                "$logTag: CAMERA_STATE latitude=${camera.target?.latitude} " +
                    "longitude=${camera.target?.longitude} zoom=${camera.zoom}"
            )
        }
        cameraMoveListener?.let(maplibreMap::addOnCameraMoveListener)
        cameraIdleListener?.let(maplibreMap::addOnCameraIdleListener)
    }

    private fun removeCameraListeners(maplibreMap: MapLibreMap?) {
        cameraMoveListener?.let { maplibreMap?.removeOnCameraMoveListener(it) }
        cameraIdleListener?.let { maplibreMap?.removeOnCameraIdleListener(it) }
        cameraMoveListener = null
        cameraIdleListener = null
    }

    private fun installHitTesting(maplibreMap: MapLibreMap) {
        if (mapClickListener != null) return
        mapClickListener = MapLibreMap.OnMapClickListener { latLng ->
            val point = maplibreMap.projection.toScreenLocation(latLng)
            val feature = overlayView?.hitTest(point) ?: return@OnMapClickListener false
            performFeatureAction(feature, showToast = true)
            true
        }
        mapClickListener?.let(maplibreMap::addOnMapClickListener)
    }

    private fun removeHitTesting(maplibreMap: MapLibreMap?) {
        mapClickListener?.let { maplibreMap?.removeOnMapClickListener(it) }
        mapClickListener = null
    }

    private fun updateControlLabels() {
        binding.prototypeToggleLabels.setTextIfChanged(
            if (prototypeLabelsVisible) R.string.map_semantic_labels_on else R.string.map_semantic_labels_off
        )
        binding.prototypeTogglePois.setTextIfChanged(
            if (prototypePoisVisible) R.string.map_semantic_pois_on else R.string.map_semantic_pois_off
        )
        binding.prototypeToggleLinks.setTextIfChanged(
            if (prototypeLinksVisible) R.string.map_semantic_links_on else R.string.map_semantic_links_off
        )
        binding.prototypeOverviewToggle.setTextIfChanged(
            if (overviewVisible) R.string.map_semantic_overview_on else R.string.map_semantic_overview_off
        )
        osrsMapPrototypeOverlay.surfaceManifest.firstOrNull { it.id == activeSurfaceId }?.let {
            binding.prototypeSurfaceSelector.setTextIfChanged(it.title)
        }
        updateSurfaceSelectorAccessibility()
        updateReferenceStopLabel()
    }

    private fun updateReferenceStopLabel() {
        val zoom = mapProvider()?.cameraPosition?.zoom
        val exact = zoom?.let(::currentReferenceStopPercent)
        binding.prototypeReferenceStop.setTextIfChanged(if (exact != null) {
            context.getString(R.string.map_semantic_stop, exact)
        } else {
            val percent = zoom?.let(::zoomPercent) ?: 100
            context.getString(R.string.map_semantic_stop_custom, percent)
        })
    }

    private fun TextView.setTextIfChanged(resourceId: Int) {
        setTextIfChanged(context.getString(resourceId))
    }

    private fun TextView.setTextIfChanged(value: CharSequence) {
        if (!text.contentEquals(value)) text = value
    }

    private fun currentReferenceStopPercent(zoom: Double): Int? {
        return osrsMapPrototypeOverlay.referenceStops
            .firstOrNull { abs(it.mapLibreZoom - zoom) <= REFERENCE_STOP_TOLERANCE }
            ?.percent
    }

    private fun zoomPercent(zoom: Double): Int {
        val base = osrsMapPrototypeOverlay.referenceStops.first { it.percent == 100 }.mapLibreZoom
        return (100.0 * 2.0.pow(zoom - base)).roundToInt().coerceIn(1, 800)
    }

    private fun showStatus(message: String) {
        statusText = message
        statusContentDescription = message
        statusVisible = true
        applyStatusState()
    }

    private fun applyStatusState() {
        binding.prototypeStatus.text = statusText.orEmpty()
        binding.prototypeStatus.contentDescription = statusContentDescription ?: statusText.orEmpty()
        binding.prototypeStatus.visibility = if (statusVisible && statusText != null) View.VISIBLE else View.INVISIBLE
        scheduleMapViewportPadding()
    }

    private fun scheduleMapViewportPadding(
        maplibreMap: MapLibreMap? = null,
        cameraOverride: osrsMapCameraState? = null,
        onApplied: (() -> Unit)? = null
    ) {
        if (cameraOverride != null) pendingViewportCameraOverride = cameraOverride
        if (onApplied != null) {
            val prior = pendingViewportOnApplied
            pendingViewportOnApplied = {
                prior?.invoke()
                onApplied.invoke()
            }
        }
        val generation = ++viewportPaddingGeneration
        binding.prototypeProductControls.requestLayout()
        binding.root.requestLayout()
        binding.root.doOnPreDraw {
            if (generation != viewportPaddingGeneration) return@doOnPreDraw
            val activeMap = maplibreMap ?: mapProvider() ?: return@doOnPreDraw
            val camera = pendingViewportCameraOverride ?: activeMap.cameraPosition.toCameraState()
            val completion = pendingViewportOnApplied
            pendingViewportCameraOverride = null
            pendingViewportOnApplied = null
            applyMapViewportPadding(activeMap)
            camera?.let {
                activeMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(it.latitude, it.longitude))
                            .zoom(it.zoom)
                            .bearing(it.bearing)
                            .tilt(it.tilt)
                            .build()
                    )
                )
            }
            overlayView?.invalidate()
            binding.prototypeOverview.cameraChanged()
            completion?.invoke()
        }
    }

    private fun applyMapViewportPadding(maplibreMap: MapLibreMap) {
        val layout = safeLayout()
        val top = layout.content.top.roundToInt().coerceAtLeast(0)
        val bottom = (binding.mapView.height - layout.content.bottom)
            .roundToInt()
            .coerceAtLeast(0)
        viewportPaddingTopPx = top
        viewportPaddingBottomPx = bottom
        maplibreMap.setPadding(0, top, 0, bottom)
    }

    private fun initializeOrSyncNavigationSnapshot() {
        if (currentNavigationSnapshot == null) {
            currentNavigationSnapshot = snapshotFromCurrentCamera(
                query = currentSearchQuery,
                resultId = null,
                status = statusText,
                statusAccessibility = statusContentDescription
            )
        } else {
            syncCurrentNavigationCamera()
        }
    }

    private fun setSearchQuery(query: String) {
        currentSearchQuery = query
        binding.prototypeSearchInput.setText(query)
        binding.prototypeSearchInput.setSelection(binding.prototypeSearchInput.text.length)
    }

    private fun updateSurfaceSelectorAccessibility() {
        val active = osrsMapPrototypeOverlay.surfaceManifest.firstOrNull { it.id == activeSurfaceId }
        val unavailable = osrsMapPrototypeOverlay.surfaceManifest
            .filterNot { it.available }
            .joinToString(separator = "; ") { "${it.title}: ${it.availability}" }
        binding.prototypeSurfaceSelector.contentDescription = buildString {
            append(context.getString(R.string.map_semantic_surface_selector_action))
            active?.let { append(". Current: ${it.title}. ${it.availability}") }
            if (unavailable.isNotBlank()) append(". Unavailable surfaces: $unavailable")
        }
    }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.prototypeSearchInput.windowToken, 0)
        binding.prototypeSearchInput.clearFocus()
    }

    private fun measureControl(name: String, action: () -> Unit) {
        val start = SystemClock.elapsedRealtimeNanos()
        action()
        lastControlDurationMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        L.d("$logTag: Prototype control timing: control=$name duration_ms=$lastControlDurationMs")
    }

    private fun safeLayout(): osrsMapPrototypeSafeLayout {
        val density = context.resources.displayMetrics.density
        val inset = 8f * density
        val width = binding.mapView.width.toFloat().coerceAtLeast(1f)
        val height = binding.mapView.height.toFloat().coerceAtLeast(1f)
        val top = if (binding.prototypeProductControls.visibility == View.VISIBLE) {
            binding.prototypeProductControls.bottom.toFloat() + inset
        } else {
            inset
        }
        val bottom = if (binding.prototypeDebugControls.visibility == View.VISIBLE) {
            binding.prototypeDebugControls.top.toFloat() - inset
        } else {
            height - inset
        }
        val obstacles = listOf(
            binding.floorControls,
            binding.prototypeKeyPanel,
            binding.prototypeOverview
        ).filter { it.visibility == View.VISIBLE && it.width > 0 && it.height > 0 }
            .map { view ->
                RectF(
                    view.left - inset,
                    view.top - inset,
                    view.right + inset,
                    view.bottom + inset
                )
            }
        return osrsMapPrototypeSafeLayout(
            content = RectF(inset, top.coerceAtMost(bottom - inset), width - inset, bottom),
            obstacles = obstacles
        )
    }

    private fun categoryTint(color: Int): ColorStateList {
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(color, Color.rgb(95, 95, 95))
        )
    }

    private fun osrsMapFeature.latLng(): LatLng = osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY)

    private fun osrsMapFeature.destinationLatLng(): LatLng {
        return osrsMapPrototypeOverlay.gameToLatLng(destinationGameX ?: gameX, destinationGameY ?: gameY)
    }

    private fun CameraPosition.toCameraState(surfaceId: String = SURFACE_GIELINOR): osrsMapCameraState? {
        val target = target ?: return null
        return osrsMapCameraState(target.latitude, target.longitude, zoom, bearing, tilt, surfaceId)
    }

    private fun Bundle.cameraState(): osrsMapCameraState? {
        if (!containsKey(STATE_CAMERA_LAT) || !containsKey(STATE_CAMERA_LON) || !containsKey(STATE_CAMERA_ZOOM)) {
            return null
        }
        return osrsMapCameraState(
            getDouble(STATE_CAMERA_LAT),
            getDouble(STATE_CAMERA_LON),
            getDouble(STATE_CAMERA_ZOOM),
            getDouble(STATE_CAMERA_BEARING, 0.0),
            getDouble(STATE_CAMERA_TILT, 0.0),
            getString(STATE_SURFACE_ID) ?: SURFACE_GIELINOR
        )
    }

    private fun osrsMapNavigationSnapshot.toBundle(): Bundle = Bundle().apply {
        putDouble(NAV_LAT, latitude)
        putDouble(NAV_LON, longitude)
        putDouble(NAV_ZOOM, zoom)
        putDouble(NAV_BEARING, bearing)
        putDouble(NAV_TILT, tilt)
        putString(NAV_SURFACE, surfaceId)
        putString(NAV_QUERY, query)
        putString(NAV_RESULT, resultId)
        putString(NAV_STATUS, statusText)
        putString(NAV_STATUS_ACCESSIBILITY, statusContentDescription)
    }

    private fun Bundle.navigationSnapshot(): osrsMapNavigationSnapshot? {
        if (!containsKey(NAV_LAT) || !containsKey(NAV_LON) || !containsKey(NAV_ZOOM)) return null
        return osrsMapNavigationSnapshot(
            latitude = getDouble(NAV_LAT),
            longitude = getDouble(NAV_LON),
            zoom = getDouble(NAV_ZOOM),
            bearing = getDouble(NAV_BEARING, 0.0),
            tilt = getDouble(NAV_TILT, 0.0),
            surfaceId = getString(NAV_SURFACE) ?: SURFACE_GIELINOR,
            query = getString(NAV_QUERY).orEmpty(),
            resultId = getString(NAV_RESULT),
            statusText = getString(NAV_STATUS),
            statusContentDescription = getString(NAV_STATUS_ACCESSIBILITY)
        )
    }

    private fun List<osrsMapNavigationSnapshot>.toBundle(): Bundle = Bundle().apply {
        putInt(HISTORY_COUNT, size)
        forEachIndexed { index, snapshot -> putBundle("$HISTORY_ENTRY_PREFIX$index", snapshot.toBundle()) }
    }

    private fun Bundle.navigationHistory(): List<osrsMapNavigationSnapshot> {
        val state = getBundle(STATE_HISTORY_V2) ?: return emptyList()
        return (0 until state.getInt(HISTORY_COUNT, 0)).mapNotNull { index ->
            state.getBundle("$HISTORY_ENTRY_PREFIX$index")?.navigationSnapshot()
        }
    }

    private companion object {
        const val SURFACE_GIELINOR = "gielinor-surface"
        const val MAX_HISTORY = 8
        const val REFERENCE_STOP_TOLERANCE = 0.004
        const val STATE_LABELS = "prototype_labels"
        const val STATE_POIS = "prototype_pois"
        const val STATE_LINKS = "prototype_links"
        const val STATE_HIGHLIGHTED_CATEGORIES = "prototype_highlighted_categories"
        const val STATE_REFERENCE_STOP = "prototype_reference_stop"
        const val STATE_KEY_PANEL = "prototype_key_panel"
        const val STATE_OVERVIEW = "prototype_overview"
        const val STATE_SURFACE_ID = "prototype_surface_id"
        const val STATE_SEARCH_QUERY = "prototype_search_query"
        const val STATE_CAMERA_LAT = "prototype_camera_lat"
        const val STATE_CAMERA_LON = "prototype_camera_lon"
        const val STATE_CAMERA_ZOOM = "prototype_camera_zoom"
        const val STATE_CAMERA_BEARING = "prototype_camera_bearing"
        const val STATE_CAMERA_TILT = "prototype_camera_tilt"
        const val STATE_HISTORY_V2 = "prototype_navigation_history_v2"
        const val STATE_CURRENT_NAVIGATION = "prototype_current_navigation"
        const val STATE_STATUS_TEXT = "prototype_status_text"
        const val STATE_STATUS_CONTENT_DESCRIPTION = "prototype_status_content_description"
        const val STATE_STATUS_VISIBLE = "prototype_status_visible"
        const val STATE_LAST_HIT_FEATURE = "prototype_last_hit_feature"
        const val STATE_LAST_ACTION_DESCRIPTION = "prototype_last_action_description"
        const val STATE_CONTROL_DURATION = "prototype_control_duration"
        const val NAV_LAT = "lat"
        const val NAV_LON = "lon"
        const val NAV_ZOOM = "zoom"
        const val NAV_BEARING = "bearing"
        const val NAV_TILT = "tilt"
        const val NAV_SURFACE = "surface"
        const val NAV_QUERY = "query"
        const val NAV_RESULT = "result"
        const val NAV_STATUS = "status"
        const val NAV_STATUS_ACCESSIBILITY = "status_accessibility"
        const val HISTORY_COUNT = "count"
        const val HISTORY_ENTRY_PREFIX = "entry_"
        const val NAVIGATION_RESULT_OVERVIEW = "overview-recenter"
    }
}

data class osrsMapPrototypeSafeLayout(
    val content: RectF,
    val obstacles: List<RectF>
)

data class osrsMapPrototypeDrawnFeature(
    val feature: osrsMapFeature,
    val bounds: RectF,
    val actionBounds: RectF
)

private data class osrsMapPrototypeHitTarget(
    val feature: osrsMapFeature,
    val bounds: RectF,
    val center: PointF
)

internal fun osrsMapPrototypeAccessibleTargetBounds(
    visualBounds: RectF,
    safeContent: RectF,
    obstacles: List<RectF>,
    minimumSizePx: Float
): RectF? {
    fun RectF.screenBounds(): osrsMapPrototypeScreenBounds {
        return osrsMapPrototypeScreenBounds(left, top, right, bottom)
    }
    return osrsMapPrototypeAccessibleTargetBounds(
        visualBounds = visualBounds.screenBounds(),
        safeContent = safeContent.screenBounds(),
        obstacles = obstacles.map(RectF::screenBounds),
        minimumSizePx = minimumSizePx
    )?.let { RectF(it.left, it.top, it.right, it.bottom) }
}

internal fun osrsMapPrototypeAccessibleTargetBounds(
    visualBounds: osrsMapPrototypeScreenBounds,
    safeContent: osrsMapPrototypeScreenBounds,
    obstacles: List<osrsMapPrototypeScreenBounds>,
    minimumSizePx: Float
): osrsMapPrototypeScreenBounds? {
    fun contains(container: osrsMapPrototypeScreenBounds, item: osrsMapPrototypeScreenBounds): Boolean {
        return container.left <= item.left && container.top <= item.top &&
            container.right >= item.right && container.bottom >= item.bottom
    }
    fun intersects(left: osrsMapPrototypeScreenBounds, right: osrsMapPrototypeScreenBounds): Boolean {
        return left.left < right.right && left.right > right.left &&
            left.top < right.bottom && left.bottom > right.top
    }
    if (!contains(safeContent, visualBounds) || minimumSizePx <= 0f) return null
    val visualWidth = visualBounds.right - visualBounds.left
    val visualHeight = visualBounds.bottom - visualBounds.top
    val safeWidth = safeContent.right - safeContent.left
    val safeHeight = safeContent.bottom - safeContent.top
    val width = maxOf(visualWidth, minimumSizePx)
    val height = maxOf(visualHeight, minimumSizePx)
    if (width > safeWidth || height > safeHeight) return null
    val centerX = (visualBounds.left + visualBounds.right) / 2f
    val centerY = (visualBounds.top + visualBounds.bottom) / 2f
    val left = (centerX - width / 2f).coerceIn(safeContent.left, safeContent.right - width)
    val top = (centerY - height / 2f).coerceIn(safeContent.top, safeContent.bottom - height)
    val target = osrsMapPrototypeScreenBounds(left, top, left + width, top + height)
    if (!contains(target, visualBounds)) return null
    if (obstacles.any { intersects(target, it) }) return null
    return target
}

internal data class osrsMapCameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
    val tilt: Double = 0.0,
    val surfaceId: String = "gielinor-surface"
) {
    fun approximatelyEquals(other: osrsMapCameraState): Boolean {
        return abs(latitude - other.latitude) < 0.00001 &&
            abs(longitude - other.longitude) < 0.00001 &&
            abs(zoom - other.zoom) < 0.0001 &&
            abs(bearing - other.bearing) < 0.0001 &&
            abs(tilt - other.tilt) < 0.0001 &&
            surfaceId == other.surfaceId
    }
}

internal data class osrsMapNavigationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val surfaceId: String,
    val query: String,
    val resultId: String?,
    val statusText: String?,
    val statusContentDescription: String?
) {
    fun cameraState(): osrsMapCameraState = osrsMapCameraState(
        latitude,
        longitude,
        zoom,
        bearing,
        tilt,
        surfaceId
    )

    fun approximatelyEquals(other: osrsMapNavigationSnapshot): Boolean {
        return cameraState().approximatelyEquals(other.cameraState()) &&
            query == other.query &&
            resultId == other.resultId &&
            statusText == other.statusText &&
            statusContentDescription == other.statusContentDescription
    }
}

class osrsMapPrototypeOverlayView(
    context: Context,
    private val mapProvider: () -> MapLibreMap?,
    private val safeLayoutProvider: () -> osrsMapPrototypeSafeLayout,
    private val onFeatureActivated: (osrsMapFeature) -> Unit,
    private val onSearchRequested: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hitTargets = mutableListOf<osrsMapPrototypeHitTarget>()
    private val lastDrawState = linkedMapOf<String, osrsMapPrototypeDrawnFeature>()
    private var labelsVisible = true
    private var poisVisible = true
    private var linksVisible = true
    private var highlightedCategories: Set<osrsMapCategory> = emptySet()
    private var lastMetricsPx: Map<String, Double> = emptyMap()
    private var lastAccessibilitySignature = ""
    private var firstSemanticLayoutMeasured = false
    private val featureIdsByVirtualId = osrsMapPrototypeOverlay.features
        .mapIndexed { index, feature -> index + 1 to feature.id }
        .toMap()
    private val virtualIdsByFeatureId = featureIdsByVirtualId.entries.associate { (id, featureId) ->
        featureId to id
    }
    private val accessibilityHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val feature = hitTest(PointF(x, y)) ?: return INVALID_ID
            return virtualIdsByFeatureId[feature.id] ?: INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            lastDrawState.values
                .sortedWith(compareBy({ kindHitRank(it.feature.kind) }, { it.feature.priority }))
                .mapNotNullTo(virtualViewIds) { virtualIdsByFeatureId[it.feature.id] }
        }

        override fun onPopulateNodeForHost(node: AccessibilityNodeInfoCompat) {
            node.contentDescription = contentDescription
            node.className = View::class.java.name
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val featureId = featureIdsByVirtualId[virtualViewId]
            val drawn = featureId?.let(lastDrawState::get)
            if (drawn == null) {
                node.contentDescription = context.getString(R.string.map_semantic_accessibility_none)
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                node.isEnabled = false
                return
            }
            val feature = drawn.feature
            val kind = when (feature.kind) {
                osrsMapFeatureKind.LABEL -> "label"
                osrsMapFeatureKind.POI -> "point of interest"
                osrsMapFeatureKind.MAP_LINK -> "map link"
            }
            val action = when (feature.action) {
                osrsMapAction.RECENTER -> R.string.map_semantic_accessibility_recenter
                osrsMapAction.SWITCH_SURFACE -> R.string.map_semantic_accessibility_switch_surface
                osrsMapAction.UNKNOWN_PENDING_EVIDENCE -> R.string.map_semantic_accessibility_unavailable
                osrsMapAction.NONE -> R.string.map_semantic_accessibility_details
            }
            node.contentDescription = context.getString(
                R.string.map_semantic_accessibility_feature,
                feature.name.removePrefix("SEM "),
                kind,
                context.getString(action)
            )
            node.className = android.widget.Button::class.java.name
            node.setBoundsInParent(drawn.actionBounds.roundedAccessibilityBounds())
            node.isEnabled = true
            node.isClickable = true
            node.isFocusable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val featureId = featureIdsByVirtualId[virtualViewId] ?: return false
            val feature = lastDrawState[featureId]?.feature ?: return false
            onFeatureActivated(feature)
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }
    }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun performClick(): Boolean {
        super.performClick()
        onSearchRequested()
        return true
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    fun configureVisibility(
        labelsVisible: Boolean,
        poisVisible: Boolean,
        linksVisible: Boolean,
        highlightedCategories: Set<osrsMapCategory>
    ) {
        val changed = this.labelsVisible != labelsVisible ||
            this.poisVisible != poisVisible ||
            this.linksVisible != linksVisible ||
            this.highlightedCategories != highlightedCategories
        this.labelsVisible = labelsVisible
        this.poisVisible = poisVisible
        this.linksVisible = linksVisible
        this.highlightedCategories = highlightedCategories.toSet()
        if (changed) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!firstSemanticLayoutMeasured) {
            firstSemanticLayoutMeasured = true
            osrsMapPrototypePerformance.measureCpuSpan("semantic_first_source_filter_layout") {
                drawSemanticOverlay(canvas)
            }
        } else {
            drawSemanticOverlay(canvas)
        }
    }

    private fun drawSemanticOverlay(canvas: Canvas) {
        hitTargets.clear()
        lastDrawState.clear()
        val map = mapProvider() ?: return
        val safeLayout = safeLayoutProvider()
        if (safeLayout.content.width() <= 0f || safeLayout.content.height() <= 0f) return
        val metrics = metricsForZoom(map.cameraPosition.zoom)
        lastMetricsPx = mapOf(
            "label_text" to metrics.labelTextPx.toDouble(),
            "regional_label_text" to metrics.regionalLabelTextPx.toDouble(),
            "poi_radius" to metrics.poiRadiusPx.toDouble(),
            "link_radius" to metrics.linkRadiusPx.toDouble()
        )
        val occupied = safeLayout.obstacles.map(::RectF).toMutableList()
        val candidates = osrsMapPrototypeOverlay.features.associateWith { feature ->
            map.projection.toScreenLocation(feature.latLng())
        }

        if (poisVisible) {
            osrsMapPrototypeOverlay.features
                .filter { it.kind == osrsMapFeatureKind.POI }
                .sortedBy { it.priority }
                .forEach { feature ->
                    val point = candidates.getValue(feature)
                    val bounds = RectF(
                        point.x - metrics.poiRadiusPx - dp(5f),
                        point.y - metrics.poiRadiusPx - dp(5f),
                        point.x + metrics.poiRadiusPx + dp(5f),
                        point.y + metrics.poiRadiusPx + dp(5f)
                    )
                    if (!safeLayout.content.contains(bounds) || bounds.intersectsAny(safeLayout.obstacles)) return@forEach
                    if (!record(feature, bounds, safeLayout)) return@forEach
                    drawPoi(canvas, feature, point, metrics)
                    occupied += RectF(bounds).apply { inset(-dp(4f), -dp(4f)) }
                }
        }

        if (linksVisible) {
            osrsMapPrototypeOverlay.features
                .filter { it.kind == osrsMapFeatureKind.MAP_LINK }
                .sortedBy { it.priority }
                .forEach { feature ->
                    val point = candidates.getValue(feature)
                    if (!safeLayout.content.contains(point.x, point.y)) return@forEach
                    val bounds = mapLinkBounds(feature, point, metrics, safeLayout.content)
                    if (!safeLayout.content.contains(bounds) || bounds.intersectsAny(occupied)) return@forEach
                    if (!record(feature, bounds, safeLayout)) return@forEach
                    drawMapLink(canvas, feature, point, bounds, metrics)
                    occupied += RectF(bounds).apply { inset(-dp(4f), -dp(4f)) }
                }
        }

        if (labelsVisible) {
            osrsMapPrototypeOverlay.features
                .filter { it.kind == osrsMapFeatureKind.LABEL && shouldDrawLabel(it, map.cameraPosition.zoom) }
                .sortedBy { it.priority }
                .forEach { feature ->
                    val anchor = candidates.getValue(feature)
                    if (!safeLayout.content.contains(anchor.x, anchor.y)) return@forEach
                    val placement = labelPlacement(
                        feature,
                        anchor,
                        metrics,
                        safeLayout.content,
                        safeLayout.obstacles,
                        occupied
                    )
                        ?: return@forEach
                    if (!record(feature, placement, safeLayout)) return@forEach
                    drawLabel(canvas, feature, anchor, placement, metrics)
                    occupied += RectF(placement).apply { inset(-dp(3f), -dp(3f)) }
                }
        }

        updateAccessibilityState()
        val kinds = lastDrawState.values.map { it.feature.kind }.toSet()
        val completeForVisibleLayers =
            (!labelsVisible || osrsMapFeatureKind.LABEL in kinds) &&
                (!poisVisible || osrsMapFeatureKind.POI in kinds) &&
                (!linksVisible || osrsMapFeatureKind.MAP_LINK in kinds)
        if (completeForVisibleLayers && kinds.isNotEmpty()) {
            osrsMapPrototypePerformance.markFirstCompleteSemantics(lastDrawState.size)
        }
    }

    fun hitTest(point: PointF): osrsMapFeature? {
        return hitTargets
            .asSequence()
            .filter { it.bounds.contains(point.x, point.y) }
            .sortedWith(
                compareBy<osrsMapPrototypeHitTarget>(
                    { kindHitRank(it.feature.kind) },
                    { squaredDistance(it.center, point) },
                    { it.feature.priority }
                )
            )
            .firstOrNull()
            ?.feature
    }

    fun drawState(): Map<String, osrsMapPrototypeDrawnFeature> = lastDrawState.toMap()

    fun semanticMetricsPx(): Map<String, Double> = lastMetricsPx.toMap()

    fun accessibilityVisibleFeatureIds(): List<String> = lastDrawState.keys.toList()

    fun notifyAccessibilityCameraSettled() {
        accessibilityHelper.invalidateRoot()
    }

    private fun updateAccessibilityState() {
        val visible = lastDrawState.values
            .joinToString(separator = ", ") { it.feature.name.removePrefix("SEM ") }
            .ifBlank { context.getString(R.string.map_semantic_accessibility_none) }
        val searchablePlaces = osrsMapPrototypeOverlay.features
            .asSequence()
            .filter { it.kind == osrsMapFeatureKind.LABEL }
            .map { it.name.removePrefix("SEM ") }
            .distinct()
            .sorted()
            .joinToString(separator = ", ")
        contentDescription = context.getString(
            R.string.map_semantic_accessibility_host,
            visible,
            context.getString(
                if (labelsVisible) {
                    R.string.map_semantic_accessibility_labels_visible
                } else {
                    R.string.map_semantic_accessibility_labels_hidden
                }
            ),
            searchablePlaces
        )
        val signature = buildString {
            append(labelsVisible)
            append('|')
            append(poisVisible)
            append('|')
            append(linksVisible)
            append('|')
            append(lastDrawState.keys.joinToString(","))
        }
        if (signature != lastAccessibilitySignature) {
            lastAccessibilitySignature = signature
            accessibilityHelper.invalidateRoot()
        }
    }

    private fun RectF.roundedAccessibilityBounds(): Rect {
        return Rect(
            left.roundToInt(),
            top.roundToInt(),
            right.roundToInt().coerceAtLeast(left.roundToInt() + 1),
            bottom.roundToInt().coerceAtLeast(top.roundToInt() + 1)
        )
    }

    private fun record(
        feature: osrsMapFeature,
        bounds: RectF,
        safeLayout: osrsMapPrototypeSafeLayout
    ): Boolean {
        val stableBounds = RectF(bounds)
        val actionBounds = osrsMapPrototypeAccessibleTargetBounds(
            visualBounds = stableBounds,
            safeContent = safeLayout.content,
            obstacles = safeLayout.obstacles,
            minimumSizePx = kotlin.math.ceil(dp(48f).toDouble()).toFloat()
        ) ?: return false
        val target = osrsMapPrototypeHitTarget(
            feature = feature,
            bounds = actionBounds,
            center = PointF(actionBounds.centerX(), actionBounds.centerY())
        )
        if (!preservesExistingTargetCenters(target)) return false
        lastDrawState[feature.id] = osrsMapPrototypeDrawnFeature(feature, stableBounds, actionBounds)
        hitTargets += target
        return true
    }

    private fun preservesExistingTargetCenters(candidate: osrsMapPrototypeHitTarget): Boolean {
        val candidateRank = kindHitRank(candidate.feature.kind)
        return hitTargets.all { existing ->
            val existingRank = kindHitRank(existing.feature.kind)
            val candidateCenterStolen =
                existingRank < candidateRank &&
                    existing.bounds.contains(candidate.center.x, candidate.center.y)
            val existingCenterStolen =
                candidateRank < existingRank &&
                    candidate.bounds.contains(existing.center.x, existing.center.y)
            val indistinguishableCenters = squaredDistance(candidate.center, existing.center) < 0.25f
            !candidateCenterStolen && !existingCenterStolen && !indistinguishableCenters
        }
    }

    private fun drawPoi(
        canvas: Canvas,
        feature: osrsMapFeature,
        point: PointF,
        metrics: osrsMapPrototypeMetrics
    ) {
        val radius = metrics.poiRadiusPx
        paint.style = Paint.Style.FILL
        paint.color = categoryDefinition(feature.category)?.color ?: Color.rgb(249, 214, 107)
        canvas.drawCircle(point.x, point.y, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.3f)
        paint.color = Color.rgb(18, 18, 18)
        canvas.drawCircle(point.x, point.y, radius, paint)

        textPaint.textSize = (radius * 0.92f).coerceAtLeast(dp(9f))
        textPaint.style = Paint.Style.FILL
        textPaint.color = Color.BLACK
        val glyph = categoryDefinition(feature.category)?.glyph ?: "*"
        canvas.drawText(glyph, point.x, point.y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)

        if (feature.category in highlightedCategories) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4f)
            paint.color = Color.rgb(255, 37, 160)
            canvas.drawCircle(point.x, point.y, radius + dp(8f), paint)
            paint.strokeWidth = dp(2f)
            paint.color = Color.WHITE
            canvas.drawCircle(point.x, point.y, radius + dp(12f), paint)
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        feature: osrsMapFeature,
        anchor: PointF,
        bounds: RectF,
        metrics: osrsMapPrototypeMetrics
    ) {
        val text = feature.name.removePrefix("SEM ")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = Color.argb(180, 92, 47, 120)
        canvas.drawLine(anchor.x, anchor.y, bounds.centerX(), bounds.centerY(), paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(242, 255, 248, 189)
        canvas.drawRoundRect(bounds, dp(4f), dp(4f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = if (feature.labelTier == osrsMapLabelTier.REGIONAL) {
            Color.rgb(74, 20, 140)
        } else {
            Color.rgb(117, 24, 74)
        }
        canvas.drawRoundRect(bounds, dp(4f), dp(4f), paint)

        textPaint.textSize = if (feature.labelTier == osrsMapLabelTier.REGIONAL) {
            metrics.regionalLabelTextPx
        } else {
            metrics.labelTextPx
        }
        fitText(text, bounds.width() - dp(10f))
        textPaint.style = Paint.Style.FILL
        textPaint.color = paint.color
        canvas.drawText(text, bounds.centerX(), bounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
    }

    private fun labelPlacement(
        feature: osrsMapFeature,
        anchor: PointF,
        metrics: osrsMapPrototypeMetrics,
        safe: RectF,
        obstacles: List<RectF>,
        occupied: List<RectF>
    ): RectF? {
        val text = feature.name.removePrefix("SEM ")
        textPaint.textSize = if (feature.labelTier == osrsMapLabelTier.REGIONAL) {
            metrics.regionalLabelTextPx
        } else {
            metrics.labelTextPx
        }
        var textWidth = textPaint.measureText(text)
        val maximumWidth = (safe.width() * 0.58f).coerceAtLeast(dp(72f))
        if (textWidth > maximumWidth) {
            textPaint.textSize *= maximumWidth / textWidth
            textWidth = textPaint.measureText(text)
        }
        val width = textWidth + dp(12f)
        val height = (textPaint.descent() - textPaint.ascent()) + dp(8f)
        if (feature.hitOverlapFixture) {
            val left = anchor.x + metrics.poiRadiusPx * 0.25f
            val top = anchor.y - height / 2f
            val overlapFixture = RectF(left, top, left + width, top + height)
            return overlapFixture.takeIf { candidate ->
                safe.contains(candidate) && !candidate.intersectsAny(obstacles)
            }
        }
        val gap = dp(12f)
        val candidates = listOf(
            RectF(anchor.x - width / 2f, anchor.y - metrics.poiRadiusPx - gap - height, anchor.x + width / 2f, anchor.y - metrics.poiRadiusPx - gap),
            RectF(anchor.x - width / 2f, anchor.y + metrics.poiRadiusPx + gap, anchor.x + width / 2f, anchor.y + metrics.poiRadiusPx + gap + height),
            RectF(anchor.x + metrics.poiRadiusPx + gap, anchor.y - height / 2f, anchor.x + metrics.poiRadiusPx + gap + width, anchor.y + height / 2f),
            RectF(anchor.x - metrics.poiRadiusPx - gap - width, anchor.y - height / 2f, anchor.x - metrics.poiRadiusPx - gap, anchor.y + height / 2f)
        )
        return candidates.firstOrNull { candidate ->
            safe.contains(candidate) && !candidate.intersectsAny(occupied)
        }
    }

    private fun drawMapLink(
        canvas: Canvas,
        feature: osrsMapFeature,
        point: PointF,
        bounds: RectF,
        metrics: osrsMapPrototypeMetrics
    ) {
        val radius = metrics.linkRadiusPx
        val available = feature.action == osrsMapAction.RECENTER || feature.action == osrsMapAction.SWITCH_SURFACE
        paint.style = Paint.Style.FILL
        paint.color = if (available) Color.argb(242, 255, 245, 157) else Color.argb(242, 225, 225, 225)
        canvas.drawCircle(point.x, point.y, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.5f)
        paint.color = if (available) Color.rgb(13, 71, 161) else Color.rgb(85, 85, 85)
        canvas.drawCircle(point.x, point.y, radius, paint)
        if (available) {
            paint.strokeWidth = dp(3.2f)
            canvas.drawLine(point.x - radius * 0.42f, point.y + radius * 0.34f, point.x + radius * 0.42f, point.y - radius * 0.34f, paint)
            canvas.drawLine(point.x + radius * 0.16f, point.y - radius * 0.40f, point.x + radius * 0.46f, point.y - radius * 0.40f, paint)
            canvas.drawLine(point.x + radius * 0.46f, point.y - radius * 0.40f, point.x + radius * 0.46f, point.y - radius * 0.10f, paint)
        } else {
            textPaint.textSize = dp(15f)
            textPaint.style = Paint.Style.FILL
            textPaint.color = Color.rgb(65, 65, 65)
            canvas.drawText("?", point.x, point.y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }
        val label = if (available) feature.destinationSurface.substringBefore(' ') else "Unavailable"
        textPaint.textSize = metrics.linkTextPx
        fitText(label, bounds.width() - dp(8f))
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = dp(2.5f)
        textPaint.color = Color.WHITE
        val baseline = bounds.bottom - dp(4f) - textPaint.descent()
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
        textPaint.style = Paint.Style.FILL
        textPaint.color = paint.color
        canvas.drawText(label, bounds.centerX(), baseline, textPaint)
    }

    private fun mapLinkBounds(
        feature: osrsMapFeature,
        point: PointF,
        metrics: osrsMapPrototypeMetrics,
        safe: RectF
    ): RectF {
        val label = if (feature.action == osrsMapAction.RECENTER || feature.action == osrsMapAction.SWITCH_SURFACE) {
            feature.destinationSurface.substringBefore(' ')
        } else {
            "Unavailable"
        }
        textPaint.textSize = metrics.linkTextPx
        val width = maxOf(metrics.linkRadiusPx * 2f + dp(8f), textPaint.measureText(label) + dp(10f))
            .coerceAtMost(safe.width() * 0.48f)
        val height = metrics.linkRadiusPx * 2f + metrics.linkTextPx + dp(13f)
        val left = point.x - width / 2f
        val top = point.y - metrics.linkRadiusPx - dp(4f)
        return RectF(left, top, left + width, top + height)
    }

    private fun shouldDrawLabel(feature: osrsMapFeature, zoom: Double): Boolean {
        if (zoom !in feature.minZoom..feature.maxZoom) return false
        return when (feature.labelTier) {
            osrsMapLabelTier.REGIONAL -> zoom <= 7.82
            osrsMapLabelTier.LOCAL -> zoom >= 6.62
            osrsMapLabelTier.NONE -> false
        }
    }

    private fun metricsForZoom(zoom: Double): osrsMapPrototypeMetrics {
        val t = ((zoom - 5.9) / 3.1).coerceIn(0.0, 1.0).toFloat()
        return osrsMapPrototypeMetrics(
            labelTextPx = dp(10.5f + 3.2f * t),
            regionalLabelTextPx = dp(11.8f + 2.4f * t),
            poiRadiusPx = dp(8.5f + 8.0f * t),
            linkRadiusPx = dp(11f + 5.5f * t),
            linkTextPx = dp(8.8f + 1.8f * t)
        )
    }

    private fun fitText(text: String, maximumWidth: Float) {
        val measured = textPaint.measureText(text)
        if (measured > maximumWidth && measured > 0f) {
            textPaint.textSize = (textPaint.textSize * maximumWidth / measured).coerceAtLeast(dp(8f))
        }
    }

    private fun categoryDefinition(category: osrsMapCategory): osrsMapCategoryDefinition? {
        return osrsMapPrototypeOverlay.categoryManifest.firstOrNull { it.category == category }
    }

    private fun osrsMapFeature.latLng(): LatLng = osrsMapPrototypeOverlay.gameToLatLng(gameX, gameY)

    private fun RectF.intersectsAny(rectangles: List<RectF>): Boolean {
        return rectangles.any { other -> RectF.intersects(this, other) }
    }

    private fun RectF.contains(other: RectF): Boolean {
        return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
    }

    private fun kindHitRank(kind: osrsMapFeatureKind): Int = when (kind) {
        osrsMapFeatureKind.MAP_LINK -> 0
        osrsMapFeatureKind.POI -> 1
        osrsMapFeatureKind.LABEL -> 2
    }

    private fun squaredDistance(left: PointF, right: PointF): Float {
        val dx = left.x - right.x
        val dy = left.y - right.y
        return dx * dx + dy * dy
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

private data class osrsMapPrototypeMetrics(
    val labelTextPx: Float,
    val regionalLabelTextPx: Float,
    val poiRadiusPx: Float,
    val linkRadiusPx: Float,
    val linkTextPx: Float
)
