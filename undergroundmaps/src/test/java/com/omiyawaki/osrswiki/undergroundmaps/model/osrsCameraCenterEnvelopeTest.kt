package com.omiyawaki.osrswiki.undergroundmaps.model

import com.omiyawaki.osrswiki.undergroundmaps.state.osrsCameraState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

class osrsCameraCenterEnvelopeTest {
    private val envelope = osrsCameraCenterEnvelope(
        west = -170.0,
        south = -60.0,
        east = 150.0,
        north = 70.0
    )

    @Test
    fun `all four content edges are legal exact camera centers`() {
        listOf(
            osrsCameraState(5.0, envelope.west, 8.0),
            osrsCameraState(5.0, envelope.east, 8.0),
            osrsCameraState(envelope.south, 0.0, 8.0),
            osrsCameraState(envelope.north, 0.0, 8.0)
        ).forEach { camera ->
            val result = osrsClampCameraToEnvelope(camera, envelope)
            assertFalse(result.clamped)
            assertEquals(camera, result.final)
        }
    }

    @Test
    fun `target clamps without changing zoom bearing or tilt`() {
        val requested = osrsCameraState(
            latitude = 85.0,
            longitude = -175.0,
            zoom = 13.25,
            bearing = 42.5,
            tilt = 31.0
        )

        val result = osrsClampCameraToEnvelope(requested, envelope)

        assertTrue(result.clamped)
        assertEquals(envelope.north, result.final.latitude, 0.0)
        assertEquals(envelope.west, result.final.longitude, 0.0)
        assertEquals(requested.zoom, result.final.zoom, 0.0)
        assertEquals(requested.bearing, result.final.bearing, 0.0)
        assertEquals(requested.tilt, result.final.tilt, 0.0)
    }

