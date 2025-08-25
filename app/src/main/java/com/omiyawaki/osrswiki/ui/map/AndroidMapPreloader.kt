package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import java.io.File

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
    
    companion object {
        @Volatile
        private var INSTANCE: AndroidMapPreloader? = null
        
        fun getInstance(): AndroidMapPreloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AndroidMapPreloader().also { INSTANCE = it }
            }
        }
        
        private const val DEFAULT_LAT = -25.2023457171692
        private const val DEFAULT_LON = -131.44071698586012
        private const val DEFAULT_ZOOM = 7.3414426741929
        private const val GROUND_FLOOR_UNDERLAY_OPACITY = 0.5f
    }
    
    // SHARED map instance that gets reused by the main map view
    private var sharedMapView: MapView? = null
    private var sharedMapContainer: FrameLayout? = null
    private var sharedMap: MapLibreMap? = null
    
    // State tracking
    private val _isPreloadingMap = MutableLiveData(false)
    val isPreloadingMap: LiveData<Boolean> = _isPreloadingMap
    
    private val _preloadingProgress = MutableLiveData(0.0)
    val preloadingProgress: LiveData<Double> = _preloadingProgress
    
    private val _mapPreloaded = MutableLiveData(false)
    val mapPreloaded: LiveData<Boolean> = _mapPreloaded
    
    private val _allLayersReady = MutableLiveData(false)
    val allLayersReady: LiveData<Boolean> = _allLayersReady
    
    private var currentFloor = 0
    private val mapFiles = (0..3).map { "map_floor_$it.mbtiles" }
    
    val isMapReady: Boolean
        get() = mapPreloaded.value == true && allLayersReady.value == true
    
    /**
     * Create the shared MapLibre instance with all layers pre-created
     * This should be called during app launch for background preloading
     */
    suspend fun preloadMapInBackground(context: Context) = withContext(Dispatchers.Main) {
        if (isPreloadingMap.value == true) {
            L.d("AndroidMapPreloader: Background map preloading already in progress")
            return@withContext
        }
        
        _isPreloadingMap.value = true
        _preloadingProgress.value = 0.0
        _mapPreloaded.value = false
        _allLayersReady.value = false
        
        L.d("AndroidMapPreloader: PRIORITY: Creating shared MapLibre instance with all layers...")
        
        try {
            // Initialize MapLibre SDK first - required before any MapView creation
            MapLibre.getInstance(context)
            L.d("AndroidMapPreloader: MapLibre SDK initialized")
            
            // Step 1: Create the shared MapView
            updateProgress(0.1, "Creating shared MapView instance...")
            createSharedMapView(context)
            
            // Step 2: Wait for style to load
            updateProgress(0.3, "Loading map style...")
            waitForStyleToLoad()
            
            // Step 3: Pre-create all floor layers
            updateProgress(0.5, "Pre-creating all floor layers...")
            preCreateAllFloorLayers(context)
            
            // Step 4: Mark as ready
            updateProgress(1.0, "Shared map instance ready!")
            _mapPreloaded.value = true
            _allLayersReady.value = true
            _isPreloadingMap.value = false
            
            L.d("AndroidMapPreloader: ✅ PRIORITY: Shared MapLibre instance ready - main map can reuse it instantly!")
            
        } catch (e: Exception) {
            L.e("AndroidMapPreloader: Error during preloading: ${e.message}")
            _isPreloadingMap.value = false
        }
    }
    
    /**
     * Create the shared MapView that will be reused
     * Positioned off-screen to avoid interfering with UI
     */
    private fun createSharedMapView(context: Context) {
        // Create off-screen container (position it outside visible area)
        sharedMapContainer = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(400, 600)
            x = -2000f
            y = -2000f
        }
        
        sharedMapView = MapView(context).apply {
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
    private suspend fun waitForStyleToLoad() = withContext(Dispatchers.Main) {
        // Step 1: Wait for MapLibreMap to be ready
        sharedMap = suspendCancellableCoroutine { continuation ->
            sharedMapView?.getMapAsync { maplibreMap ->
                L.d("AndroidMapPreloader: MapLibreMap instance ready")
                continuation.resume(maplibreMap)
            }
        }
        
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
                        L.d("AndroidMapPreloader: Map style loaded successfully from file")
                        continuation.resume(style)
                    }
                } catch (e: Exception) {
                    L.e("AndroidMapPreloader: Failed to create style file", e)
                    // Fallback to inline JSON
                    val styleJson = createMapStyle()
                    maplibreMap.setStyle(styleJson) { style ->
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
        
        // Set position to Lumbridge (default position)
        val center = LatLng(DEFAULT_LAT, DEFAULT_LON)
        maplibreMap.cameraPosition = CameraPosition.Builder()
            .target(center)
            .zoom(DEFAULT_ZOOM)
            .build()
            
        L.d("AndroidMapPreloader: Initial zoom set to $DEFAULT_ZOOM, center: $center")
        
        // Set OSRS map bounds to prevent panning beyond actual game content
        // Source image: 12800x45568 pixels, scale: 4 pixels per game unit
        val gameMinX = 1024.0
        val gameMaxX = gameMinX + (12800.0 / 4.0) // gameMinX + image_width / scale = 1024 + 3200 = 4224
        val gameMaxY = 12608.0
        val gameMinY = gameMaxY - (45568.0 / 4.0) // gameMaxY - image_height / scale = 12608 - 11392 = 1216
        
        // Convert game coordinates to lat/lng bounds
        val northWest = gameToLatLng(gameMinX, gameMinY)
        val southEast = gameToLatLng(gameMaxX, gameMaxY)
        
        val osrsMapBounds = LatLngBounds.Builder()
            .include(northWest)
            .include(southEast)
            .build()
            
        // Use proper bounds that restrict the entire visible area, not just camera center
        maplibreMap.setLatLngBoundsForCameraTarget(osrsMapBounds)
        
        L.d("AndroidMapPreloader: OSRS bounds set:")
        L.d("AndroidMapPreloader: - Game coords: X($gameMinX-$gameMaxX), Y($gameMinY-$gameMaxY)")
        L.d("AndroidMapPreloader: - LatLng bounds: NW(${northWest.latitude}, ${northWest.longitude}), SE(${southEast.latitude}, ${southEast.longitude})")
        L.d("AndroidMapPreloader: - Bounds applied to camera target restrictions")
        
        // Restore proper zoom after bounds operation
        if (maplibreMap.cameraPosition.zoom < 1.0) {
            maplibreMap.cameraPosition = CameraPosition.Builder()
                .target(center)
                .zoom(DEFAULT_ZOOM)
                .build()
            L.d("AndroidMapPreloader: Fixed zoom: ${maplibreMap.cameraPosition.zoom}, center: ${maplibreMap.cameraPosition.target}")
        }
    }
    
    /**
     * Pre-create all floor layers for instant floor switching
     */
    private suspend fun preCreateAllFloorLayers(context: Context) = withContext(Dispatchers.IO) {
        // Copy map assets to internal storage
        copyMapAssets(context)
        
        withContext(Dispatchers.Main) {
            val style = sharedMap?.style
            if (style == null) {
                L.e("AndroidMapPreloader: Style not available for layer creation")
                return@withContext
            }
            
            // Add all floor layers
            for (floor in 0..3) {
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
    private fun copyMapAssets(context: Context) {
        for (fileName in mapFiles) {
            val destFile = File(context.filesDir, fileName)
            if (destFile.exists()) continue
            
            try {
                context.assets.open(fileName).use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                L.d("AndroidMapPreloader: Copied asset: $fileName")
            } catch (e: Exception) {
                L.e("AndroidMapPreloader: Failed to copy asset file: $fileName", e)
            }
        }
    }
    
    /**
     * Move the shared map to be visible in the main map container
     * This is equivalent to iOS attachToMainMapContainer
     */
    fun attachToMainMapContainer(mainContainer: ViewGroup): MapView? {
        val mapView = sharedMapView
        if (mapView == null) {
            L.e("AndroidMapPreloader: Shared map not ready for attachment")
            return null
        }
        
        L.d("AndroidMapPreloader: Attaching shared map to main container")
        
        // Remove from off-screen container
        sharedMapContainer?.removeView(mapView)
        
        // Add to main container and make visible
        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        mapView.visibility = View.VISIBLE  // Make visible when properly attached
        mainContainer.addView(mapView, layoutParams)
        
        L.d("AndroidMapPreloader: ✅ Shared map attached to main container - instant display!")
        
        return mapView
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
    
    private fun updateProgress(progress: Double, message: String) {
        _preloadingProgress.value = progress
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
    
    private fun gameToLatLng(gx: Double, gy: Double): LatLng {
        val gameCoordScale = 4.0
        val gameMinX = 1024.0
        val gameMaxY = 12608.0
        val canvasSize = 65536.0
        val px = (gx - gameMinX) * gameCoordScale
        val py = (gameMaxY - gy) * gameCoordScale
        val nx = px / canvasSize
        val ny = py / canvasSize
        val lon = -180.0 + nx * 360.0
        val lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * ny))))
        return LatLng(lat, lon)
    }
}

