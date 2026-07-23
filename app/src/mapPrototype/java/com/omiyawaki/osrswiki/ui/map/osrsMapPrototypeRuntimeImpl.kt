package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.os.Bundle
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import org.maplibre.android.maps.MapLibreMap

object osrsMapPrototypeRuntimeInstaller {
    fun install() {
        osrsMapPrototypeBridge.install(
            osrsMapPrototypeControllerFactory { binding, context, mapProvider, logTag, restoredState ->
                osrsMapPrototypeControllerAdapter(
                    osrsMapPrototypeController(
                        binding = binding,
                        context = context,
                        mapProvider = mapProvider,
                        logTag = logTag,
                        restoredState = restoredState
                    )
                )
            }
        )
    }
}

private class osrsMapPrototypeControllerAdapter(
    private val delegate: osrsMapPrototypeController
) : osrsMapPrototypeControllerContract {
    override fun prepare() = delegate.prepare()

    override fun enable(maplibreMap: MapLibreMap) = delegate.enable(maplibreMap)

    override fun disable(maplibreMap: MapLibreMap?) = delegate.disable(maplibreMap)

    override fun onMapViewAttached() = delegate.onMapViewAttached()

    override fun onFirstTerrainFrame(encodingMs: Double, renderingMs: Double) {
        delegate.onFirstTerrainFrame(encodingMs, renderingMs)
    }

    override fun isInitialViewportReadyForTerrainHandoff(): Boolean {
        return delegate.isInitialViewportReadyForTerrainHandoff()
    }

    override fun loadingPreviewView() = delegate.loadingPreviewView()

    override fun saveState(outState: Bundle): Bundle = delegate.saveState(outState)

    override fun diagnosticsForTesting(): osrsMapPrototypeDiagnostics? {
        return delegate.diagnosticsForTesting()
    }

    override fun setZoomForTesting(zoom: Double): Boolean = delegate.setZoomForTesting(zoom)

    override fun panPrototypeAwayAndBackForTesting(): Boolean {
        return delegate.panPrototypeAwayAndBackForTesting()
    }

    override fun performFeatureActionForTesting(featureId: String): Boolean {
        return delegate.performFeatureActionForTesting(featureId)
    }

    override fun hitFeatureIdForTesting(x: Float, y: Float): String? {
        return delegate.hitFeatureIdForTesting(x, y)
    }

    override fun performSearchForTesting(query: String): Boolean {
        return delegate.performSearchForTesting(query)
    }

    override fun toggleCategoryForTesting(categoryValue: String): Boolean {
        val category = osrsMapCategory.entries.firstOrNull { it.value == categoryValue } ?: return false
        return delegate.toggleCategoryForTesting(category)
    }

    override fun setLayerVisibilityForTesting(
        labels: Boolean?,
        pois: Boolean?,
        links: Boolean?
    ): Boolean = delegate.setLayerVisibilityForTesting(labels, pois, links)

    override fun setOverviewCenterForTesting(gameX: Double, gameY: Double): Boolean {
        return delegate.setOverviewCenterForTesting(gameX, gameY)
    }

    override fun setCameraForTesting(gameX: Double, gameY: Double, zoom: Double): Boolean {
        return delegate.setCameraForTesting(gameX, gameY, zoom)
    }

    override fun setCameraPoseForTesting(
        gameX: Double,
        gameY: Double,
        zoom: Double,
        bearing: Double,
        tilt: Double
    ): Boolean = delegate.setCameraPoseForTesting(gameX, gameY, zoom, bearing, tilt)

    override fun restorePreviousCameraForTesting(): Boolean {
        return delegate.restorePreviousCameraForTesting()
    }

    override fun selectSurfaceForTesting(surfaceId: String): Boolean {
        return delegate.selectSurfaceForTesting(surfaceId)
    }

    override fun viewportPaddingForHandoff(): osrsMapPrototypePadding {
        return delegate.viewportPaddingForHandoff()
    }
}
