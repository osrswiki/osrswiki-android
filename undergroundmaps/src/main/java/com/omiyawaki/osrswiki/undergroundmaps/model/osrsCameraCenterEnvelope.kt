package com.omiyawaki.osrswiki.undergroundmaps.model

import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraState
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** Camera-target bounds that allow each exact content edge to reach the drawable map center. */
data class osrsCameraCenterEnvelope(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double
) {
    init {
        require(listOf(west, south, east, north).all(Double::isFinite)) {
            "Camera-center envelope bounds must be finite"
        }
        require(west < east && south < north) {
            "Camera-center envelope must have positive extent"
        }
        require(west >= -180.0 && east <= 180.0 && south >= -90.0 && north <= 90.0) {
            "Camera-center envelope must use valid latitude/longitude bounds"
        }
    }

    companion object {
        fun from(asset: osrsRealmAsset): osrsCameraCenterEnvelope =
            osrsCameraCenterEnvelope(
                west = asset.west,
                south = asset.south,
                east = asset.east,
                north = asset.north
            )

        fun fromVisibleAssets(assets: Iterable<osrsRealmAsset>): osrsCameraCenterEnvelope {
            val visible = assets.toList()
            require(visible.isNotEmpty()) { "Camera envelope requires a visible asset" }
            require(visible.map { it.canvasSize }.distinct().size == 1) {
                "Visible realm planes must share one canvas"
            }
            return osrsCameraCenterEnvelope(
                west = visible.minOf { it.west },
                south = visible.minOf { it.south },
                east = visible.maxOf { it.east },
                north = visible.maxOf { it.north }
            )
        }
    }
}

/**
 * Minimum zoom that keeps the nearest repeated world copy outside the viewport at either edge.
 * Both widths must use MapLibre's rendered coordinate unit: physical pixels on Android.
 */
fun osrsCopySafeMinimumZoom(
    envelope: osrsCameraCenterEnvelope,
    viewportWidth: Double,
    worldWidthAtZoomZero: Double = 512.0
): Double {
    require(viewportWidth.isFinite() && viewportWidth > 0.0) {
        "Copy-safe viewport width must be positive and finite"
    }
    require(worldWidthAtZoomZero.isFinite() && worldWidthAtZoomZero > 0.0) {
        "Copy-safe world width must be positive and finite"
    }
    val contentFraction = (envelope.east - envelope.west) / 360.0
    require(contentFraction > 0.0 && contentFraction < 1.0) {
        "Copy-safe envelope must be finite and narrower than one world"
    }
    val gapFraction = 1.0 - contentFraction
    return max(
        0.0,
        log2(viewportWidth / (2.0 * worldWidthAtZoomZero * gapFraction))
    )
}

/**
 * Applies the finite-realm copy-safe floor to every realm, including Gielinor Surface.
 *
 * Surface still permits its camera center to reach the exact content edge (the established
 * half-viewport overbound behavior). Raising only the minimum zoom keeps the neighboring
 * MapLibre world copy beyond that overbound area without changing the legal camera centers.
 */
fun osrsFiniteRealmMinimumZoom(
    baseMinimumZoom: Double,
    envelope: osrsCameraCenterEnvelope,
    viewportWidth: Double,
    viewportHeight: Double = viewportWidth,
    worldSizeAtZoomZero: Double = 512.0
): Double {
    require(baseMinimumZoom.isFinite() && baseMinimumZoom >= 0.0) {
        "Base minimum zoom must be finite and non-negative"
    }
    require(viewportHeight.isFinite() && viewportHeight > 0.0) {
        "Finite-realm viewport height must be positive and finite"
    }
    val leftPadding = (envelope.west + 180.0) / 360.0
    val rightPadding = (180.0 - envelope.east) / 360.0
    val northY = osrsWebMercatorY(envelope.north)
    val southY = osrsWebMercatorY(envelope.south)
    val topPadding = northY
    val bottomPadding = 1.0 - southY
    val horizontalPadding = min(leftPadding, rightPadding)
    val verticalPadding = min(topPadding, bottomPadding)
    if (horizontalPadding <= 0.0 || verticalPadding <= 0.0) {
        return maxOf(
            baseMinimumZoom,
            osrsCopySafeMinimumZoom(envelope, viewportWidth, worldSizeAtZoomZero)
        )
    }
    return maxOf(
        baseMinimumZoom,
        0.0,
        log2(viewportWidth / (2.0 * worldSizeAtZoomZero * horizontalPadding)),
        log2(viewportHeight / (2.0 * worldSizeAtZoomZero * verticalPadding))
    )
}

private fun osrsWebMercatorY(latitude: Double): Double {
    val clamped = latitude.coerceIn(-85.0511287798066, 85.0511287798066)
    val radians = Math.toRadians(clamped)
    return (1.0 - asinh(tan(radians)) / Math.PI) / 2.0
}

data class osrsCameraClampResult(
    val requested: osrsCameraState,
    val normalizedRequestedLongitude: Double,
    val final: osrsCameraState,
    val clamped: Boolean
)

