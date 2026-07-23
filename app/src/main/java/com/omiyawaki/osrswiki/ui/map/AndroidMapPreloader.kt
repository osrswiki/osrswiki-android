package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Android equivalent to iOS osrsBackgroundMapPreloader
 * Creates ONE shared MapView instance that gets reused by the main map view for instant loading
 * 
 * Architecture:
 * 1. Create off-screen MapView with all tiles pre-loaded
 * 2. Initialize MapLibre with style and layers
 * 3. Provide attachment mechanism to move shared map to visible container
 * 4. Maintain tile state across attachments for instant display
 */
class AndroidMapPreloader private constructor() {

    class MapAttachment internal constructor(
        val ownerToken: Long,
        val mapView: MapView
    )

    sealed class PreloadState {
        data object Idle : PreloadState()
        data class Loading(val progress: Double) : PreloadState()
        data object Ready : PreloadState()
        data class Failed(val message: String, val cause: Throwable? = null) : PreloadState()
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AndroidMapPreloader? = null
        
        fun getInstance(): AndroidMapPreloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AndroidMapPreloader().also { INSTANCE = it }
            }
        }
        
        private const val GROUND_FLOOR_UNDERLAY_OPACITY = 0.5f
        private const val PRELOAD_SURFACE_SIZE_PX = 32
    }
    
    // SHARED map instance that gets reused by the main map view
    private var sharedMapView: MapView? = null
    private var sharedMapContainer: FrameLayout? = null
    private var sharedMap: MapLibreMap? = null

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preloadCoordinator = ProcessOwnedPreloadCoordinator(processScope)

    private val ownerGate = MapOwnerGenerationGate()
    private var mapViewStarted = false
    private var mapViewResumed = false
    
    // State tracking
    private val _isPreloadingMap = MutableLiveData(false)
    val isPreloadingMap: LiveData<Boolean> = _isPreloadingMap
    
    private val _preloadingProgress = MutableLiveData(0.0)
    val preloadingProgress: LiveData<Double> = _preloadingProgress
    
    private val _mapPreloaded = MutableLiveData(false)
    val mapPreloaded: LiveData<Boolean> = _mapPreloaded
    
    private val _allLayersReady = MutableLiveData(false)
    val allLayersReady: LiveData<Boolean> = _allLayersReady

    private val _preloadState = MutableLiveData<PreloadState>(PreloadState.Idle)
    val preloadState: LiveData<PreloadState> = _preloadState
    
    private var currentFloor = 0
    private val mapFiles = (0..3).map { "map_floor_$it.mbtiles" }
    
    // Defensive programming: track MapView attachment state
    private var isMapViewAttached = false
    private var currentAttachedContainer: ViewGroup? = null
    
    val isMapReady: Boolean
        get() = mapPreloaded.value == true && allLayersReady.value == true
    
    /**
     * Debug helper: Log current MapView state for troubleshooting
     */
    fun logMapViewState(context: String = "") {
        val mapView = sharedMapView
        val parent = mapView?.parent as? ViewGroup
        L.d("AndroidMapPreloader: MapView State [$context]:")
        L.d("  - MapView exists: ${mapView != null}")
        L.d("  - Parent: ${parent?.javaClass?.simpleName ?: "null"}")
        L.d("  - Visibility: ${if (mapView != null) if (mapView.visibility == View.VISIBLE) "VISIBLE" else "INVISIBLE/GONE" else "N/A"}")
        L.d("  - Attached flag: $isMapViewAttached")
        L.d("  - Tracked container: ${currentAttachedContainer?.javaClass?.simpleName ?: "null"}")
        L.d("  - SharedContainer exists: ${sharedMapContainer != null}")
    }
    
    /**
     * Create the shared MapLibre instance with all layers pre-created
     * This should be called during app launch for background preloading
     */
    fun requestPreload(context: Context) {
        val hostContext = context
        processScope.launch {
            preloadMapInBackground(hostContext)
        }
    }

    suspend fun preloadMapInBackground(context: Context): Result<Unit> {
        if (isMapReady) {
            L.d("AndroidMapPreloader: Shared map already ready; preload request is idempotent")
            _preloadState.value = PreloadState.Ready
            return Result.success(Unit)
        }
        return preloadCoordinator.awaitOrStart { generation ->
            performPreload(context, generation)
        }
    }

    private suspend fun performPreload(context: Context, generation: Long): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            if (!isCurrentPreloadGeneration(generation)) {
                return@withContext Result.failure(CancellationException("Obsolete preload generation"))
            }
            _isPreloadingMap.value = true
            _preloadingProgress.value = 0.0
            _mapPreloaded.value = false
            _allLayersReady.value = false
            _preloadState.value = PreloadState.Loading(0.0)
            L.d("AndroidMapPreloader: Creating process-owned shared map generation=$generation")

            try {
                releaseSharedMapResources(generation)
                ensurePreloadGeneration(generation)
                osrsMapPrototypePerformance.measureCpuSpan("maplibre_sdk_initialization") {
                    MapLibre.getInstance(context.applicationContext)
                }
                ensurePreloadGeneration(generation)

                updateProgress(generation, 0.1, "Creating shared MapView instance...")
                osrsMapPrototypePerformance.measureCpuSpan(
                    "maplibre_view_construction",
                    "preload_generation=$generation preload_surface_px=$PRELOAD_SURFACE_SIZE_PX pixel_ratio=1.0"
                ) {
                    createSharedMapView(context, generation)
                }

                updateProgress(generation, 0.3, "Loading map style...")
                withTimeout(15_000) {
                    waitForStyleToLoad(generation)
                }

                updateProgress(generation, 0.5, "Pre-creating all floor layers...")
                preCreateAllFloorLayers(context.applicationContext, generation)
                ensurePreloadGeneration(generation)

                updateProgress(generation, 1.0, "Shared map instance ready!")
                _mapPreloaded.value = true
                _allLayersReady.value = true
                _isPreloadingMap.value = false
                _preloadState.value = PreloadState.Ready
                L.d("AndroidMapPreloader: Shared map generation=$generation is ready")
                Result.success(Unit)
            } catch (timeout: TimeoutCancellationException) {
                if (isCurrentPreloadGeneration(generation)) {
                    val message = timeout.message ?: "Map preload timed out."
                    _mapPreloaded.value = false
                    _allLayersReady.value = false
                    _isPreloadingMap.value = false
                    _preloadState.value = PreloadState.Failed(message, timeout)
                    releaseSharedMapResources(generation)
                }
                Result.failure(timeout)
            } catch (cancelled: CancellationException) {
                if (isCurrentPreloadGeneration(generation)) {
                    _isPreloadingMap.value = false
                    _preloadState.value = PreloadState.Idle
                    releaseSharedMapResources(generation)
                }
                throw cancelled
            } catch (failure: Exception) {
                if (isCurrentPreloadGeneration(generation)) {
                    val message = failure.message ?: "Map failed to preload."
                    L.e("AndroidMapPreloader: Preload generation=$generation failed: $message", failure)
                    _mapPreloaded.value = false
                    _allLayersReady.value = false
                    _isPreloadingMap.value = false
                    _preloadState.value = PreloadState.Failed(message, failure)
                    releaseSharedMapResources(generation)
                }
                Result.failure(failure)
            }
        }

    private fun isCurrentPreloadGeneration(generation: Long): Boolean {
        return preloadCoordinator.isCurrent(generation)
    }

    private fun ensurePreloadGeneration(generation: Long) {
        if (!isCurrentPreloadGeneration(generation)) {
            throw CancellationException("Obsolete preload generation $generation")
        }
    }
    
    /**
     * Create the shared MapView that will be reused
     * Positioned off-screen to avoid interfering with UI
     */
    private fun createSharedMapView(context: Context, generation: Long) {
        ensurePreloadGeneration(generation)
        val viewContext = context.applicationContext
        // Create off-screen container (position it outside visible area)
        sharedMapContainer = FrameLayout(viewContext).apply {
            layoutParams = ViewGroup.LayoutParams(PRELOAD_SURFACE_SIZE_PX, PRELOAD_SURFACE_SIZE_PX)
            x = -128f
            y = -128f
        }

        val options = MapLibreMapOptions.createFromAttributes(context)
            .pixelRatio(1f)
            .setPrefetchesTiles(false)
            .foregroundLoadColor(Color.TRANSPARENT)
        // The preloader outlives a configuration-specific Activity. An application-scoped
        // View context lets the native renderer survive rotation without retaining the old host.
        sharedMapView = MapView(viewContext, options).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.INVISIBLE  // Hide until attached to prevent drawing artifacts
        }
        
        sharedMapContainer?.addView(sharedMapView!!)
        
        // CRITICAL: Add to activity's root view for MapLibre initialization  
        // MapLibre requires the MapView to be in the view hierarchy for getMapAsync to work
        if (context is android.app.Activity) {
            val rootView = context.findViewById<ViewGroup>(android.R.id.content)
            rootView?.addView(sharedMapContainer)
            L.d("AndroidMapPreloader: Shared MapView attached to activity root view")
        } else {
            L.w("AndroidMapPreloader: Context is not Activity - shared map may not initialize properly")
        }
        
        L.d("AndroidMapPreloader: Shared MapView created off-screen")
    }
    
    /**
     * Wait for MapLibre style to load asynchronously
     * Properly waits for both getMapAsync and setStyle callbacks
     */
    private suspend fun waitForStyleToLoad(generation: Long) = withContext(Dispatchers.Main.immediate) {
        ensurePreloadGeneration(generation)
        // Step 1: Wait for MapLibreMap to be ready
        sharedMap = suspendCancellableCoroutine { continuation ->
            sharedMapView?.getMapAsync { maplibreMap ->
                if (!continuation.isActive || !isCurrentPreloadGeneration(generation)) {
                    return@getMapAsync
                }
                L.d("AndroidMapPreloader: MapLibreMap instance ready")
                continuation.resume(maplibreMap)
            }
        }
        ensurePreloadGeneration(generation)
        
        // Step 2: Configure map settings  
        sharedMap?.let { maplibreMap ->
            configureMapSettings(maplibreMap)
            
            // Step 3: Wait for style to load (using file-based approach like iOS)
            suspendCancellableCoroutine<Style> { continuation ->
                try {
                    val styleJson = createMapStyle()
                    // Use cacheDir from the MapView's context
                    val tempDir = sharedMapView?.context?.cacheDir
                    val styleFile = File(tempDir, "osrs-shared-map-style.json")
                    styleFile.writeText(styleJson)
                    
                    L.d("AndroidMapPreloader: Loading style from file: ${styleFile.absoluteFile}")
                    maplibreMap.setStyle(Style.Builder().fromUri("file://${styleFile.absolutePath}")) { style ->
                        if (!continuation.isActive || !isCurrentPreloadGeneration(generation)) {
                            return@setStyle
                        }
                        L.d("AndroidMapPreloader: Map style loaded successfully from file")
                        continuation.resume(style)
                    }
                } catch (e: Exception) {
                    L.e("AndroidMapPreloader: Failed to create style file", e)
                    // Fallback to inline JSON
                    val styleJson = createMapStyle()
                    maplibreMap.setStyle(styleJson) { style ->
                        if (!continuation.isActive || !isCurrentPreloadGeneration(generation)) {
                            return@setStyle
                        }
                        L.d("AndroidMapPreloader: Map style loaded successfully (fallback)")
                        continuation.resume(style)
                    }
                }
            }
        }
    }
    
    /**
     * Configure MapLibre settings to match main map
     */
    private fun configureMapSettings(maplibreMap: MapLibreMap) {
        maplibreMap.uiSettings.apply {
            isLogoEnabled = false
            isAttributionEnabled = false
            isCompassEnabled = true  // Enable compass for rotation
            isRotateGesturesEnabled = true
            isTiltGesturesEnabled = false
            isScrollGesturesEnabled = true  // Enable panning
            isZoomGesturesEnabled = true    // Enable zoom
            
            // Configure compass position to match original implementation
            val marginInDp = 16f
            val context = sharedMapView?.context
            val density = context?.resources?.displayMetrics?.density ?: 1f
            val marginInPx = (marginInDp * density).toInt()
            setCompassGravity(Gravity.TOP or Gravity.END)
            setCompassMargins(0, marginInPx, marginInPx, 0)
        }
        
        // Set zoom limits: min zoom 0, max zoom 12
        maplibreMap.setMinZoomPreference(0.0)
        maplibreMap.setMaxZoomPreference(12.0)
        
        // Set position to Lumbridge town center.
        val center = LatLng(osrsMapDefaultView.LATITUDE, osrsMapDefaultView.LONGITUDE)
        maplibreMap.cameraPosition = CameraPosition.Builder()
            .target(center)
            .zoom(osrsMapDefaultView.ZOOM)
            .build()
            
        L.d("AndroidMapPreloader: Initial zoom set to ${osrsMapDefaultView.ZOOM}, center: $center")
        
        val gameMinX = osrsMapDefaultView.GAME_MIN_X
        val gameMaxX = osrsMapDefaultView.GAME_MAX_X
        val gameMinY = osrsMapDefaultView.GAME_MIN_Y
        val gameMaxY = osrsMapDefaultView.GAME_MAX_Y
        
        val southWest = gameToLatLng(gameMinX, gameMinY)
        val northEast = gameToLatLng(gameMaxX, gameMaxY)
        
        val osrsMapBounds = LatLngBounds.Builder()
            .include(southWest)
            .include(northEast)
            .build()
            
        // Use proper bounds that restrict the entire visible area, not just camera center
        maplibreMap.setLatLngBoundsForCameraTarget(osrsMapBounds)
        
        L.d("AndroidMapPreloader: OSRS bounds set:")
        L.d("AndroidMapPreloader: - Game coords: X($gameMinX-$gameMaxX), Y($gameMinY-$gameMaxY)")
        L.d("AndroidMapPreloader: - LatLng bounds: SW(${southWest.latitude}, ${southWest.longitude}), NE(${northEast.latitude}, ${northEast.longitude})")
        L.d("AndroidMapPreloader: - Bounds applied to camera target restrictions")
        
        // Restore proper zoom after bounds operation
        if (maplibreMap.cameraPosition.zoom < 1.0) {
            maplibreMap.cameraPosition = CameraPosition.Builder()
                .target(center)
                .zoom(osrsMapDefaultView.ZOOM)
                .build()
            L.d("AndroidMapPreloader: Fixed zoom: ${maplibreMap.cameraPosition.zoom}, center: ${maplibreMap.cameraPosition.target}")
        }
    }
    
    /**
     * Pre-create all floor layers for instant floor switching
     */
    private suspend fun preCreateAllFloorLayers(
        context: Context,
        generation: Long
    ) = withContext(Dispatchers.IO) {
        ensurePreloadGeneration(generation)
        copyMapAssets(context).getOrThrow()
        
        withContext(Dispatchers.Main.immediate) {
            ensurePreloadGeneration(generation)
            val style = sharedMap?.style
            if (style == null) {
                L.e("AndroidMapPreloader: Style not available for layer creation")
                return@withContext
            }
            
            // Add all floor layers
            for (floor in 0..3) {
                ensurePreloadGeneration(generation)
                val fileName = mapFiles[floor]
                val filePath = File(context.filesDir, fileName).absolutePath
                val sourceId = "map-source-$floor"
                val layerId = "osrs-layer-$floor"
                
                // Add raster source
                val rasterSource = RasterSource(sourceId, "mbtiles://$filePath", 1024)
                style.addSource(rasterSource)
                
                // Add raster layer with pixelated rendering and proper layering
                val rasterLayer = RasterLayer(layerId, sourceId).apply {
                    setProperties(
                        PropertyFactory.visibility(Property.VISIBLE), // All layers always visible
                        PropertyFactory.rasterOpacity(
                            when {
                                floor == currentFloor -> 1.0f  // Target floor: full opacity
                                floor == 0 && currentFloor > 0 -> GROUND_FLOOR_UNDERLAY_OPACITY  // Ground floor underlay
                                else -> 0.0f  // Other floors: invisible but still rendered
                            }
                        ),
                        PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST) // Crisp pixelated rendering
                    )
                }
                style.addLayer(rasterLayer)
                
                L.d("AndroidMapPreloader: Pre-created layer for floor $floor")
            }
        }
    }
    
    /**
     * Copy map assets from assets to internal storage
     */
    private fun copyMapAssets(context: Context): Result<Unit> {
        for (fileName in mapFiles) {
            val destFile = File(context.filesDir, fileName)
            if (destFile.exists() && destFile.length() > 0L) continue
            
            try {
                copyAssetAtomically(context, fileName, destFile)
                if (!destFile.exists() || destFile.length() <= 0L) {
                    return Result.failure(IOException("Copied MBTiles asset is empty: $fileName"))
                }
                L.d("AndroidMapPreloader: Copied asset: $fileName")
            } catch (e: Exception) {
                L.e("AndroidMapPreloader: Failed to copy asset file: $fileName", e)
                return Result.failure(IOException("Required map asset is unavailable: $fileName", e))
            }
        }
        return Result.success(Unit)
    }

    private fun copyAssetAtomically(context: Context, fileName: String, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.copying")
        temporary.delete()
        try {
            context.assets.openFd(fileName).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { input ->
                    FileOutputStream(temporary).channel.use { output ->
                        var inputPosition = descriptor.startOffset
                        var remaining = descriptor.length
                        while (remaining > 0L) {
                            val transferred = input.transferTo(inputPosition, remaining, output)
                            if (transferred <= 0L) break
                            inputPosition += transferred
                            remaining -= transferred
                        }
                        output.force(false)
                    }
                }
            }
        } catch (_: IOException) {
            context.assets.open(fileName).use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
        }
        if (temporary.length() <= 0L || !temporary.renameTo(destination)) {
            temporary.delete()
            throw IOException("Could not publish copied asset: $fileName")
        }
    }
    
    /**
     * Move the shared map to be visible in the main map container
     * This is equivalent to iOS attachToMainMapContainer
     */
    fun attachToMainMapContainer(mainContainer: ViewGroup): MapAttachment? {
        if (!isMapReady) {
            L.e("AndroidMapPreloader: Shared map is not ready for attachment")
            return null
        }

        val mapView = sharedMapView
        if (mapView == null) {
            L.e("AndroidMapPreloader: Shared map not ready for attachment")
            return null
        }
        
        val priorOwner = ownerGate.activeToken
        val ownerToken = ownerGate.claim()
        L.d("AndroidMapPreloader: Attaching shared map owner=$ownerToken prior_owner=$priorOwner")
        logMapViewState("Before attachment")
        
        // CRITICAL FIX: Robustly handle any existing parent before attachment
        val currentParent = mapView.parent as? ViewGroup
        if (currentParent != null) {
            L.d("AndroidMapPreloader: MapView has existing parent: ${currentParent.javaClass.simpleName}, removing...")
            try {
                currentParent.removeView(mapView)
                L.d("AndroidMapPreloader: Successfully removed MapView from existing parent")
            } catch (e: Exception) {
                L.e("AndroidMapPreloader: Error removing MapView from existing parent: ${e.message}")
                // Continue anyway - addView will handle the IllegalStateException if needed
            }
        } else {
            L.d("AndroidMapPreloader: MapView has no existing parent, proceeding with attachment")
        }
        
        // Add to main container and make visible
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        mapView.visibility = View.VISIBLE  // Make visible when properly attached
        
        try {
            mainContainer.addView(mapView, layoutParams)
            isMapViewAttached = true
            currentAttachedContainer = mainContainer
            L.d("AndroidMapPreloader: ✅ Shared map attached to main container - instant display!")
            logMapViewState("After successful attachment")
        } catch (e: IllegalStateException) {
            L.e("AndroidMapPreloader: Failed to attach MapView - IllegalStateException: ${e.message}")
            logMapViewState("After attachment failure")
            // Last resort: try to force-remove from any parent and retry once
            (mapView.parent as? ViewGroup)?.removeView(mapView)
            try {
                mainContainer.addView(mapView, layoutParams)
                isMapViewAttached = true
                currentAttachedContainer = mainContainer
                L.d("AndroidMapPreloader: ✅ Shared map attached after force-removal retry")
                logMapViewState("After retry success")
            } catch (e2: Exception) {
                L.e("AndroidMapPreloader: Final attachment attempt failed: ${e2.message}")
                logMapViewState("After final failure")
                ownerGate.release(ownerToken)
                return null
            }
        } catch (e: Exception) {
            L.e("AndroidMapPreloader: Unexpected error during attachment: ${e.message}")
            logMapViewState("After unexpected error")
            ownerGate.release(ownerToken)
            return null
        }
        
        return MapAttachment(ownerToken, mapView)
    }

    fun isActiveOwner(ownerToken: Long, mapView: MapView? = null): Boolean {
        return ownerGate.isActive(ownerToken) &&
            (mapView == null || sharedMapView === mapView)
    }

    fun startForOwner(ownerToken: Long) {
        if (!isActiveOwner(ownerToken)) return
        val mapView = sharedMapView ?: return
        if (!mapViewStarted) {
            mapView.onStart()
            mapViewStarted = true
        }
    }

    fun resumeForOwner(ownerToken: Long) {
        if (!isActiveOwner(ownerToken)) return
        startForOwner(ownerToken)
        val mapView = sharedMapView ?: return
        if (!mapViewResumed) {
            mapView.onResume()
            mapViewResumed = true
        }
    }

    fun pauseForOwner(ownerToken: Long) {
        if (!isActiveOwner(ownerToken) || !mapViewResumed) return
        sharedMapView?.onPause()
        mapViewResumed = false
    }

    fun stopForOwner(ownerToken: Long) {
        if (!isActiveOwner(ownerToken)) return
        pauseForOwner(ownerToken)
        if (mapViewStarted) {
            sharedMapView?.onStop()
            mapViewStarted = false
        }
    }
    
    /**
     * Detach the shared map from the main container and return it to the shared container
     * This ensures proper cleanup when fragments are destroyed/hidden
     */
    fun detachFromMainMapContainer(ownerToken: Long): MapView? {
        if (!isActiveOwner(ownerToken)) {
            L.d("AndroidMapPreloader: Ignoring detach from obsolete owner=$ownerToken active=${ownerGate.activeToken}")
            return null
        }
        val mapView = sharedMapView
        if (mapView == null) {
            L.d("AndroidMapPreloader: No shared map to detach")
            ownerGate.release(ownerToken)
            return null
        }

        stopForOwner(ownerToken)
        ownerGate.release(ownerToken)
        
        L.d("AndroidMapPreloader: Detaching shared map from main container")
        logMapViewState("Before detachment")
        
        // Remove from current parent (likely a fragment's container)
        val currentParent = mapView.parent as? ViewGroup
        if (currentParent != null) {
            L.d("AndroidMapPreloader: Removing MapView from current parent: ${currentParent.javaClass.simpleName}")
            try {
                currentParent.removeView(mapView)
                isMapViewAttached = false
                currentAttachedContainer = null
                L.d("AndroidMapPreloader: Successfully removed MapView from current parent")
            } catch (e: Exception) {
                L.e("AndroidMapPreloader: Error removing MapView from current parent: ${e.message}")
            }
        } else {
            L.d("AndroidMapPreloader: MapView has no current parent")
        }
        
        // Return to shared container (off-screen position)
        if (sharedMapContainer != null) {
            try {
                mapView.visibility = View.INVISIBLE  // Hide while off-screen
                sharedMapContainer?.addView(mapView)
                L.d("AndroidMapPreloader: ✅ Shared map returned to off-screen container")
                logMapViewState("After detachment success")
            } catch (e: Exception) {
                L.e("AndroidMapPreloader: Error returning MapView to shared container: ${e.message}")
                // Create new shared container if needed
                if (mapView.context is android.app.Activity) {
                    createSharedMapContainer(mapView.context)
                    sharedMapContainer?.addView(mapView)
                    L.d("AndroidMapPreloader: Created new shared container and added MapView")
                    logMapViewState("After container recreation")
                }
            }
        } else {
            L.w("AndroidMapPreloader: Shared container is null, MapView is now orphaned")
            // Try to recreate shared container
            if (mapView.context is android.app.Activity) {
                createSharedMapContainer(mapView.context)
                sharedMapContainer?.addView(mapView)
                L.d("AndroidMapPreloader: Recreated shared container for orphaned MapView")
                logMapViewState("After orphan recovery")
            }
        }
        
        return mapView
    }
    
    /**
     * Helper method to create or recreate the shared map container
     */
    private fun createSharedMapContainer(context: Context) {
        (sharedMapContainer?.parent as? ViewGroup)?.removeView(sharedMapContainer)
        
        // Create new off-screen container
        sharedMapContainer = FrameLayout(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(400, 600)
            x = -2000f
            y = -2000f
        }
        
        // Add to activity's root view
        if (context is android.app.Activity) {
            val rootView = context.findViewById<ViewGroup>(android.R.id.content)
            rootView?.addView(sharedMapContainer)
            L.d("AndroidMapPreloader: Created new shared container")
        }
    }

    fun destroy() {
        L.d("AndroidMapPreloader: Destroying shared map resources")
        preloadCoordinator.invalidate(CancellationException("Map preloader reset"))
        releaseSharedMapResources()
        _isPreloadingMap.value = false
        _preloadingProgress.value = 0.0
        _mapPreloaded.value = false
        _allLayersReady.value = false
        _preloadState.value = PreloadState.Idle
    }

    /** Keep the initialized native renderer while its Activity host is being replaced. */
    fun retainForProcessLifetime() {
        val mapView = sharedMapView ?: return
        ownerGate.activeToken?.let(::stopForOwner)
        ownerGate.invalidate()
        val container = sharedMapContainer ?: FrameLayout(mapView.context.applicationContext).also {
            sharedMapContainer = it
        }
        (mapView.parent as? ViewGroup)?.removeView(mapView)
        (container.parent as? ViewGroup)?.removeView(container)
        mapView.visibility = View.INVISIBLE
        container.addView(mapView)
        isMapViewAttached = false
        currentAttachedContainer = null
        L.d("AndroidMapPreloader: Retained shared map for process lifetime")
    }

    fun retainForConfigurationChange() = retainForProcessLifetime()

    private fun releaseSharedMapResources(expectedGeneration: Long? = null) {
        if (expectedGeneration != null && !isCurrentPreloadGeneration(expectedGeneration)) return
        ownerGate.activeToken?.let(::stopForOwner)
        ownerGate.invalidate()
        val mapView = sharedMapView
        val mapContainer = sharedMapContainer
        val mapViewParent = mapView?.parent as? ViewGroup
        try {
            mapViewParent?.removeView(mapView)
        } catch (e: Exception) {
            L.e("AndroidMapPreloader: Error removing MapView from parent during release", e)
        }

        val rootView = (mapContainer?.context as? android.app.Activity)
            ?.findViewById<ViewGroup>(android.R.id.content)
        try {
            rootView?.removeView(sharedMapContainer)
        } catch (e: Exception) {
            L.e("AndroidMapPreloader: Error removing shared container during release", e)
        }

        try {
            sharedMapView?.onDestroy()
        } catch (e: Exception) {
            L.e("AndroidMapPreloader: Error destroying shared MapView", e)
        }

        sharedMap = null
        sharedMapView = null
        sharedMapContainer = null
        isMapViewAttached = false
        currentAttachedContainer = null
        mapViewStarted = false
        mapViewResumed = false
    }
    
    /**
     * Update floor visibility on the shared map
     */
    fun updateFloor(newFloor: Int) {
        currentFloor = newFloor.coerceIn(0, 3)
        
        val style = sharedMap?.style ?: return
        
        // Update layer opacity for proper layering (like iOS implementation)
        for (floor in 0..3) {
            val layerId = "osrs-layer-$floor"
            val layer = style.getLayer(layerId) ?: continue
            
            layer.setProperties(
                PropertyFactory.visibility(Property.VISIBLE), // All layers always visible
                PropertyFactory.rasterOpacity(
                    when {
                        floor == currentFloor -> 1.0f  // Target floor: full opacity
                        floor == 0 && currentFloor > 0 -> GROUND_FLOOR_UNDERLAY_OPACITY  // Ground floor underlay
                        else -> 0.0f  // Other floors: invisible but still rendered
                    }
                ),
                PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST) // Maintain crisp rendering
            )
        }
        
        L.d("AndroidMapPreloader: Updated to floor $currentFloor")
    }
    
    private fun updateProgress(generation: Long, progress: Double, message: String) {
        ensurePreloadGeneration(generation)
        _preloadingProgress.value = progress
        _preloadState.value = PreloadState.Loading(progress)
        L.d("AndroidMapPreloader: $message (${(progress * 100).toInt()}%)")
    }
    
    private fun createMapStyle(): String {
        return """
            {
                "version": 8,
                "name": "OSRS Map Style",
                "sources": {},
                "layers": [
                    {
                        "id": "background",
                        "type": "background",
                        "paint": {
                            "background-color": "#000000"
                        }
                    }
                ]
            }
        """.trimIndent()
    }

    fun prototypeStyleJsonForTesting(): String {
        return createMapStyle()
    }

    fun restoreFloorLayersForTesting(context: Context, maplibreMap: MapLibreMap, floor: Int) {
        copyMapAssets(context).getOrThrow()
        val style = maplibreMap.style ?: return
        val boundedFloor = floor.coerceIn(0, 3)
        for (candidateFloor in 0..3) {
            val fileName = mapFiles[candidateFloor]
            val filePath = File(context.filesDir, fileName).absolutePath
            val sourceId = "map-source-$candidateFloor"
            val layerId = "osrs-layer-$candidateFloor"
            if (style.getSource(sourceId) == null) {
                style.addSource(RasterSource(sourceId, "mbtiles://$filePath", 1024))
            }
            if (style.getLayer(layerId) == null) {
                style.addLayer(
                    RasterLayer(layerId, sourceId).apply {
                        setProperties(
                            PropertyFactory.visibility(Property.VISIBLE),
                            PropertyFactory.rasterOpacity(
                                when {
                                    candidateFloor == boundedFloor -> 1.0f
                                    candidateFloor == 0 && boundedFloor > 0 -> GROUND_FLOOR_UNDERLAY_OPACITY
                                    else -> 0.0f
                                }
                            ),
                            PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST)
                        )
                    }
                )
            }
        }
    }
    
    private fun gameToLatLng(gx: Double, gy: Double): LatLng {
        return LatLng(
            osrsMapDefaultView.latitudeForGameY(gy),
            osrsMapDefaultView.longitudeForGameX(gx)
        )
    }
}