    @Test
    fun `out of range longitude is normalized without periodic wrapping`() {
        assertEquals(180.0, osrsNormalizeFiniteLongitude(190.0), 0.0)
        assertEquals(-180.0, osrsNormalizeFiniteLongitude(-210.0), 0.0)
        assertEquals(180.0, osrsNormalizeFiniteLongitude(540.0), 0.0)
        assertEquals(-180.0, osrsNormalizeFiniteLongitude(-540.0), 0.0)

        val result = osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = 550.0, zoom = 7.0),
            envelope
        )
        assertEquals(180.0, result.normalizedRequestedLongitude, 0.0)
        assertEquals(150.0, result.final.longitude, 0.0)

        val dateLineEdge = osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = -180.25, zoom = 7.0),
            osrsCameraCenterEnvelope(
                west = -180.0,
                south = -30.0,
                east = -60.0,
                north = 30.0
            )
        )
        assertTrue(dateLineEdge.clamped)
        assertEquals(-180.25, dateLineEdge.requested.longitude, 0.0)
        assertEquals(-180.0, dateLineEdge.final.longitude, 0.0)

        val equivalentMapLibreRepresentation = osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = 180.0, zoom = 7.0),
            osrsCameraCenterEnvelope(
                west = -180.0,
                south = -30.0,
                east = -60.0,
                north = 30.0
            )
        )
        assertTrue(equivalentMapLibreRepresentation.clamped)
        assertEquals(-60.0, equivalentMapLibreRepresentation.final.longitude, 0.0)
        assertTrue(osrsLongitudesEquivalent(180.0, -180.0))
    }

    @Test
    fun `finite clamp is idempotent at either horizontal edge`() {
        val west = osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = -900.0, zoom = 7.0),
            envelope
        ).final
        val east = osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = 900.0, zoom = 7.0),
            envelope
        ).final

        assertEquals(envelope.west, west.longitude, 0.0)
        assertEquals(envelope.east, east.longitude, 0.0)
        assertEquals(west, osrsClampCameraToEnvelope(west, envelope).final)
        assertEquals(east, osrsClampCameraToEnvelope(east, envelope).final)
    }

    @Test
    fun `MapLibre dateline callback echo stays on the same finite edge`() {
        assertEquals(
            -180.02,
            osrsResolveMapLibreLongitudeRepresentation(-180.0, 179.98),
            1e-12
        )
        assertEquals(
            180.02,
            osrsResolveMapLibreLongitudeRepresentation(180.0, -179.98),
            1e-12
        )
        assertEquals(
            -179.5,
            osrsResolveMapLibreLongitudeRepresentation(-180.0, -179.5),
            0.0
        )
        val westEcho = osrsClampCameraToEnvelope(
            osrsCameraState(
                latitude = 0.0,
                longitude = osrsResolveMapLibreLongitudeRepresentation(-180.0, 179.98),
                zoom = 7.0
            ),
            osrsCameraCenterEnvelope(-180.0, -30.0, 90.0, 30.0)
        )
        assertTrue(westEcho.clamped)
        assertEquals(-180.0, westEcho.final.longitude, 0.0)
    }

    @Test
    fun `settled camera equivalence includes zoom bearing and tilt`() {
        val expected = osrsCameraState(
            latitude = 10.0,
            longitude = -180.0,
            zoom = 4.0,
            bearing = 0.0,
            tilt = 12.0
        )

        assertTrue(
            osrsCameraStatesEquivalent(
                expected,
                expected.copy(longitude = 180.0, bearing = 360.0)
            )
        )
        assertFalse(osrsCameraStatesEquivalent(expected, expected.copy(zoom = 4.1)))
        assertFalse(osrsCameraStatesEquivalent(expected, expected.copy(bearing = 1.0)))
        assertFalse(osrsCameraStatesEquivalent(expected, expected.copy(tilt = 13.0)))
    }

    @Test
    fun `visible composition envelope unions aligned planes`() {
        val base = osrsTestAssetForEnvelope(
            plane = 0,
            canvasSize = 1024,
            bounds = listOf(-180.0, -50.0, 0.0, 80.0)
        )
        val overlay = osrsTestAssetForEnvelope(
            plane = 2,
            canvasSize = 1024,
            bounds = listOf(-160.0, -60.0, -20.0, 70.0)
        )

        assertEquals(
            osrsCameraCenterEnvelope(-180.0, -60.0, 0.0, 80.0),
            osrsCameraCenterEnvelope.fromVisibleAssets(listOf(base, overlay))
        )
        val mismatched = overlay.copy(canvasSize = 2048)
        val error = runCatching {
            osrsCameraCenterEnvelope.fromVisibleAssets(listOf(base, mismatched))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `surface copy safe zoom preserves edge centers without exposing a world copy`() {
        val surface = osrsCameraCenterEnvelope(
            west = -180.0,
            south = -11.178401873711781,
            east = 90.0,
            north = 85.0511287798066
        )

        val copySafe = osrsCopySafeMinimumZoom(surface, viewportWidth = 1080.0)

        assertEquals(2.0768155970508317, copySafe, 1e-12)
        assertEquals(
            copySafe,
            osrsFiniteRealmMinimumZoom(
                baseMinimumZoom = 0.0,
                envelope = surface,
                viewportWidth = 1080.0
            ),
            1e-12
        )
        assertEquals(
            3.0,
            osrsFiniteRealmMinimumZoom(
                baseMinimumZoom = 3.0,
                envelope = surface,
                viewportWidth = 1080.0
            ),
            0.0
        )
        assertEquals(surface.west, osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = -540.0, zoom = copySafe),
            surface
        ).final.longitude, 0.0)
        assertEquals(surface.east, osrsClampCameraToEnvelope(
            osrsCameraState(latitude = 0.0, longitude = 450.0, zoom = copySafe),
            surface
        ).final.longitude, 0.0)
    }

    @Test
    fun `four sided canvas padding governs portrait copy safe zoom`() {
        val padded = osrsCameraCenterEnvelope(
            west = -67.5,
            south = -66.51326044311186,
            east = 67.5,
            north = 66.51326044311186
        )

        assertEquals(
            log2(852.0 / 256.0),
            osrsFiniteRealmMinimumZoom(
                baseMinimumZoom = 0.0,
                envelope = padded,
                viewportWidth = 393.0,
                viewportHeight = 852.0
            ),
            1e-12
        )
        assertEquals(
            3.0,
            osrsFiniteRealmMinimumZoom(
                baseMinimumZoom = 3.0,
                envelope = padded,
                viewportWidth = 393.0,
                viewportHeight = 852.0
            ),
            0.0
        )
    }

    @Test
    fun `copy safe minimum zoom keeps adjacent worlds outside wide viewports`() {
        val halfWorld = osrsCameraCenterEnvelope(-180.0, -60.0, 0.0, 80.0)
        assertEquals(0.0, osrsCopySafeMinimumZoom(halfWorld, 393.0), 0.0)
        assertEquals(
            1.0768155970508317,
            osrsCopySafeMinimumZoom(halfWorld, 1080.0),
            1e-12
        )
    }

    @Test
    fun `callback guard suppresses recursive camera writes but reopens afterward`() {
        val guard = osrsCameraClampCallbackGuard()
        var nestedRan = false

        assertTrue(
            guard.run {
                nestedRan = guard.run { error("recursive block must not run") }
            }
        )
        assertFalse(nestedRan)
        assertEquals(1, guard.suppressedCallbacks)
        assertTrue(guard.run {})
    }

    @Test
    fun `elastic drag is continuous resisted and strictly capped beyond every edge`() {
        val minimum = -100.0
        val maximum = 100.0
        val cap = (maximum - minimum) * OSRS_EDGE_MAXIMUM_OVERSHOOT_FRACTION

        assertEquals(-25.0, osrsElasticAxisPosition(-25.0, minimum, maximum), 0.0)
        assertEquals(minimum, osrsElasticAxisPosition(minimum, minimum, maximum), 0.0)
        assertEquals(maximum, osrsElasticAxisPosition(maximum, minimum, maximum), 0.0)

        val nearWest = osrsElasticAxisPosition(-110.0, minimum, maximum)
        val farWest = osrsElasticAxisPosition(-10_000.0, minimum, maximum)
        val nearEast = osrsElasticAxisPosition(110.0, minimum, maximum)
        val farEast = osrsElasticAxisPosition(10_000.0, minimum, maximum)
        assertTrue(nearWest < minimum)
        assertTrue(farWest < nearWest)
        assertTrue(farWest > minimum - cap)
        assertTrue(nearEast > maximum)
        assertTrue(farEast > nearEast)
        assertTrue(farEast < maximum + cap)
    }

    @Test
    fun `shared damped spring overshoots gently and converges exactly to finite edge`() {
        val target = 100.0
        var state = osrsDampedSpringAxisState(position = 118.0, velocity = 65.0)
        var crossedInside = false
        repeat(240) {
            state = osrsStepDampedSpring(state, target, 1.0 / 120.0)
            crossedInside = crossedInside || state.position < target
        }

        assertTrue("A native-style underdamped return should cross the edge gently", crossedInside)
        assertTrue(osrsDampedSpringIsSettled(state, target, axisSpan = 200.0))
        assertEquals(target, state.position, 0.002)
        assertTrue(abs(state.velocity) < 0.02)
    }

    @Test
    fun `spring result is refresh-rate stable and release magnitude is deterministic`() {
        fun run(frameRate: Int): osrsDampedSpringAxisState {
            var state = osrsDampedSpringAxisState(position = -116.0, velocity = -40.0)
            repeat(frameRate * 2) {
                state = osrsStepDampedSpring(state, -100.0, 1.0 / frameRate)
            }
            return state
        }

        val at60 = run(60)
        val at120 = run(120)
        assertEquals(at60.position, at120.position, 0.002)
        assertEquals(at60.velocity, at120.velocity, 0.01)
        assertEquals(500.0, osrsCameraReleaseSpeed(300.0, 400.0), 0.0)
    }

    @Test
    fun `pinch velocity converts to zoom levels and decays independently of refresh rate`() {
        assertEquals(
            1.0,
            osrsPinchZoomVelocityLevelsPerSecond(
                previousSpan = 100.0,
                currentSpan = 200.0,
                elapsedSeconds = 1.0
            ),
            1e-12
        )
        assertEquals(
            OSRS_ZOOM_MOMENTUM_MAXIMUM_VELOCITY,
            osrsPinchZoomVelocityLevelsPerSecond(100.0, 10_000.0, 0.01),
            0.0
        )

        fun zoomAfterOneSecond(frameRate: Int): Double {
            var velocity = 4.0
            var zoom = 6.0
            repeat(frameRate) {
                val elapsed = 1.0 / frameRate
                zoom += velocity * elapsed
                velocity = osrsDecayZoomMomentumVelocity(velocity, elapsed)
            }
            return zoom
        }
        assertEquals(zoomAfterOneSecond(60), zoomAfterOneSecond(120), 0.04)
    }


    private fun osrsTestAssetForEnvelope(
        plane: Int,
        canvasSize: Int,
        bounds: List<Double>
    ): osrsRealmAsset = osrsRealmAsset(
        plane = plane,
        mbtilesPath = "realms/test-$plane.mbtiles",
        mbtilesSha256 = "a".repeat(64),
        mbtilesBytes = 1,
        width = 512,
        height = 512,
        nonblank = true,
        tileSize = 512,
        minZoom = 0,
        maxZoom = 1,
        tileCount = 1,
        canvasSize = canvasSize,
        contentPixelBounds = listOf(0, 0, 512, 512),
        contentLatlonBounds = bounds
    )
}
