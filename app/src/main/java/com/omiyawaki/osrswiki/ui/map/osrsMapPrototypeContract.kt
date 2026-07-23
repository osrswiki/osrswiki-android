package com.omiyawaki.osrswiki.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import com.omiyawaki.osrswiki.databinding.FragmentMapBinding
import org.maplibre.android.maps.MapLibreMap

data class osrsMapPrototypeRuntimePolicy(
    val showControls: Boolean,
    val installOverlay: Boolean,
    val installHitTesting: Boolean
)

data class osrsMapPrototypeCameraDescriptor(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
)

data class osrsMapPrototypePadding(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int
)

data class osrsMapPrototypeHandoffState(
    val fragmentState: Bundle,
    val camera: osrsMapPrototypeCameraDescriptor,
    val floor: Int,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val padding: osrsMapPrototypePadding
)

data class osrsMapPrototypeTerrainCapture(
    val generation: Long,
    val handoffState: osrsMapPrototypeHandoffState,
    val bitmap: Bitmap
)

interface PrototypeTerrainPreviewHost {
    fun reservePrototypeTerrainGeneration(): Long

    fun onPrototypeTerrainPreview(
        capture: osrsMapPrototypeTerrainCapture,
        completion: (Boolean) -> Unit
    )
}

interface osrsMapPrototypeControllerContract {
    fun prepare()
    fun enable(maplibreMap: MapLibreMap)
    fun disable(maplibreMap: MapLibreMap?)
    fun onMapViewAttached()
    fun onFirstTerrainFrame(encodingMs: Double, renderingMs: Double)
    fun isInitialViewportReadyForTerrainHandoff(): Boolean
    fun loadingPreviewView(): View?
    fun saveState(outState: Bundle): Bundle
    fun diagnosticsForTesting(): osrsMapPrototypeDiagnostics?
    fun setZoomForTesting(zoom: Double): Boolean
    fun panPrototypeAwayAndBackForTesting(): Boolean
    fun performFeatureActionForTesting(featureId: String): Boolean
    fun hitFeatureIdForTesting(x: Float, y: Float): String?
    fun performSearchForTesting(query: String): Boolean
    fun toggleCategoryForTesting(categoryValue: String): Boolean
    fun setLayerVisibilityForTesting(labels: Boolean?, pois: Boolean?, links: Boolean?): Boolean
    fun setOverviewCenterForTesting(gameX: Double, gameY: Double): Boolean
    fun setCameraForTesting(gameX: Double, gameY: Double, zoom: Double): Boolean
    fun setCameraPoseForTesting(
        gameX: Double,
        gameY: Double,
        zoom: Double,
        bearing: Double,
        tilt: Double
    ): Boolean
    fun restorePreviousCameraForTesting(): Boolean
    fun selectSurfaceForTesting(surfaceId: String): Boolean
    fun viewportPaddingForHandoff(): osrsMapPrototypePadding
}

fun interface osrsMapPrototypeControllerFactory {
    fun create(
        binding: FragmentMapBinding,
        context: Context,
        mapProvider: () -> MapLibreMap?,
        logTag: String,
        restoredState: Bundle?
    ): osrsMapPrototypeControllerContract
}

object osrsMapPrototypeBridge {
    @Volatile
    private var factory: osrsMapPrototypeControllerFactory? = null

    fun install(controllerFactory: osrsMapPrototypeControllerFactory) {
        factory = controllerFactory
    }

    fun runtimePolicy(explicitlyEnabled: Boolean): osrsMapPrototypeRuntimePolicy {
        val enabled = explicitlyEnabled && factory != null
        return osrsMapPrototypeRuntimePolicy(
            showControls = enabled,
            installOverlay = enabled,
            installHitTesting = enabled
        )
    }

    fun createController(
        binding: FragmentMapBinding,
        context: Context,
        mapProvider: () -> MapLibreMap?,
        logTag: String,
        restoredState: Bundle?
    ): osrsMapPrototypeControllerContract? {
        return factory?.create(binding, context, mapProvider, logTag, restoredState)
    }
}

data class osrsMapPrototypeDiagnostics(
    val cameraZoom: Double,
    val cameraLatitude: Double?,
    val cameraLongitude: Double?,
    val cameraBearing: Double = 0.0,
    val cameraTilt: Double = 0.0,
    val renderedFeatureIdsByKind: Map<String, List<String>>,
    val sourceFeatureIdsByKind: Map<String, List<String>>,
    val semanticLayersPresent: Map<String, Boolean>,
    val layerOrder: List<String>,
    val featureScreenPoints: Map<String, osrsMapPrototypeScreenPoint>,
    val renderedFeatureBounds: Map<String, osrsMapPrototypeScreenBounds> = emptyMap(),
    val virtualTargetBounds: Map<String, osrsMapPrototypeScreenBounds> = emptyMap(),
    val mapContentBounds: osrsMapPrototypeScreenBounds? = null,
    val viewportPaddingTopPx: Int = 0,
    val viewportPaddingBottomPx: Int = 0,
    val semanticMetricsPx: Map<String, Double> = emptyMap(),
    val highlightedCategories: List<String> = emptyList(),
    val activeSurfaceId: String = "gielinor-surface",
    val overviewVisible: Boolean = true,
    val referenceStopPercent: Int? = 100,
    val featureActionMetadata: Map<String, String> = emptyMap(),
    val lastControlDurationMs: Double? = null,
    val lastHitFeatureId: String? = null,
    val lastActionDescription: String? = null,
    val searchQuery: String = "",
    val statusText: String? = null,
    val statusContentDescription: String? = null,
    val accessibilityHostDescription: String = "",
    val accessibilityVisibleFeatureIds: List<String> = emptyList(),
    val historyDepth: Int = 0,
    val currentNavigationResultId: String? = null,
    val elapsedRealtimeMs: Long
)

data class osrsMapPrototypeScreenPoint(
    val x: Float,
    val y: Float
)

data class osrsMapPrototypeScreenBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
