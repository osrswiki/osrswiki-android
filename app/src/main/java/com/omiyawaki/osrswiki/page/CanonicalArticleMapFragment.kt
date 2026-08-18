package com.omiyawaki.osrswiki.page

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omiyawaki.osrswiki.undergroundmaps.data.osrsRealmRepository
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsArticleMapRealmResolver
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsDefaultZoomForAsset
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsCameraCenterEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsClampCameraToEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsFiniteRealmMinimumZoom
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmCameraEnvelope
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmEndpointMapper
import com.omiyawaki.osrswiki.undergroundmaps.model.rasterProjectionOrNull
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsResolveMapLibreLongitudeRepresentation
import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraState
import com.omiyawaki.osrswiki.util.log.L
import kotlinx.coroutines.launch
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

/**
 * Interactive article map backed by the exact reviewed Gielinor Surface assets used by the Map
 * tab. This deliberately replaces the retired map_floor_*.mbtiles article stack so the app has
 * one raster provenance, one coordinate transform, and one floor-composition policy.
 */
class CanonicalArticleMapFragment : Fragment() {
    private lateinit var mapView: MapView
    private lateinit var repository: osrsRealmRepository
    var onInteractionChanged: ((Boolean) -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null
    var onFailure: ((Throwable) -> Unit)? = null
    private var renderListener: MapView.OnDidFinishRenderingFrameWithStatsListener? = null
    private var applyingFiniteClamp = false
    private var lastObservedLongitude: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(requireContext().applicationContext)
        repository = osrsRealmRepository(requireContext())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = MapView(requireContext()).also {
        mapView = it
        it.setBackgroundColor(Color.BLACK)
        it.contentDescription = "Interactive OSRS article map"
        it.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> onInteractionChanged?.invoke(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.post {
                    onInteractionChanged?.invoke(false)
                }
            }
            false
        }
        it.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
                isCompassEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            map.setStyle(Style.Builder().fromJson(BASE_STYLE_JSON)) { style ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val catalog = repository.loadCatalog()
                        val requestedPlane = arguments?.getString(ARG_PLANE)?.toIntOrNull() ?: 0
                        val gameX = arguments?.getString(ARG_LON)?.toDoubleOrNull()?.toInt() ?: 3200
                        val gameY = arguments?.getString(ARG_LAT)?.toDoubleOrNull()?.toInt() ?: 3200
                        val authoredMapId = arguments?.getString(ARG_MAP_ID)?.toIntOrNull()
                        val projection = catalog.manifest.rasterProjectionOrNull()
                            ?: error("Realm coordinate provenance is missing")
                        val mapper = osrsRealmEndpointMapper(projection)
                        val resolved = osrsArticleMapRealmResolver.resolve(
                            catalog = catalog,
                            mapper = mapper,
                            mapId = authoredMapId,
                            plane = requestedPlane,
                            gameX = gameX,
                            gameY = gameY
                        )
                        val selectedRealm = resolved.realm
                        val selected = selectedRealm.assetForPlane(resolved.plane)
                            ?: selectedRealm.assetForPlane(selectedRealm.defaultPlane)
                            ?: error("${selectedRealm.id} has no usable article-map plane")
                        L.d(
                            "CanonicalArticleMap: mapId=$authoredMapId plane=$requestedPlane " +
                                "game=($gameX,$gameY) realm=${selectedRealm.id}"
                        )
                        val visible = if (selected.plane > 0) {
                            listOfNotNull(selectedRealm.assetForPlane(0), selected).distinctBy { it.plane }
                        } else {
                            listOf(selected)
                        }
                        val staged = visible.associateWith { repository.stage(it) }
                        if (!isAdded) return@launch

                        visible.sortedBy { it.plane }.forEach { asset ->
                            val sourceId = "article-canonical-source-${asset.plane}"
                            val layerId = "article-canonical-layer-${asset.plane}"
                            val source = RasterSource(
                                sourceId,
                                "mbtiles://${staged.getValue(asset).file.absolutePath}",
                                asset.tileSize
                            )
                            style.addSource(source)
                            style.addLayer(
                                RasterLayer(layerId, sourceId).withProperties(
                                    PropertyFactory.rasterOpacity(
                                        if (asset.plane == selected.plane) 1f else 0.5f
                                    ),
                                    PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_NEAREST),
                                    PropertyFactory.rasterFadeDuration(0f)
                                )
                            )
                        }

                        val envelope = osrsCameraCenterEnvelope.fromVisibleAssets(visible)
                        installFiniteCameraContract(map, selected, envelope)
                        val target = resolved.destination?.let { LatLng(it.latitude, it.longitude) }
                            ?: LatLng(
                                (envelope.south + envelope.north) / 2.0,
                                (envelope.west + envelope.east) / 2.0
                            )
                        // Kartographer's zoom is tied to its retired tile pyramid. The canonical
                        // asset's audited relative zoom is the one scale that has identical meaning
                        // in the Map tab and an article embed.
                        map.cameraPosition = CameraPosition.Builder()
                            .target(target)
                            .zoom(osrsDefaultZoomForAsset(selected).coerceIn(
                                map.minZoomLevel,
                                osrsRealmCameraEnvelope.maxZoom(selected)
                            ))
                            .build()
                        installFirstFrameHandoff()
                        map.triggerRepaint()
                    } catch (error: Throwable) {
                        if (isAdded) onFailure?.invoke(error)
                    }
                }
            }
        }
    }

    private fun installFiniteCameraContract(
        map: MapLibreMap,
        selected: com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmAsset,
        envelope: osrsCameraCenterEnvelope
    ) {
        val width = mapView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = mapView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val minimumZoom = osrsFiniteRealmMinimumZoom(
            baseMinimumZoom = osrsRealmCameraEnvelope.minZoom(selected),
            envelope = envelope,
            viewportWidth = width.toDouble(),
            viewportHeight = height.toDouble()
        )
        map.setLatLngBoundsForCameraTarget(null)
        map.setMinZoomPreference(minimumZoom)
        map.setMaxZoomPreference(osrsRealmCameraEnvelope.maxZoom(selected))
        map.addOnCameraMoveListener {
            if (applyingFiniteClamp) return@addOnCameraMoveListener
            val current = map.cameraPosition
            val target = current.target ?: return@addOnCameraMoveListener
            val longitude = lastObservedLongitude?.let {
                osrsResolveMapLibreLongitudeRepresentation(it, target.longitude)
            } ?: target.longitude
            val final = osrsClampCameraToEnvelope(
                osrsCameraState(
                    latitude = target.latitude,
                    longitude = longitude,
                    zoom = current.zoom,
                    bearing = current.bearing,
                    tilt = current.tilt
                ),
                envelope
            ).final
            lastObservedLongitude = final.longitude
            if (final.latitude == target.latitude && final.longitude == longitude) return@addOnCameraMoveListener
            applyingFiniteClamp = true
            map.cancelAllVelocityAnimations()
            map.cancelTransitions()
            map.cameraPosition = CameraPosition.Builder(current)
                .target(LatLng(final.latitude, final.longitude))
                .build()
            applyingFiniteClamp = false
        }
    }

    private fun installFirstFrameHandoff() {
        renderListener?.let(mapView::removeOnDidFinishRenderingFrameListener)
        val listener = MapView.OnDidFinishRenderingFrameWithStatsListener { fully, _ ->
            if (!fully) return@OnDidFinishRenderingFrameWithStatsListener
            renderListener?.let(mapView::removeOnDidFinishRenderingFrameListener)
            renderListener = null
            onFirstFrame?.invoke()
        }
        renderListener = listener
        mapView.addOnDidFinishRenderingFrameListener(listener)
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroyView() {
        onInteractionChanged?.invoke(false)
        renderListener?.let(mapView::removeOnDidFinishRenderingFrameListener)
        renderListener = null
        mapView.onDestroy()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_LAT = "article-map-lat"
        private const val ARG_LON = "article-map-lon"
        private const val ARG_PLANE = "article-map-plane"
        private const val ARG_MAP_ID = "article-map-id"
        private val BASE_STYLE_JSON = """
            {
              "version": 8,
              "name": "OSRS canonical article map",
              "sources": {},
              "layers": [{
                "id": "article-map-background",
                "type": "background",
                "paint": { "background-color": "#000000" }
              }]
            }
        """.trimIndent()

        fun newInstance(lat: String?, lon: String?, plane: String?, mapId: String? = null) =
            CanonicalArticleMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LAT, lat)
                    putString(ARG_LON, lon)
                    putString(ARG_PLANE, plane)
                    putString(ARG_MAP_ID, mapId)
                }
            }
    }
}