/** MapLibre may report a finite longitude outside the canonical range. Do not wrap it. */
fun osrsNormalizeFiniteLongitude(longitude: Double): Double {
    require(longitude.isFinite()) { "Longitude must be finite" }
    return longitude.coerceIn(-180.0, 180.0)
}

/** Resolves only MapLibre's equivalent +/-180 callback representation; it is not path wrapping. */
fun osrsResolveMapLibreLongitudeRepresentation(
    previousLongitude: Double,
    reportedLongitude: Double
): Double {
    require(previousLongitude.isFinite() && reportedLongitude.isFinite()) {
        "MapLibre longitude representations must be finite"
    }
    val delta = reportedLongitude - previousLongitude
    return when {
        delta > 180.0 -> reportedLongitude - 360.0
        delta < -180.0 -> reportedLongitude + 360.0
        else -> reportedLongitude
    }
}

fun osrsClampCameraToEnvelope(
    camera: osrsCameraState,
    envelope: osrsCameraCenterEnvelope
): osrsCameraClampResult {
    require(camera.isFinite()) { "Camera must be finite" }
    val normalizedLongitude = osrsNormalizeFiniteLongitude(camera.longitude)
    val finalLongitude = normalizedLongitude.coerceIn(envelope.west, envelope.east)
    val final = camera.copy(
        latitude = camera.latitude.coerceIn(envelope.south, envelope.north),
        longitude = finalLongitude
    )
    return osrsCameraClampResult(
        requested = camera,
        normalizedRequestedLongitude = normalizedLongitude,
        final = final,
        clamped =
            abs(final.latitude - camera.latitude) > OSRS_CAMERA_TARGET_EPSILON ||
                abs(final.longitude - camera.longitude) > OSRS_CAMERA_TARGET_EPSILON
    )
}

/**
 * Native-style resisted overscroll for one finite camera axis.
 *
 * The strict envelope is still the exact 50%-overflow contract. During direct manipulation only,
 * this maps unbounded input onto a small asymptotic elastic region outside the strict edge. The
 * result can never cross the opposite edge or grow beyond [maximumOvershootFraction] of the
 * envelope span, irrespective of gesture distance.
 */
fun osrsElasticAxisPosition(
    requested: Double,
    minimum: Double,
    maximum: Double,
    maximumOvershootFraction: Double = OSRS_EDGE_MAXIMUM_OVERSHOOT_FRACTION
): Double {
    require(requested.isFinite() && minimum.isFinite() && maximum.isFinite()) {
        "Elastic-axis inputs must be finite"
    }
    require(minimum < maximum) { "Elastic axis requires positive extent" }
    require(maximumOvershootFraction > 0.0 && maximumOvershootFraction < 0.5) {
        "Elastic overshoot fraction must be between zero and one half"
    }
    if (requested in minimum..maximum) return requested
    val edge = if (requested < minimum) minimum else maximum
    val direction = if (requested < minimum) -1.0 else 1.0
    val distance = abs(requested - edge)
    val limit = (maximum - minimum) * maximumOvershootFraction
    val resistedDistance = limit * distance / (limit + distance)
    return edge + direction * resistedDistance
}

data class osrsDampedSpringAxisState(
    val position: Double,
    val velocity: Double
) {
    init {
        require(position.isFinite() && velocity.isFinite()) {
            "Spring position and velocity must be finite"
        }
    }
}

/**
 * Advances the app-owned edge spring with bounded semi-implicit integration.
 *
 * Values are expressed in the caller's coordinate unit (latitude, longitude, or pixels) and
 * seconds. The natural frequency and damping ratio are deliberately shared with iOS so the two
 * platforms have the same interaction shape even though their display refresh rates differ.
 */
fun osrsStepDampedSpring(
    state: osrsDampedSpringAxisState,
    target: Double,
    elapsedSeconds: Double,
    naturalFrequency: Double = OSRS_EDGE_SPRING_NATURAL_FREQUENCY,
    dampingRatio: Double = OSRS_EDGE_SPRING_DAMPING_RATIO
): osrsDampedSpringAxisState {
    require(target.isFinite() && elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) {
        "Spring target and elapsed time must be finite and non-negative"
    }
    require(naturalFrequency > 0.0 && dampingRatio > 0.0) {
        "Spring frequency and damping must be positive"
    }
    if (elapsedSeconds == 0.0) return state
    val substepCount = ceil(elapsedSeconds / OSRS_EDGE_SPRING_MAX_SUBSTEP_SECONDS)
        .toInt()
        .coerceAtLeast(1)
    val dt = elapsedSeconds / substepCount
    val stiffness = naturalFrequency * naturalFrequency
    val damping = 2.0 * dampingRatio * naturalFrequency
    var position = state.position
    var velocity = state.velocity
    repeat(substepCount) {
        val acceleration = -stiffness * (position - target) - damping * velocity
        velocity += acceleration * dt
        position += velocity * dt
    }
    return osrsDampedSpringAxisState(position, velocity)
}

