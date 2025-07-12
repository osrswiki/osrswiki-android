package com.omiyawaki.osrswiki.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
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
    private val maxFloor = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = binding.mapView

        lifecycleScope.launch {
            copyAssetsToInternalStorage()
            initializeMap(savedInstanceState)
        }
    }

    private suspend fun copyAssetsToInternalStorage() = withContext(Dispatchers.IO) {
        Log.d(logTag, "Checking for map assets in internal storage...")
        for (fileName in mapFiles) {
            val destFile = File(requireContext().filesDir, fileName)
            if (destFile.exists()) {
                Log.d(logTag, "Asset '$fileName' already exists. Skipping copy.")
                continue
            }
            Log.d(logTag, "Asset '$fileName' not found. Copying from APK assets...")
            try {
                requireContext().assets.open(fileName).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(logTag, "Successfully copied asset: $fileName")
            } catch (e: IOException) {
                Log.e(logTag, "Failed to copy asset file: $fileName", e)
            }
        }
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { maplibreMap ->
            this.map = maplibreMap
            val styleJson = """
                {
                  "version": 8,
                  "name": "OSRS Map Style",
                  "sources": {},
                  "layers": [
                    {
                      "id": "background",
                      "type": "background",
                      "paint": { "background-color": "#000000" }
                    }
                  ]
                }
            """.trimIndent()

            maplibreMap.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                maplibreMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(0.0, 0.0))
                    .zoom(4.0)
                    .build()

                setupMapLayers(style)
                setupFloorControls()
            }
        }
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
        Log.d(logTag, "All ${maxFloor + 1} map layers and sources have been added.")
    }

    private fun setupFloorControls() {
        binding.floorControls.visibility = View.VISIBLE
        updateFloorText()

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
    }

    private fun showFloor(newFloor: Int) {
        if (newFloor == currentFloor || newFloor < 0 || newFloor > maxFloor) return

        val style = map?.style
        if (style == null) {
            Log.e(logTag, "Cannot switch floor, map style is not ready.")
            return
        }

        Log.d(logTag, "Switching from floor $currentFloor to $newFloor")

        // This is the new logic to handle visibility and opacity.
        for (floor in 0..maxFloor) {
            val layer = style.getLayer("osrs-layer-$floor") as? RasterLayer
            layer?.let {
                when {
                    // The selected floor is always fully opaque and visible.
                    floor == newFloor -> {
                        it.setProperties(
                            PropertyFactory.visibility(Property.VISIBLE),
                            PropertyFactory.rasterOpacity(1.0f)
                        )
                    }
                    // If viewing an upper floor, make the ground floor semi-transparent.
                    floor == 0 && newFloor > 0 -> {
                        it.setProperties(
                            PropertyFactory.visibility(Property.VISIBLE),
                            PropertyFactory.rasterOpacity(0.5f)
                        )
                    }
                    // Hide all other layers.
                    else -> {
                        it.setProperties(
                            PropertyFactory.visibility(Property.NONE)
                        )
                    }
                }
            }
        }

        currentFloor = newFloor
        updateFloorText()
    }

    private fun updateFloorText() {
        binding.floorControlText.text = currentFloor.toString()
    }

    // region MapView Lifecycle
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView?.onDestroy(); _binding = null; map = null }
    // endregion
}
