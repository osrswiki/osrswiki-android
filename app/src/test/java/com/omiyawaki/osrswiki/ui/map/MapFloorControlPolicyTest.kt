package com.omiyawaki.osrswiki.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFloorControlPolicyTest {

    @Test
    fun bottomFloorDisablesDownActionOnly() {
        val state = MapFloorControlPolicy.state(currentFloor = 0, maxFloor = 3)

        assertEquals("0", state.floorLabel)
        assertTrue(state.up.isActionable)
        assertFalse(state.down.isActionable)
        assertEquals(1.0f, state.up.alpha, 0.0f)
        assertEquals(0.4f, state.down.alpha, 0.0f)
    }

    @Test
    fun topFloorDisablesUpActionOnly() {
        val state = MapFloorControlPolicy.state(currentFloor = 3, maxFloor = 3)

        assertEquals("3", state.floorLabel)
        assertFalse(state.up.isActionable)
        assertTrue(state.down.isActionable)
        assertEquals(0.4f, state.up.alpha, 0.0f)
        assertEquals(1.0f, state.down.alpha, 0.0f)
    }

    @Test
    fun currentFloorIsClampedToAvailableRange() {
        val state = MapFloorControlPolicy.state(currentFloor = 8, maxFloor = 3)

        assertEquals("3", state.floorLabel)
        assertFalse(state.up.isActionable)
        assertTrue(state.down.isActionable)
    }
}
