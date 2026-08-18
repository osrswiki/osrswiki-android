package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import com.omiyawaki.osrswiki.util.log.L
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToLong

/**
 * MapFragment that uses AndroidMapPreloader for background preloading
 * This version is designed to work with standard Fragment navigation patterns
 * (ViewPager2, FragmentTransaction show/hide/replace) while maintaining instant tile display
 */
class StandardNavigationMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var attachedMapView: MapView? = null
    private var mapOwnerToken: Long? = null
    private var map: MapLibreMap? = null
    private var prototypeController: osrsMapPrototypeControllerContract? = null
    private var terrainRenderListener: MapView.OnDidFinishRenderingFrameWithStatsListener? = null
    private var terrainPresented = false
    private var mapLibreFrameCount = 0
    private var previewSnapshotInFlight = false
    private var previewCapturePending = false
    private var previewCaptureGeneration = 0
    private var previewCaptureRunnable: Runnable? = null
    private var previewCameraIdleListener: MapLibreMap.OnCameraIdleListener? = null
    private var deferredAttachRunnable: Runnable? = null
    private var lastPersistedPreviewSignature: PrototypePreviewSignature? = null
    private val logTag = "StandardMapFragment"

    private var currentFloor = 0
    private val maxFloor = 3

    private data class PrototypePreviewSignature(
        val latitudeE6: Long,
        val longitudeE6: Long,
        val zoomE6: Long,
        val bearingE4: Long,
        val tiltE4: Long,
        val widthPx: Int,
        val heightPx: Int,
        val floor: Int
    )
    
    companion object {
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LON = "arg_lon"
        private const val ARG_ZOOM = "arg_zoom"
        private const val ARG_PLANE = "arg_plane"
        private const val ARG_DEFER_INITIAL_ATTACH = "arg_defer_initial_attach"
        private const val ARG_ENABLE_SEMANTIC_PROTOTYPE = "arg_enable_semantic_prototype"
        private const val ARG_RESTORED_FRAGMENT_STATE = "arg_restored_fragment_state"
        private const val STATE_CURRENT_FLOOR = "state_current_floor"
        private const val STATE_SEMANTIC_PROTOTYPE = "state_semantic_prototype"
        private const val PROTOTYPE_MAP_START_DELAY_MS = 500L
        private const val TERRAIN_HANDOFF_OPAQUE_HOLD_MS = 180L
        private const val PREVIEW_CAPTURE_IDLE_DELAY_MS = 1_800L

        fun newInstance(
            lat: String?,
            lon: String?,
            zoom: String?,
            plane: String?,
            deferInitialAttach: Boolean = false,
            enableSemanticPrototype: Boolean = false,
            restoredFragmentState: Bundle? = null
        ): StandardNavigationMapFragment {
            return StandardNavigationMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LAT, lat)
                    putString(ARG_LON, lon)
                    putString(ARG_ZOOM, zoom)
                    putString(ARG_PLANE, plane)
                    putBoolean(ARG_DEFER_INITIAL_ATTACH, deferInitialAttach)
                    putBoolean(ARG_ENABLE_SEMANTIC_PROTOTYPE, enableSemanticPrototype)
                    putBundle(ARG_RESTORED_FRAGMENT_STATE, restoredFragmentState)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("$logTag: LIFECYCLE: onCreate")
        val restoredFragmentState = arguments?.getBundle(ARG_RESTORED_FRAGMENT_STATE)
        currentFloor = savedInstanceState?.getInt(STATE_CURRENT_FLOOR)
            ?: restoredFragmentState?.getInt(STATE_CURRENT_FLOOR)
            ?: arguments?.getString(ARG_PLANE)?.toIntOrNull()
            ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("$logTag: LIFECYCLE: onCreateView")
        _binding = osrsMapPrototypePerformance.measureCpuSpan("fragment_layout_inflation") {
            FragmentMapBinding.inflate(inflater, container, false)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("$logTag: LIFECYCLE: onViewCreated")
        
        // Set up floor controls
        setupFloorControls()
        setupMarkerInfoControl()
        osrsMapPrototypePerformance.measureCpuSpan(
            "semantic_state_restore_and_controls",
            "restored=${savedInstanceState != null}"
        ) {
            prototypeController = osrsMapPrototypeBridge.createController(
                binding = binding,
                context = requireContext(),
                mapProvider = { map },
                logTag = logTag,
                restoredState = savedInstanceState?.getBundle(STATE_SEMANTIC_PROTOTYPE)
                    ?: arguments?.getBundle(ARG_RESTORED_FRAGMENT_STATE)
                        ?.getBundle(STATE_SEMANTIC_PROTOTYPE)
            )
            prototypeController?.prepare()
        }
        
        if (arguments?.getBoolean(ARG_DEFER_INITIAL_ATTACH) == true) {
            showMapLoading()
        } else if (
            semanticPrototypePolicy().showControls &&
            prototypeController?.loadingPreviewView() != null
        ) {
            osrsMapPrototypePerformance.markPhase(
                "maplibre_start_deferred",
                "delay_ms=$PROTOTYPE_MAP_START_DELAY_MS preview_visible=true"
            )
            val deferredAttach = Runnable {
                deferredAttachRunnable = null
                if (_binding == null || !isAdded || isHidden) return@Runnable
                osrsMapPrototypePerformance.markPhase("maplibre_deferred_start")
                attachMapForVisibleNavigation()
            }
            deferredAttachRunnable = deferredAttach
            binding.mapView.postDelayed(deferredAttach, PROTOTYPE_MAP_START_DELAY_MS)
        } else {
            attachMapForVisibleNavigation()
        }
    }

    private fun attachSharedMapView() {
        val preloader = AndroidMapPreloader.getInstance()
        
        if (preloader.isMapReady) {
            L.d("$logTag: ✅ Using shared map instance - instant display!")
            
            // Clear the container first  
            clearMapContainerPreservingPreview()
            
            // Attach shared map to our container
            val attachment = osrsMapPrototypePerformance.measureCpuSpan("maplibre_visible_attachment") {
                preloader.attachToMainMapContainer(binding.mapView)
            }
            val sharedMapView = attachment?.mapView
            attachedMapView = sharedMapView
            mapOwnerToken = attachment?.ownerToken
            
            if (sharedMapView != null) {
                syncAttachedMapLifecycle(sharedMapView)
                applyMapAccessibilityDescription(sharedMapView)
                installTerrainRenderListener(sharedMapView)
                prototypeController?.onMapViewAttached()
                // Get reference to the MapLibreMap instance
                sharedMapView.getMapAsync { maplibreMap ->
                    if (!isCurrentMapOwner(sharedMapView)) return@getMapAsync
                    this.map = maplibreMap
                    configurePrototypeForAttachedMap()
                    L.d("$logTag: Shared map attached and ready!")
                }
            }
            
        } else {
            L.w("$logTag: Shared map not ready - waiting for preloader...")
            showMapLoading()
            waitForPreloaderAndAttach()
        }
    }

    fun attachMapForVisibleNavigation() {
        if (_binding == null || attachedMapView != null) {
            return
        }
        attachSharedMapView()
    }
    
    private fun waitForPreloaderAndAttach() {
        val preloader = AndroidMapPreloader.getInstance()
        
        L.d("$logTag: Setting up preloader observers...")
        
        // Check if already ready (in case preloader finished before observer setup)
        if (preloader.isMapReady) {
            L.d("$logTag: Preloader already ready, attaching immediately")
            attachSharedMapViewNow(preloader)
            return
        }
        
        preloader.preloadState.observe(viewLifecycleOwner) { state ->
            when (state) {
                AndroidMapPreloader.PreloadState.Idle -> {
                    showMapLoading()
                    if (isAdded) preloader.requestPreload(requireContext())
                }
                is AndroidMapPreloader.PreloadState.Loading -> showMapLoading()
                AndroidMapPreloader.PreloadState.Ready -> {
                    L.d("$logTag: Preloader state ready, attaching shared map")
                    attachSharedMapViewNow(preloader)
                }
                is AndroidMapPreloader.PreloadState.Failed -> {
                    L.e("$logTag: Preloader failed: ${state.message}", state.cause)
                    showMapUnavailable(getString(R.string.map_unavailable_message))
                }
            }
        }
    }
    
    private fun attachSharedMapViewNow(preloader: AndroidMapPreloader) {
        hideMapUnavailable()

        // Clear the container first
        clearMapContainerPreservingPreview()
        
        // Attach shared map to our container
        val attachment = osrsMapPrototypePerformance.measureCpuSpan("maplibre_visible_attachment") {
            preloader.attachToMainMapContainer(binding.mapView)
        }
        val sharedMapView = attachment?.mapView
        attachedMapView = sharedMapView
        mapOwnerToken = attachment?.ownerToken
        
        if (sharedMapView != null) {
            syncAttachedMapLifecycle(sharedMapView)
            applyMapAccessibilityDescription(sharedMapView)
            installTerrainRenderListener(sharedMapView)
            prototypeController?.onMapViewAttached()
            // Get reference to the MapLibreMap instance
            sharedMapView.getMapAsync { maplibreMap ->
                if (!isCurrentMapOwner(sharedMapView)) return@getMapAsync
                this.map = maplibreMap
                configurePrototypeForAttachedMap()
                L.d("$logTag: Shared map attached successfully!")
            }
        } else {
            L.e("$logTag: Failed to attach shared map - sharedMapView is null")
            showMapUnavailable(getString(R.string.map_unavailable_message))
        }
    }

    override fun onStart() { 
        L.d("$logTag: LIFECYCLE: onStart")
        super.onStart() 
        mapOwnerToken?.let(AndroidMapPreloader.getInstance()::startForOwner)
    }
    
    override fun onResume() { 
        L.d("$logTag: LIFECYCLE: onResume")
        super.onResume() 
        if (!isHidden) mapOwnerToken?.let(AndroidMapPreloader.getInstance()::resumeForOwner)
        schedulePrototypeTerrainPreviewCapture()
    }
    
    override fun onPause() { 
        L.d("$logTag: LIFECYCLE: onPause - MapView paused")
        cancelPrototypeTerrainPreviewCapture()
        mapOwnerToken?.let(AndroidMapPreloader.getInstance()::pauseForOwner)
        super.onPause()
    }
    
    override fun onStop() { 
        L.d("$logTag: LIFECYCLE: onStop - MapView stopped")  
        mapOwnerToken?.let(AndroidMapPreloader.getInstance()::stopForOwner)
        super.onStop()
    }

    override fun onDestroyView() {
        L.d("$logTag: LIFECYCLE: onDestroyView")
        cancelDeferredAttach()
        prototypeController?.disable(map)
        removeTerrainRenderListener()
        removePrototypePreviewCaptureListener()
        
        // CRITICAL FIX: Use proper detachment method to return MapView to shared container
        // This prevents the "child already has a parent" crash on subsequent attachments
        if (attachedMapView != null) {
            L.d("$logTag: Properly detaching shared map using AndroidMapPreloader.detachFromMainMapContainer()")
            mapOwnerToken?.let(AndroidMapPreloader.getInstance()::detachFromMainMapContainer)
            attachedMapView = null
            mapOwnerToken = null
            L.d("$logTag: ✅ Shared map properly detached and returned to off-screen container")
        } else {
            L.d("$logTag: No attached map to detach")
        }
        
        _binding = null
        map = null
        prototypeController = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_FLOOR, currentFloor)
        prototypeController?.saveState(Bundle())?.let {
            outState.putBundle(STATE_SEMANTIC_PROTOTYPE, it)
        }
        super.onSaveInstanceState(outState)
    }

    fun prototypePersistenceState(): Bundle? {
        if (prototypeController == null) return null
        return Bundle().apply {
            putInt(STATE_CURRENT_FLOOR, currentFloor)
            putBundle(STATE_SEMANTIC_PROTOTYPE, prototypeController?.saveState(Bundle()))
        }
    }

    private fun configurePrototypeForAttachedMap() {
        if (!isCurrentMapOwner()) return
        val maplibreMap = map ?: return
        // The fragment owns the selected floor across Activity and process recreation. A newly
        // initialized preloader starts at floor 0, so synchronize the actual raster layers before
        // exposing restored controls or semantic content.
        AndroidMapPreloader.getInstance().updateFloor(currentFloor)
        L.d("$logTag: RASTER_FLOOR_STATE floor=$currentFloor")
        if (semanticPrototypePolicy().installOverlay) {
            prototypeController?.enable(maplibreMap)
            installPrototypePreviewCaptureListener(maplibreMap)
        } else {
            removePrototypePreviewCaptureListener()
            prototypeController?.disable(maplibreMap)
        }
    }

    private fun syncAttachedMapLifecycle(mapView: MapView) {
        if (!isCurrentMapOwner(mapView)) return
        val token = mapOwnerToken ?: return
        val preloader = AndroidMapPreloader.getInstance()
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            preloader.startForOwner(token)
        }
        if (!isHidden && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            preloader.resumeForOwner(token)
        }
    }

    private fun clearMapContainerPreservingPreview() {
        val preview = prototypeController?.loadingPreviewView()
        (preview?.parent as? ViewGroup)?.removeView(preview)
        binding.mapView.removeAllViews()
        preview?.let { binding.mapView.addView(it) }
    }

    private fun installTerrainRenderListener(mapView: MapView) {
        removeTerrainRenderListener()
        mapLibreFrameCount = 0
        terrainPresented = false
        val ownerToken = mapOwnerToken ?: return
        val listener = MapView.OnDidFinishRenderingFrameWithStatsListener { fully, stats ->
            if (!isCurrentMapOwner(mapView, ownerToken, requireResumed = true)) {
                return@OnDidFinishRenderingFrameWithStatsListener
            }
            mapLibreFrameCount++
            if (mapLibreFrameCount <= 24 || (fully && !terrainPresented)) {
                osrsMapPrototypePerformance.recordMapLibreFrame(
                    fully = fully,
                    encodingMs = stats.encodingTime,
                    renderingMs = stats.renderingTime,
                    drawCalls = stats.numDrawCalls
                )
            }
            if (
                fully &&
                !terrainPresented &&
                map != null &&
                prototypeController?.isInitialViewportReadyForTerrainHandoff() == true
            ) {
                terrainPresented = true
                val handoff = Runnable {
                    if (!isCurrentMapOwner(mapView, ownerToken, requireResumed = true)) return@Runnable
                    prototypeController?.onFirstTerrainFrame(stats.encodingTime, stats.renderingTime)
                    binding.root.postInvalidateOnAnimation()
                    schedulePrototypeTerrainPreviewCapture()
                }
                mapView.postDelayed(handoff, TERRAIN_HANDOFF_OPAQUE_HOLD_MS)
            }
        }
        terrainRenderListener = listener
        mapView.addOnDidFinishRenderingFrameListener(listener)
    }

    private fun removeTerrainRenderListener() {
        val mapView = attachedMapView ?: return
        terrainRenderListener?.let(mapView::removeOnDidFinishRenderingFrameListener)
        terrainRenderListener = null
    }

    fun capturePrototypeTerrainPreviewForPersistence(): Boolean {
        if (
            !semanticPrototypePolicy().showControls ||
            !isResumed ||
            !isCurrentMapOwner(requireResumed = true)
        ) {
            return false
        }
        if (previewSnapshotInFlight) {
            previewCapturePending = true
            return false
        }
        val maplibreMap = map ?: return false
        val signature = prototypePreviewSignature(maplibreMap) ?: return false
        val handoffState = prototypeHandoffState(maplibreMap) ?: return false
        val host = activity as? PrototypeTerrainPreviewHost ?: return false
        if (signature == lastPersistedPreviewSignature) {
            osrsMapPrototypePerformance.markPhase(
                "persisted_terrain_preview_snapshot_unchanged_skipped",
                "generation=$previewCaptureGeneration"
            )
            return false
        }
        val callbackGeneration = previewCaptureGeneration
        val ownerToken = mapOwnerToken ?: return false
        val persistedGeneration = host.reservePrototypeTerrainGeneration()
        previewSnapshotInFlight = true
        osrsMapPrototypePerformance.markPhase(
            "persisted_terrain_preview_snapshot_requested",
            "generation=$persistedGeneration owner=$ownerToken"
        )
        maplibreMap.snapshot { bitmap ->
            previewSnapshotInFlight = false
            val canPersist =
                callbackGeneration == previewCaptureGeneration &&
                    isCurrentMapOwner(ownerToken = ownerToken, requireResumed = true) &&
                    prototypePreviewSignature(maplibreMap) == signature &&
                    !bitmap.isRecycled
            if (canPersist) {
                osrsMapPrototypePerformance.markPhase(
                    "persisted_terrain_preview_snapshot_complete",
                    "generation=$persistedGeneration width=${bitmap.width} height=${bitmap.height}"
                )
                host.onPrototypeTerrainPreview(
                    osrsMapPrototypeTerrainCapture(
                        generation = persistedGeneration,
                        handoffState = handoffState,
                        bitmap = bitmap
                    )
                ) { committed ->
                    if (committed) {
                        lastPersistedPreviewSignature = signature
                        osrsMapPrototypePerformance.markPhase(
                            "persisted_terrain_preview_generation_committed",
                            "generation=$persistedGeneration"
                        )
                    } else {
                        osrsMapPrototypePerformance.markPhase(
                            "persisted_terrain_preview_generation_rejected",
                            "generation=$persistedGeneration"
                        )
                    }
                }
            } else {
                if (!bitmap.isRecycled) bitmap.recycle()
                osrsMapPrototypePerformance.markPhase(
                    "persisted_terrain_preview_snapshot_discarded",
                    "generation=$persistedGeneration callback_generation=$callbackGeneration " +
                        "current_callback_generation=$previewCaptureGeneration owner=$ownerToken"
                )
            }
            if (previewCapturePending) {
                previewCapturePending = false
                schedulePrototypeTerrainPreviewCapture()
            }
        }
        return true
    }

    fun prototypeHandoffState(): osrsMapPrototypeHandoffState? {
        return map?.let(::prototypeHandoffState)
    }

    private fun prototypeHandoffState(
        maplibreMap: MapLibreMap
    ): osrsMapPrototypeHandoffState? {
        val target = maplibreMap.cameraPosition.target ?: return null
        val mapView = attachedMapView ?: return null
        if (mapView.width <= 0 || mapView.height <= 0) return null
        val state = prototypePersistenceState() ?: return null
        val camera = maplibreMap.cameraPosition
        return osrsMapPrototypeHandoffState(
            fragmentState = state,
            camera = osrsMapPrototypeCameraDescriptor(
                latitude = target.latitude,
                longitude = target.longitude,
                zoom = camera.zoom,
                bearing = camera.bearing,
                tilt = camera.tilt
            ),
            floor = currentFloor,
            viewportWidthPx = mapView.width,
            viewportHeightPx = mapView.height,
            padding = prototypeController?.viewportPaddingForHandoff()
                ?: osrsMapPrototypePadding(0, 0, 0, 0)
        )
    }

    private fun prototypePreviewSignature(maplibreMap: MapLibreMap): PrototypePreviewSignature? {
        val target = maplibreMap.cameraPosition.target ?: return null
        val mapView = attachedMapView ?: return null
        return PrototypePreviewSignature(
            latitudeE6 = (target.latitude * 1_000_000.0).roundToLong(),
            longitudeE6 = (target.longitude * 1_000_000.0).roundToLong(),
            zoomE6 = (maplibreMap.cameraPosition.zoom * 1_000_000.0).roundToLong(),
            bearingE4 = (maplibreMap.cameraPosition.bearing * 10_000.0).roundToLong(),
            tiltE4 = (maplibreMap.cameraPosition.tilt * 10_000.0).roundToLong(),
            widthPx = mapView.width,
            heightPx = mapView.height,
            floor = currentFloor
        )
    }

    private fun installPrototypePreviewCaptureListener(maplibreMap: MapLibreMap) {
        removePrototypePreviewCaptureListener()
        previewCameraIdleListener = MapLibreMap.OnCameraIdleListener {
            if (isCurrentMapOwner(requireResumed = true)) {
                schedulePrototypeTerrainPreviewCapture()
            }
        }.also(maplibreMap::addOnCameraIdleListener)
        schedulePrototypeTerrainPreviewCapture()
    }

    private fun removePrototypePreviewCaptureListener() {
        previewCameraIdleListener?.let { map?.removeOnCameraIdleListener(it) }
        previewCameraIdleListener = null
        cancelPrototypeTerrainPreviewCapture()
    }

    private fun schedulePrototypeTerrainPreviewCapture() {
        if (
            !semanticPrototypePolicy().showControls ||
            !isResumed ||
            !isCurrentMapOwner(requireResumed = true)
        ) return
        val mapView = attachedMapView ?: return
        previewCaptureRunnable?.let(mapView::removeCallbacks)
        val generation = previewCaptureGeneration
        val capture = Runnable {
            previewCaptureRunnable = null
            if (
                generation == previewCaptureGeneration &&
                isCurrentMapOwner(mapView, requireResumed = true)
            ) {
                capturePrototypeTerrainPreviewForPersistence()
            }
        }
        previewCaptureRunnable = capture
        mapView.postDelayed(capture, PREVIEW_CAPTURE_IDLE_DELAY_MS)
    }

    private fun cancelPrototypeTerrainPreviewCapture() {
        previewCaptureGeneration++
        previewCapturePending = false
        previewCaptureRunnable?.let { attachedMapView?.removeCallbacks(it) }
        previewCaptureRunnable = null
    }

    fun prototypeDiagnosticsForTesting(): osrsMapPrototypeDiagnostics? {
        return prototypeController?.diagnosticsForTesting()
    }

    fun setPrototypeZoomForTesting(zoom: Double): Boolean {
        return prototypeController?.setZoomForTesting(zoom) == true
    }

    fun panPrototypeAwayAndBackForTesting(): Boolean {
        return prototypeController?.panPrototypeAwayAndBackForTesting() == true
    }

    fun performPrototypeFeatureActionForTesting(featureId: String): Boolean {
        return prototypeController?.performFeatureActionForTesting(featureId) == true
    }

    fun hitPrototypeFeatureIdForTesting(x: Float, y: Float): String? {
        return prototypeController?.hitFeatureIdForTesting(x, y)
    }

    fun performPrototypeSearchForTesting(query: String): Boolean {
        return prototypeController?.performSearchForTesting(query) == true
    }

    fun togglePrototypeCategoryForTesting(categoryValue: String): Boolean {
        return prototypeController?.toggleCategoryForTesting(categoryValue) == true
    }

    fun setPrototypeLayerVisibilityForTesting(
        labels: Boolean? = null,
        pois: Boolean? = null,
        links: Boolean? = null
    ): Boolean {
        return prototypeController?.setLayerVisibilityForTesting(labels, pois, links) == true
    }

    fun setPrototypeOverviewCenterForTesting(gameX: Double, gameY: Double): Boolean {
        return prototypeController?.setOverviewCenterForTesting(gameX, gameY) == true
    }

    fun setPrototypeCameraForTesting(gameX: Double, gameY: Double, zoom: Double): Boolean {
        return prototypeController?.setCameraForTesting(gameX, gameY, zoom) == true
    }

    fun setPrototypeCameraPoseForTesting(
        gameX: Double,
        gameY: Double,
        zoom: Double,
        bearing: Double,
        tilt: Double
    ): Boolean {
        return prototypeController?.setCameraPoseForTesting(gameX, gameY, zoom, bearing, tilt) == true
    }

    fun restorePrototypeCameraForTesting(): Boolean {
        return prototypeController?.restorePreviousCameraForTesting() == true
    }

    fun selectPrototypeSurfaceForTesting(surfaceId: String): Boolean {
        return prototypeController?.selectSurfaceForTesting(surfaceId) == true
    }

    fun recreatePrototypeStyleForTesting(): Boolean {
        val maplibreMap = map ?: return false
        val ownerToken = mapOwnerToken ?: return false
        if (!isCurrentMapOwner(ownerToken = ownerToken)) return false
        val styleJson = AndroidMapPreloader.getInstance().prototypeStyleJsonForTesting()
        maplibreMap.setStyle(styleJson) {
            if (!isCurrentMapOwner(ownerToken = ownerToken)) return@setStyle
            AndroidMapPreloader.getInstance().restoreFloorLayersForTesting(requireContext(), maplibreMap, currentFloor)
            configurePrototypeForAttachedMap()
        }
        return true
    }

    private fun semanticPrototypePolicy(): osrsMapPrototypeRuntimePolicy {
        return osrsMapPrototypeBridge.runtimePolicy(
            explicitlyEnabled = arguments?.getBoolean(ARG_ENABLE_SEMANTIC_PROTOTYPE, false) == true
        )
    }

    /**
     * Update floor - delegates to the shared preloader
     */
    fun updateFloor(newFloor: Int) {
        currentFloor = newFloor.coerceIn(0, 3)
        AndroidMapPreloader.getInstance().updateFloor(currentFloor)
        updateFloorControlStates()
        L.d("$logTag: Updated floor to $currentFloor")
    }
    
    private fun setupFloorControls() {
        val isEmbeddedMap = arguments?.getString(ARG_LON) != null
        if (isEmbeddedMap) {
            binding.floorControls.visibility = View.GONE
            return
        }
        
        if (maxFloor > 0) {
            binding.floorControls.visibility = View.VISIBLE
            
            binding.floorControlUp.setOnClickListener {
                if (currentFloor < maxFloor) {
                    showFloor(currentFloor + 1)
                }
            }
            
            binding.floorControlDown.setOnClickListener {
                if (currentFloor > 0) {
                    showFloor(currentFloor - 1)
                }
            }
            updateFloorControlStates()
        } else {
            binding.floorControls.visibility = View.GONE
        }
    }

    private fun setupMarkerInfoControl() {
        binding.mapMarkerInfoButton.setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.map_marker_noninteractive_message,
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.mapRetryButton.setOnClickListener {
            showMapLoading()
            AndroidMapPreloader.getInstance().requestPreload(requireContext())
        }
    }
    
    private fun showFloor(newFloor: Int) {
        if (newFloor == currentFloor || newFloor < 0 || newFloor > maxFloor) return
        
        currentFloor = newFloor
        AndroidMapPreloader.getInstance().updateFloor(currentFloor)
        L.d("$logTag: RASTER_FLOOR_STATE floor=$currentFloor")
        updateFloorControlStates()
        
        L.d("$logTag: Changed to floor $currentFloor")
    }
    
    private fun updateFloorControlStates() {
        val state = MapFloorControlPolicy.state(currentFloor, maxFloor)
        currentFloor = state.floorLabel.toInt()
        binding.floorControlText.text = state.floorLabel
        binding.floorControlText.contentDescription = getString(R.string.map_floor_current, state.floorLabel)
        applyFloorButtonState(binding.floorControlUp, state.up)
        applyFloorButtonState(binding.floorControlDown, state.down)
    }

    private fun applyFloorButtonState(
        button: ImageButton,
        state: MapFloorControlPolicy.ButtonState
    ) {
        button.alpha = state.alpha
        button.isEnabled = state.isActionable
        button.isClickable = state.isActionable
        button.isFocusable = state.isActionable
        button.importantForAccessibility = if (state.isActionable) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun applyMapAccessibilityDescription(mapView: MapView) {
        mapView.contentDescription = getString(R.string.map_accessibility_description)
    }

    private fun showMapLoading() {
        binding.mapUnavailablePanel.visibility = View.GONE
    }

    private fun hideMapUnavailable() {
        binding.mapUnavailablePanel.visibility = View.GONE
    }

    private fun showMapUnavailable(detail: String?) {
        binding.mapUnavailableMessage.text = detail
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.map_unavailable_message)
        binding.mapUnavailablePanel.visibility = View.VISIBLE
    }

    private fun isCurrentMapOwner(
        mapView: MapView? = attachedMapView,
        ownerToken: Long? = mapOwnerToken,
        requireResumed: Boolean = false
    ): Boolean {
        if (_binding == null || !isAdded || isHidden) return false
        if (mapView == null || ownerToken == null || attachedMapView !== mapView) return false
        val lifecycleState = viewLifecycleOwner.lifecycle.currentState
        val minimumState = if (requireResumed) {
            androidx.lifecycle.Lifecycle.State.RESUMED
        } else {
            androidx.lifecycle.Lifecycle.State.CREATED
        }
        return lifecycleState.isAtLeast(minimumState) &&
            AndroidMapPreloader.getInstance().isActiveOwner(ownerToken, mapView)
    }

    private fun cancelDeferredAttach() {
        deferredAttachRunnable?.let { runnable ->
            _binding?.mapView?.removeCallbacks(runnable)
        }
        deferredAttachRunnable = null
    }

    private fun releaseMapOwnerForHiddenState() {
        cancelDeferredAttach()
        prototypeController?.disable(map)
        removeTerrainRenderListener()
        removePrototypePreviewCaptureListener()
        val token = mapOwnerToken
        if (token != null) {
            AndroidMapPreloader.getInstance().detachFromMainMapContainer(token)
        }
        attachedMapView = null
        mapOwnerToken = null
        map = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        L.d("$logTag: LIFECYCLE: onHiddenChanged(hidden=$hidden)")
        if (hidden) {
            L.d("$logTag: Fragment hidden - releasing shared MapView owner token")
            releaseMapOwnerForHiddenState()
        } else {
            L.d("$logTag: Fragment shown - requesting a new shared MapView owner token")
            attachMapForVisibleNavigation()
        }
    }
}
