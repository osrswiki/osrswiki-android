package com.omiyawaki.osrswiki.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOwnerGenerationGateTest {
    @Test
    fun aNewOwnerMakesEveryOlderCallbackObsolete() {
        val gate = MapOwnerGenerationGate()
        val first = gate.claim()
        val second = gate.claim()

        assertFalse(gate.isActive(first))
        assertTrue(gate.isActive(second))
        assertFalse(gate.release(first))
        assertTrue(gate.isActive(second))
    }

    @Test
    fun onlyTheActiveOwnerCanReleaseTheMap() {
        val gate = MapOwnerGenerationGate()
        val owner = gate.claim()

        assertTrue(gate.release(owner))
        assertNull(gate.activeToken)
        assertFalse(gate.release(owner))
    }

    @Test
    fun hideOrProcessRetentionInvalidatesTheCurrentOwner() {
        val gate = MapOwnerGenerationGate()
        val owner = gate.claim()

        assertTrue(gate.invalidate() == owner)
        assertFalse(gate.isActive(owner))
        assertNull(gate.activeToken)
    }
}