internal class MapOwnerGenerationGate {
    private var nextToken = 0L

    var activeToken: Long? = null
        private set

    @Synchronized
    fun claim(): Long {
        val token = ++nextToken
        activeToken = token
        return token
    }

    @Synchronized
    fun isActive(token: Long): Boolean = activeToken == token

    @Synchronized
    fun release(token: Long): Boolean {
        if (activeToken != token) return false
        activeToken = null
        return true
    }

    @Synchronized
    fun invalidate(): Long? {
        val invalidated = activeToken
        activeToken = null
        return invalidated
    }
}

internal class ProcessOwnedPreloadCoordinator(
    private val processScope: CoroutineScope
) {
    private val mutex = Mutex()
    private val nextGeneration = AtomicLong(0L)
    private val activeGeneration = AtomicLong(0L)

    @Volatile
    private var inFlight: Deferred<Result<Unit>>? = null

    suspend fun awaitOrStart(operation: suspend (Long) -> Result<Unit>): Result<Unit> {
        val deferred = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: run {
                val generation = nextGeneration.incrementAndGet()
                activeGeneration.set(generation)
                processScope.async {
                    operation(generation)
                }.also { inFlight = it }
            }
        }
        // The Deferred is a child of processScope, never of the caller awaiting it.
        return deferred.await()
    }

    fun isCurrent(generation: Long): Boolean = activeGeneration.get() == generation

    fun invalidate(cause: CancellationException): Long {
        val invalidationGeneration = nextGeneration.incrementAndGet()
        activeGeneration.set(invalidationGeneration)
        inFlight?.cancel(cause)
        inFlight = null
        return invalidationGeneration
    }
}
