package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.R
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import com.omiyawaki.osrswiki.util.log.L
import com.omiyawaki.osrswiki.util.applyRubikUILabel
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
        
        mapView = binding.mapView

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
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { maplibreMap ->
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
    }

    private fun setMapBounds(map: MapLibreMap) {
        val north = 85.0511
        val west = -180.0
        val south = -85.0511
        val east = 180.0
        val bounds = LatLngBounds.Builder()
            .include(LatLng(north, west))
            .include(LatLng(south, east))
            .build()
        map.setLatLngBoundsForCameraTarget(bounds)
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
            val rasterLayer = RasterLayer(layerId, sourceId).withProperties(
                PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                PropertyFactory.visibility(if (floor == currentFloor) Property.VISIBLE else Property.NONE)
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
            
            // Apply font to floor control text
            binding.floorControlText.applyRubikUILabel()
            
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
                when {
                    floor == newFloor -> it.setProperties(
                        PropertyFactory.visibility(Property.VISIBLE),
                        PropertyFactory.rasterOpacity(1.0f)
                    )
                    floor == 0 && newFloor > 0 -> it.setProperties(
                        PropertyFactory.visibility(Property.VISIBLE),
                        PropertyFactory.rasterOpacity(GROUND_FLOOR_UNDERLAY_OPACITY)
                    )
                    else -> it.setProperties(PropertyFactory.visibility(Property.NONE))
                }
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

    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            mapView?.onStop()
        } else {
            mapView?.onStart()
        }
    }
}
