package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.FileOutputStream
import java.io.IOException

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var mapView: MapView? = null
    private var mapContainer: android.widget.FrameLayout? = null
    private var map: MapLibreMap? = null
    private val logTag = "MapFragment"

    private var currentFloor = 0
    private val mapFiles = (0..3).map { "map_floor_$it.mbtiles" }
    private val maxFloor: Int get() = mapFiles.size - 1

    companion object {
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LON = "arg_lon"
        private const val ARG_ZOOM = "arg_zoom"
        private const val ARG_PLANE = "arg_plane"
        private const val ARG_IS_PRELOADING = "arg_is_preloading"
        private const val GROUND_FLOOR_UNDERLAY_OPACITY = 0.5f
        private const val DEFAULT_LAT = -25.2023457171692
        private const val DEFAULT_LON = -131.44071698586012
        private const val DEFAULT_ZOOM = 7.3414426741929

        fun newInstance(lat: String?, lon: String?, zoom: String?, plane: String?, isPreloading: Boolean = false): MapFragment {
            return MapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LAT, lat)
                    putString(ARG_LON, lon)
                    putString(ARG_ZOOM, zoom)
                    putString(ARG_PLANE, plane)
                    putBoolean(ARG_IS_PRELOADING, isPreloading)
                }
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("MapFragment: LIFECYCLE: onCreate")
        MapLibre.getInstance(requireContext())
        arguments?.getString(ARG_PLANE)?.toIntOrNull()?.let {
            currentFloor = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("MapFragment: LIFECYCLE: onCreateView")
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("MapFragment: LIFECYCLE: onViewCreated")
        
        mapContainer = binding.mapView
        
        // Create the actual MapView programmatically and add it to the container
        mapView = MapView(requireContext())
        mapContainer?.addView(mapView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        lifecycleScope.launch {
            copyAssetsToInternalStorage()
            initializeMap(savedInstanceState)
        }
    }

    private suspend fun copyAssetsToInternalStorage() = withContext(Dispatchers.IO) {
        for (fileName in mapFiles) {
            val destFile = File(requireContext().filesDir, fileName)
            if (destFile.exists()) continue
            try {
                requireContext().assets.open(fileName).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream -> inputStream.copyTo(outputStream) }
                }
            } catch (e: IOException) {
                Log.e(logTag, "Failed to copy asset file: $fileName", e)
            }
        }
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        L.d("MapFragment: INIT: initializeMap() called")
        mapView?.onCreate(savedInstanceState)
        L.d("MapFragment: INIT: mapView.onCreate() called")
        mapView?.getMapAsync { maplibreMap ->
            L.d("MapFragment: INIT: MapLibre map async callback received")
            this.map = maplibreMap
            configureUiSettings(maplibreMap)
            val styleJson = """
                {
                    "version": 8,
                    "name": "OSRS Map Style",
                    "sources": {},
                    "layers": [
                        { "id": "background", "type": "background", "paint": { "background-color": "#000000" } }
                    ]
                }
            """.trimIndent()
            maplibreMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                val gameLon = arguments?.getString(ARG_LON)?.toDoubleOrNull()
                val gameLat = arguments?.getString(ARG_LAT)?.toDoubleOrNull()
                val zoom = 6.0
                val cameraBuilder = CameraPosition.Builder()
                if (gameLon != null && gameLat != null) {
                    val target = gameToLatLng(gameLon, gameLat)
                    cameraBuilder.target(target).zoom(zoom)
                } else {
                    cameraBuilder.target(LatLng(DEFAULT_LAT, DEFAULT_LON)).zoom(DEFAULT_ZOOM)
                }
                maplibreMap.cameraPosition = cameraBuilder.build()
                setMapBounds(maplibreMap)
                setupMapLayers(style)
                setupFloorControls()
            }
        }
    }

    private fun configureUiSettings(map: MapLibreMap) {
        val uiSettings = map.uiSettings
        uiSettings.isLogoEnabled = false
        uiSettings.isAttributionEnabled = false
        val marginInDp = 16f
        val marginInPx = (marginInDp * resources.displayMetrics.density).toInt()
        uiSettings.setCompassGravity(Gravity.TOP or Gravity.END)
        uiSettings.setCompassMargins(0, marginInPx, marginInPx, 0)
        
        // Set zoom limits: min zoom 0, max zoom 12
        map.setMinZoomPreference(0.0)
        map.setMaxZoomPreference(12.0)
    }

    private fun setMapBounds(map: MapLibreMap) {
        // Calculate OSRS map bounds based on actual game coordinate range
        // Source image: 12800x45568 pixels, scale: 4 pixels per game unit
        val gameMinX = 1024.0
        val gameMaxX = gameMinX + (12800.0 / 4.0) // gameMinX + image_width / scale = 1024 + 3200 = 4224
        val gameMaxY = 12608.0
        val gameMinY = gameMaxY - (45568.0 / 4.0) // gameMaxY - image_height / scale = 12608 - 11392 = 1216
        
        // Convert game coordinates to lat/lng bounds
        val northWest = gameToLatLng(gameMinX, gameMinY)
        val southEast = gameToLatLng(gameMaxX, gameMaxY)
        
        val bounds = LatLngBounds.Builder()
            .include(northWest)
            .include(southEast)
            .build()
        
        // Use proper bounds that restrict the entire visible area, not just camera center
        map.setLatLngBoundsForCameraTarget(bounds)
        
        Log.d(logTag, "OSRS bounds set:")
        Log.d(logTag, "- Game coords: X($gameMinX-$gameMaxX), Y($gameMinY-$gameMaxY)")
        Log.d(logTag, "- LatLng bounds: NW(${northWest.latitude}, ${northWest.longitude}), SE(${southEast.latitude}, ${southEast.longitude})")
        Log.d(logTag, "- Bounds applied to camera target restrictions")
    }

    private fun setupMapLayers(style: Style) {
        for (floor in 0..maxFloor) {
            val sourceId = "osrs-source-$floor"
            val layerId = "osrs-layer-$floor"
            val fileName = mapFiles[floor]
            val mbtilesFile = File(requireContext().filesDir, fileName)
            val mbtilesUri = "mbtiles://${mbtilesFile.absolutePath}"
            val rasterSource = RasterSource(sourceId, mbtilesUri)
            style.addSource(rasterSource)
            
            // Initially make all layers visible with appropriate opacity to trigger rendering
            val rasterLayer = RasterLayer(layerId, sourceId).withProperties(
                PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                PropertyFactory.visibility(Property.VISIBLE),
                PropertyFactory.rasterOpacity(when {
                    floor == currentFloor -> 1.0f
                    floor == 0 && currentFloor > 0 -> GROUND_FLOOR_UNDERLAY_OPACITY
                    else -> 0.0f // Invisible but still rendered to eliminate pixelation
                })
            )
            style.addLayer(rasterLayer)
        }
    }

    private fun setupFloorControls() {
        val isEmbeddedMap = arguments?.getString(ARG_LON) != null
        if (isEmbeddedMap) {
            binding.floorControls.visibility = View.GONE
            return
        }
        if (maxFloor > 0) {
            binding.floorControls.visibility = View.VISIBLE
            updateFloorControlStates()
            binding.floorControlUp.setOnClickListener {
                if (currentFloor < maxFloor) showFloor(currentFloor + 1)
            }
            binding.floorControlDown.setOnClickListener {
                if (currentFloor > 0) showFloor(currentFloor - 1)
            }
        } else {
            binding.floorControls.visibility = View.GONE
        }
    }

    private fun showFloor(newFloor: Int) {
        if (newFloor == currentFloor || newFloor < 0 || newFloor > maxFloor) return
        val style = map?.style ?: return
        for (floor in 0..maxFloor) {
            val layer = style.getLayer("osrs-layer-$floor") as? RasterLayer
            layer?.let {
                // All layers remain visible but use opacity for floor switching
                val opacity = when {
                    floor == newFloor -> 1.0f
                    floor == 0 && newFloor > 0 -> GROUND_FLOOR_UNDERLAY_OPACITY
                    else -> 0.0f
                }
                it.setProperties(PropertyFactory.rasterOpacity(opacity))
            }
        }
        currentFloor = newFloor
        updateFloorControlStates()
    }

    private fun updateFloorControlStates() {
        binding.floorControlText.text = currentFloor.toString()
        binding.floorControlUp.isEnabled = currentFloor < maxFloor
        binding.floorControlDown.isEnabled = currentFloor > 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDestroy()
        _binding = null
        map = null
    }

    // NUCLEAR OPTION: Removed ThemeAware interface and onThemeChanged
    // Theme switching now forces complete activity recreation to avoid window surface corruption

    override fun onStart() { 
        L.d("MapFragment: LIFECYCLE: onStart")
        super.onStart(); mapView?.onStart() 
    }
    override fun onResume() { 
        L.d("MapFragment: LIFECYCLE: onResume")
        super.onResume(); mapView?.onResume() 
    }
    override fun onPause() { 
        L.d("MapFragment: LIFECYCLE: onPause - MapView paused")
        super.onPause(); mapView?.onPause() 
    }
    override fun onStop() { 
        L.d("MapFragment: LIFECYCLE: onStop - MapView stopped")
        super.onStop(); mapView?.onStop() 
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        L.d("MapFragment: LIFECYCLE: onHiddenChanged(hidden=$hidden)")
        if (hidden) {
            L.d("MapFragment: Fragment hidden - calling mapView?.onStop()")
            mapView?.onStop()
        } else {
            L.d("MapFragment: Fragment shown - calling mapView?.onStart()")
            mapView?.onStart()
        }
    }
}
