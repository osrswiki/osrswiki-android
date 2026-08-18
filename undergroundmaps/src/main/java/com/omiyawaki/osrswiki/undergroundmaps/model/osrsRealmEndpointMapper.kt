package com.omiyawaki.osrswiki.undergroundmaps.model

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh

/** Realm-local MapLibre destination derived from one authoritative cache endpoint. */
data class osrsRealmEndpointDestination(
    val realmId: String,
    val plane: Int,
    val gameX: Int,
    val gameY: Int,
    val assetPixelX: Double,
    val assetPixelY: Double,
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val matchingLayoutCount: Int
)

data class osrsRelativeLinkZoomResult(
    val sourceZoom: Double,
    val sourceNativeMaxZoom: Int,
    val targetNativeMaxZoom: Int,
    val relativeZoom: Double,
    val requestedTargetZoom: Double,
    val finalTargetZoom: Double,
    val targetMinZoom: Double,
    val targetMaxZoom: Double,
    val clampState: String
)

/**
 * Preserves the apparent scale of the shared pre-realm-selector surface camera after the
 * surface raster is placed in a differently sized Web Mercator canvas. A zoom value alone is
 * not portable across those coordinate frames: halving the canvas requires subtracting one
 * zoom level to keep the same source-pixel density on screen.
 */
fun osrsDefaultZoomForAsset(asset: osrsRealmAsset): Double {
    require(asset.canvasSize > 0) { "Realm asset canvas must be positive" }
    return osrsUndergroundMapDefaultView.ZOOM + log2(
        asset.canvasSize.toDouble() / osrsUndergroundMapDefaultView.CANVAS_SIZE
    )
}

fun osrsSurfaceDefaultZoomForAsset(asset: osrsRealmAsset): Double =
    osrsDefaultZoomForAsset(asset)

fun osrsRelativeLinkZoomForAssets(
    currentZoom: Double,
    sourceAsset: osrsRealmAsset,
    targetAsset: osrsRealmAsset
): osrsRelativeLinkZoomResult {
    require(currentZoom.isFinite()) { "Source camera zoom must be finite" }
    val targetMinZoom = osrsRealmCameraEnvelope.minZoom(targetAsset)
    val targetMaxZoom = osrsRealmCameraEnvelope.maxZoom(targetAsset)
    val relativeZoom = currentZoom - sourceAsset.maxZoom
    val requestedTargetZoom = targetAsset.maxZoom + relativeZoom
    val finalTargetZoom = requestedTargetZoom.coerceIn(targetMinZoom, targetMaxZoom)
    val clampState = when (finalTargetZoom) {
        requestedTargetZoom -> "none"
        targetMinZoom -> "min"
        targetMaxZoom -> "max"
        else -> "unknown"
    }
    return osrsRelativeLinkZoomResult(
        sourceZoom = currentZoom,
        sourceNativeMaxZoom = sourceAsset.maxZoom,
        targetNativeMaxZoom = targetAsset.maxZoom,
        relativeZoom = relativeZoom,
        requestedTargetZoom = requestedTargetZoom,
        finalTargetZoom = finalTargetZoom,
        targetMinZoom = targetMinZoom,
        targetMaxZoom = targetMaxZoom,
        clampState = clampState
    )
}

/**
 * Projects cache game coordinates through the exact source-to-realm layout recorded in the
 * release manifest. No semantic or manually captured coordinate is involved.
 */
class osrsRealmEndpointMapper(
    private val projection: osrsRealmRasterProjection
) {
    fun map(
        realm: osrsRealmRecord,
        position: osrsRealmLinkPosition
    ): osrsRealmEndpointDestination? {
        val asset = realm.assetForPlane(position.plane) ?: return null
        val sourcePixelX = (position.x - projection.gameMinX + 0.5) * projection.scale
        val sourcePixelY = (projection.gameMaxY - position.y - 0.5) * projection.scale
        if (sourcePixelX !in 0.0..<projection.width.toDouble() ||
            sourcePixelY !in 0.0..<projection.height.toDouble()) {
            return null
        }

        val placements = asset.layoutComponents.mapNotNull { component ->
            val source = component.sourcePixelBounds
            if (!source.contains(sourcePixelX, sourcePixelY)) return@mapNotNull null
            val target = component.assetPixelBounds
            val assetPixelX = target.minX + sourcePixelX - source.minX
            val assetPixelY = target.minY + sourcePixelY - source.minY
            // Layout targets use shared padded-canvas coordinates. `width` and
            // `height` describe the unpadded rendered source and therefore are
            // not the legal coordinate extent after producer translation.
            if (assetPixelX !in 0.0..<asset.canvasSize.toDouble() ||
                assetPixelY !in 0.0..<asset.canvasSize.toDouble()) {
                return@mapNotNull null
            }
            assetPixelX to assetPixelY
        }.distinct().sortedWith(compareBy<Pair<Double, Double>>({ it.second }, { it.first }))

        // Composite definitions can intentionally render the same source endpoint more than once.
        // Every candidate here is the exact same cache pixel; choose the stable topmost/leftmost
        // copy and expose the candidate count so diagnostics never hide that layout multiplicity.
        val (assetPixelX, assetPixelY) = placements.firstOrNull() ?: return null
        val longitude = -180.0 + 360.0 * assetPixelX / asset.canvasSize
        val mercatorY = PI * (1.0 - 2.0 * assetPixelY / asset.canvasSize)
        val latitude = Math.toDegrees(atan(sinh(mercatorY)))
        val minZoom = osrsRealmCameraEnvelope.minZoom(asset)
        val maxZoom = osrsRealmCameraEnvelope.maxZoom(asset)
        val endpointZoom = (asset.maxZoom + OSRS_ENDPOINT_CONTEXT_ZOOM_LEVELS)
            .toDouble()
            .coerceIn(minZoom, maxZoom)

        if (longitude !in asset.west..asset.east || latitude !in asset.south..asset.north) {
            return null
        }
        return osrsRealmEndpointDestination(
            realmId = realm.id,
            plane = position.plane,
            gameX = position.x,
            gameY = position.y,
            assetPixelX = assetPixelX,
            assetPixelY = assetPixelY,
            latitude = latitude,
            longitude = longitude,
            zoom = endpointZoom,
            matchingLayoutCount = placements.size
        )
    }

    companion object {
        private const val OSRS_ENDPOINT_CONTEXT_ZOOM_LEVELS = 2
    }
}

