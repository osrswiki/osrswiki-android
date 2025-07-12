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
    private val logTag = "MapFragment"

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
        binding.floorControls.visibility = View.GONE

        lifecycleScope.launch {
            copyTestAssetToInternalStorage()
            initializeMap(savedInstanceState)
        }
    }

    private suspend fun copyTestAssetToInternalStorage() = withContext(Dispatchers.IO) {
        val fileName = "map_floor_0_test.mbtiles"
        val destFile = File(requireContext().filesDir, fileName)
        if (destFile.exists()) {
            destFile.delete()
        }
        try {
            requireContext().assets.open(fileName).use { inputStream ->
                FileOutputStream(destFile).use { outputStream -> inputStream.copyTo(outputStream) }
            }
            Log.d(logTag, "Successfully copied test asset to internal storage.")
        } catch (e: IOException) {
            Log.e(logTag, "Failed to copy asset file: $fileName", e)
        }
    }

    private fun initializeMap(savedInstanceState: Bundle?) {
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { map ->
            map.setStyle(Style.Builder()) { style ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(0.0, 0.0))
                    .zoom(4.0)
                    .build()
                setupTestLayer(style)
            }
        }
    }
    
    private fun setupTestLayer(style: Style) {
        val sourceId = "osrs-test-source"
        val layerId = "osrs-test-layer"
        val testFileName = "map_floor_0_test.mbtiles"
        
        val mbtilesFile = File(requireContext().filesDir, testFileName)
        val mbtilesUri = "mbtiles://${mbtilesFile.absolutePath}"
        
        // Using the correct RasterSource(id, uri) constructor
        val rasterSource = RasterSource(sourceId, mbtilesUri)
        style.addSource(rasterSource)
        
        val rasterLayer = RasterLayer(layerId, sourceId).withProperties(
            PropertyFactory.rasterResampling("nearest")
        )
        style.addLayer(rasterLayer)
        Log.d(logTag, "OSRS test source and layer have been added to the map.")
    }

    // region MapView Lifecycle
    override fun onStart() { super.onStart(); mapView?.onStart() }
    override fun onResume() { super.onResume(); mapView?.onResume() }
    override fun onPause() { super.onPause(); mapView?.onPause() }
    override fun onStop() { super.onStop(); mapView?.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView?.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView?.onLowMemory() }
    override fun onDestroyView() { super.onDestroyView(); mapView?.onDestroy(); _binding = null }
    // endregion
}