fun osrsDampedSpringIsSettled(
    state: osrsDampedSpringAxisState,
    target: Double,
    axisSpan: Double
): Boolean {
    require(target.isFinite() && axisSpan.isFinite() && axisSpan > 0.0) {
        "Spring settle comparison requires a finite target and positive span"
    }
    val positionTolerance = max(axisSpan * 0.000_01, 0.000_000_1)
    val velocityTolerance = max(axisSpan * 0.000_5, 0.000_001)
    return abs(state.position - target) <= positionTolerance &&
        abs(state.velocity) <= velocityTolerance
}

/** Euclidean magnitude helper kept here so release thresholds are unit-testable. */
fun osrsCameraReleaseSpeed(horizontalPixelsPerSecond: Double, verticalPixelsPerSecond: Double): Double {
    require(horizontalPixelsPerSecond.isFinite() && verticalPixelsPerSecond.isFinite()) {
        "Camera release velocity must be finite"
    }
    return sqrt(
        horizontalPixelsPerSecond * horizontalPixelsPerSecond +
            verticalPixelsPerSecond * verticalPixelsPerSecond
    )
}

/** Converts multiplicative two-finger span movement to MapLibre zoom levels per second. */
fun osrsPinchZoomVelocityLevelsPerSecond(
    previousSpan: Double,
    currentSpan: Double,
    elapsedSeconds: Double
): Double {
    require(previousSpan.isFinite() && previousSpan > 0.0) { "Previous span must be positive" }
    require(currentSpan.isFinite() && currentSpan > 0.0) { "Current span must be positive" }
    require(elapsedSeconds.isFinite() && elapsedSeconds > 0.0) { "Elapsed time must be positive" }
    return (log2(currentSpan / previousSpan) / elapsedSeconds)
        .coerceIn(-OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY, OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY)
}

fun osrsDecayZoomMomentumVelocity(velocity: Double, elapsedSeconds: Double): Double {
    require(velocity.isFinite() && elapsedSeconds.isFinite() && elapsedSeconds >= 0.0)
    return velocity * kotlin.math.exp(-OSRS_ZOOM_MOMENTUM_DECELERATION_PER_SECOND * elapsedSeconds)
}

fun osrsLongitudesEquivalent(first: Double, second: Double): Boolean =
    abs(
        osrsNormalizeFiniteLongitude(first) - osrsNormalizeFiniteLongitude(second)
    ) <= OSRS_CAMERA_TARGET_EPSILON ||
        (abs(abs(first) - 180.0) <= OSRS_CAMERA_TARGET_EPSILON &&
            abs(abs(second) - 180.0) <= OSRS_CAMERA_TARGET_EPSILON)

fun osrsCameraTargetsEquivalent(
    first: osrsCameraState,
    second: osrsCameraState
): Boolean =
    abs(first.latitude - second.latitude) <= OSRS_CAMERA_TARGET_EPSILON &&
        osrsLongitudesEquivalent(first.longitude, second.longitude)

fun osrsCameraStatesEquivalent(
    first: osrsCameraState,
    second: osrsCameraState
): Boolean =
    osrsCameraTargetsEquivalent(first, second) &&
        abs(first.zoom - second.zoom) <= OSRS_CAMERA_STATE_EPSILON &&
        osrsCircularDegreesDifference(first.bearing, second.bearing) <=
        OSRS_CAMERA_STATE_EPSILON &&
        abs(first.tilt - second.tilt) <= OSRS_CAMERA_STATE_EPSILON

private fun osrsCircularDegreesDifference(first: Double, second: Double): Double {
    val raw = abs((first - second) % 360.0)
    return minOf(raw, 360.0 - raw)
}

const val OSRS_EDGE_MAXIMUM_OVERSHOOT_FRACTION = 0.12
const val OSRS_EDGE_SPRING_NATURAL_FREQUENCY = 14.0
const val OSRS_EDGE_SPRING_DAMPING_RATIO = 0.82
const val OSRS_ZOOM_MOMENTUM_DECELERATION_PER_SECOND = 4.8
const val OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY = 6.0
const val OSRS_ZOOM_MOMENTUM_MINIMUM_RELEASE_VELOCITY = 0.04
const val OSRS_ZOOM_MOMENTUM_STOP_VELOCITY = 0.01
private const val OSRS_EDGE_SPRING_MAX_SUBSTEP_SECONDS = 1.0 / 240.0

/**
 * Small re-entrancy gate used around MapLibre camera writes. A synchronous camera callback caused
 * by a clamp is ignored, while later user callbacks remain eligible for their own clamp.
 */
class osrsCameraClampCallbackGuard {
    var suppressedCallbacks: Int = 0
        private set

    private var applying = false

    fun run(block: () -> Unit): Boolean {
        if (applying) {
            suppressedCallbacks += 1
            return false
        }
        applying = true
        return try {
            block()
            true
        } finally {
            applying = false
        }
    }
}

private const val OSRS_CAMERA_TARGET_EPSILON = 1e-9
private const val OSRS_CAMERA_STATE_EPSILON = 1e-6