/** One zoom policy shared by endpoint mapping, MapLibre, persistence, and restoration. */
object osrsRealmCameraEnvelope {
    const val OSRS_MIN_ZOOM_PADDING_LEVELS = 2.0
    const val OSRS_MAX_OVERZOOM_LEVELS = 8.0
    const val OSRS_MAPLIBRE_GLOBAL_MAX_ZOOM = 22.0

    fun minZoom(asset: osrsRealmAsset): Double =
        max(0.0, asset.minZoom.toDouble() - OSRS_MIN_ZOOM_PADDING_LEVELS)

    fun maxZoom(asset: osrsRealmAsset): Double =
        min(OSRS_MAPLIBRE_GLOBAL_MAX_ZOOM, asset.maxZoom + OSRS_MAX_OVERZOOM_LEVELS)

    fun contains(asset: osrsRealmAsset, zoom: Double): Boolean =
        zoom.isFinite() && zoom in minZoom(asset)..maxZoom(asset)
}

/**
 * Raises the contextual endpoint zoom only when MapLibre's viewport constraint would otherwise
 * move the authoritative coordinate. The display's largest dp extent makes the result stable
 * across portrait/landscape recreation on the same device.
 */
fun osrsEndpointZoomForViewport(
    realm: osrsRealmRecord,
    destination: osrsRealmEndpointDestination,
    maximumViewportExtentDp: Double
): Double {
    val asset = requireNotNull(realm.assetForPlane(destination.plane))
    val canvasSize = asset.canvasSize.toDouble()
    val bounds = asset.contentPixelBounds
    require(bounds.size == 4) { "Realm endpoint camera requires four content bounds" }

    val horizontalDistance = min(
        destination.assetPixelX - bounds[0],
        bounds[2] - destination.assetPixelX
    ) / canvasSize
    val verticalMin = bounds[1].toDouble()
    val verticalMax = bounds[3].toDouble()
    val verticalDistance = min(
        destination.assetPixelY - verticalMin,
        verticalMax - destination.assetPixelY
    ) / canvasSize
    val edgeDistance = min(horizontalDistance, verticalDistance)
    val maxZoom = osrsRealmCameraEnvelope.maxZoom(asset)
    if (edgeDistance <= 0.0) return maxZoom

    // MapLibre camera zoom uses a 512 dp world. Keeping half of the viewport inside the legal
    // edge distance prevents the SDK from silently clamping the requested center coordinate.
    val safeExtent = max(1.0, maximumViewportExtentDp)
    val requiredZoom = ceil(log2(safeExtent / (2.0 * OSRS_MAPLIBRE_WORLD_DP * edgeDistance)))
    return max(destination.zoom, requiredZoom).coerceAtMost(maxZoom)
}

private const val OSRS_MAPLIBRE_WORLD_DP = 512.0

/** Resolve actual positive Android density without clamping legitimate sub-1.0 values. */
fun osrsPositiveDisplayDensity(density: Double, densityDpi: Int): Double {
    if (density.isFinite() && density > 0.0) return density
    val derived = densityDpi / 160.0
    require(derived.isFinite() && derived > 0.0) {
        "Display density must be finite and positive"
    }
    return derived
}

/** Stable maximum display extent in dp from real or maximum-window pixel bounds. */
fun osrsMaximumDisplayExtentDp(
    widthPixels: Int,
    heightPixels: Int,
    density: Double,
    densityDpi: Int
): Double {
    require(widthPixels > 0 && heightPixels > 0) {
        "Display pixel bounds must be positive"
    }
    return max(widthPixels, heightPixels) /
        osrsPositiveDisplayDensity(density, densityDpi)
}
