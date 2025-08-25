package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap

/**
 * MapFragment that uses AndroidMapPreloader for background preloading
 * This version is designed to work with standard Fragment navigation patterns
 * (ViewPager2, FragmentTransaction show/hide/replace) while maintaining instant tile display
 */
class StandardNavigationMapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var attachedMapView: MapView? = null
    private var map: MapLibreMap? = null
    private val logTag = "StandardMapFragment"

    private var currentFloor = 0
    private val maxFloor = 3
    
    companion object {
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LON = "arg_lon"
        private const val ARG_ZOOM = "arg_zoom"
        private const val ARG_PLANE = "arg_plane"

        fun newInstance(lat: String?, lon: String?, zoom: String?, plane: String?): StandardNavigationMapFragment {
            return StandardNavigationMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LAT, lat)
                    putString(ARG_LON, lon)
                    putString(ARG_ZOOM, zoom)
                    putString(ARG_PLANE, plane)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.d("$logTag: LIFECYCLE: onCreate")
        arguments?.getString(ARG_PLANE)?.toIntOrNull()?.let {
            currentFloor = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        L.d("$logTag: LIFECYCLE: onCreateView")
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        L.d("$logTag: LIFECYCLE: onViewCreated")
        
        // Set up floor controls
        setupFloorControls()
        
        // Instead of creating a new MapView, try to attach the shared one
        attachSharedMapView()
    }

    private fun attachSharedMapView() {
        val preloader = AndroidMapPreloader.getInstance()
        
        if (preloader.isMapReady) {
            L.d("$logTag: ✅ Using shared map instance - instant display!")
            
            // Clear the container first  
            binding.mapView.removeAllViews()
            
            // Attach shared map to our container
            val sharedMapView = preloader.attachToMainMapContainer(binding.mapView)
            attachedMapView = sharedMapView
            
            if (sharedMapView != null) {
                // Get reference to the MapLibreMap instance
                sharedMapView.getMapAsync { maplibreMap ->
                    this.map = maplibreMap
                    L.d("$logTag: Shared map attached and ready!")
                }
            }
            
        } else {
            L.w("$logTag: Shared map not ready - waiting for preloader...")
            waitForPreloaderAndAttach()
        }
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
        
        // Observe both mapPreloaded and allLayersReady
        preloader.mapPreloaded.observe(viewLifecycleOwner) { isPreloaded ->
            L.d("$logTag: mapPreloaded changed to: $isPreloaded")
            if (isPreloaded && preloader.allLayersReady.value == true) {
                L.d("$logTag: Both mapPreloaded and allLayersReady are true, attaching shared map")
                attachSharedMapViewNow(preloader)
            }
        }
        
        preloader.allLayersReady.observe(viewLifecycleOwner) { layersReady ->
            L.d("$logTag: allLayersReady changed to: $layersReady")
            if (layersReady && preloader.mapPreloaded.value == true) {
                L.d("$logTag: Both mapPreloaded and allLayersReady are true, attaching shared map")
                attachSharedMapViewNow(preloader)
            }
        }
    }
    
    private fun attachSharedMapViewNow(preloader: AndroidMapPreloader) {
        // Clear the container first
        binding.mapView.removeAllViews()
        
        // Attach shared map to our container
        val sharedMapView = preloader.attachToMainMapContainer(binding.mapView)
        attachedMapView = sharedMapView
        
        if (sharedMapView != null) {
            // Get reference to the MapLibreMap instance
            sharedMapView.getMapAsync { maplibreMap ->
                this.map = maplibreMap
                L.d("$logTag: Shared map attached successfully!")
            }
        } else {
            L.e("$logTag: Failed to attach shared map - sharedMapView is null")
        }
    }

    /**
     * Fallback to standard MapView creation if shared map isn't ready
     * This ensures the fragment still works even if preloading failed
     */
    private fun fallbackToStandardMapCreation() {
        L.d("$logTag: Creating fallback MapView")
        
        lifecycleScope.launch {
            // This would be the standard MapFragment initialization
            // For now, just log that we would create a standard map
            L.w("$logTag: Would create standard MapView as fallback")
        }
    }

    override fun onStart() { 
        L.d("$logTag: LIFECYCLE: onStart")
        super.onStart() 
        attachedMapView?.onStart()
    }
    
    override fun onResume() { 
        L.d("$logTag: LIFECYCLE: onResume")
        super.onResume() 
        attachedMapView?.onResume()
    }
    
    override fun onPause() { 
        L.d("$logTag: LIFECYCLE: onPause - MapView paused")
        super.onPause() 
        attachedMapView?.onPause()
    }
    
    override fun onStop() { 
        L.d("$logTag: LIFECYCLE: onStop - MapView stopped")  
        super.onStop() 
        attachedMapView?.onStop()
    }

    override fun onDestroyView() {
        L.d("$logTag: LIFECYCLE: onDestroyView")
        super.onDestroyView()
        
        // CRITICAL: Do NOT destroy the shared MapView!
        // Just detach it from our container
        if (attachedMapView != null) {
            binding.mapView.removeView(attachedMapView)
            L.d("$logTag: Detached shared map from container (preserved for reuse)")
        }
        
        _binding = null
        map = null
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
            updateFloorControlStates()
            
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
        } else {
            binding.floorControls.visibility = View.GONE
        }
    }
    
    private fun showFloor(newFloor: Int) {
        if (newFloor == currentFloor || newFloor < 0 || newFloor > maxFloor) return
        
        currentFloor = newFloor
        AndroidMapPreloader.getInstance().updateFloor(currentFloor)
        updateFloorControlStates()
        
        L.d("$logTag: Changed to floor $currentFloor")
    }
    
    private fun updateFloorControlStates() {
        binding.floorControlText.text = currentFloor.toString()
        binding.floorControlUp.alpha = if (currentFloor < maxFloor) 1.0f else 0.4f
        binding.floorControlDown.alpha = if (currentFloor > 0) 1.0f else 0.4f
        binding.floorControlUp.isEnabled = currentFloor < maxFloor
        binding.floorControlDown.isEnabled = currentFloor > 0
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        L.d("$logTag: LIFECYCLE: onHiddenChanged(hidden=$hidden)")
        
        if (hidden) {
            L.d("$logTag: Fragment hidden - calling mapView?.onPause()")
            attachedMapView?.onPause()
        } else {
            L.d("$logTag: Fragment shown - calling mapView?.onResume()")
            attachedMapView?.onResume()
        }
    }
}