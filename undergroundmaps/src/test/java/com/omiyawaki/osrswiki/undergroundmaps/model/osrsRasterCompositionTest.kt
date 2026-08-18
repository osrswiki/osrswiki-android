package com.omiyawaki.osrswiki.undergroundmaps.model

import com.omiyawaki.osrswiki.undergroundmaps.osrsTestCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class osrsRasterCompositionTest {
    private val realm = osrsTestCatalog().byId.getValue(
        "cache-world-map:lms-desert-island"
    )

    @Test
    fun `plane zero renders alone and fully opaque`() {
        val composition = osrsRasterCompositionFor(realm, selectedPlane = 0)

        assertEquals(listOf(0), composition.layersBottomToTop.map { it.plane })
        assertEquals(listOf(1.0f), composition.layersBottomToTop.map { it.opacity })
        assertTrue(composition.layersBottomToTop.single().selected)
    }

    @Test
    fun `upper plane renders above half-opacity plane zero`() {
        val composition = osrsRasterCompositionFor(realm, selectedPlane = 1)

        assertEquals(listOf(0, 1), composition.layersBottomToTop.map { it.plane })
        assertEquals(listOf(0.5f, 1.0f), composition.layersBottomToTop.map { it.opacity })
        assertFalse(composition.layersBottomToTop.first().selected)
        assertTrue(composition.layersBottomToTop.last().selected)
    }

    @Test
    fun `same-style upper-floor transition reuses plane-zero source and layer`() {
        val planeZero = osrsRasterCompositionFor(realm, 0).layersBottomToTop.map {
            osrsRasterResourceIdentity(4, realm.id, it)
        }
        val upper = osrsRasterCompositionFor(realm, 1).layersBottomToTop.map {
            osrsRasterResourceIdentity(4, realm.id, it)
        }
        val transition = osrsRasterCompositionTransition(planeZero, upper)

        assertEquals(listOf(0), transition.reused.map { it.plane })
        assertEquals(listOf(1), transition.additionsBottomToTop.map { it.plane })
        assertTrue(transition.obsoleteLayersTopToBottom.isEmpty())
        assertTrue(transition.obsoleteSources.isEmpty())
        assertTrue(transition.replacementPreparedBeforeRemoval)
        assertEquals(planeZero.single().sourceId, upper.first().sourceId)
        assertEquals(planeZero.single().layerId, upper.first().layerId)
    }

    @Test
    fun `upper-to-base transition activates retained base before removing obsolete upper`() {
        val upper = osrsRasterCompositionFor(realm, 1).layersBottomToTop.map {
            osrsRasterResourceIdentity(5, realm.id, it)
        }
        val planeZero = osrsRasterCompositionFor(realm, 0).layersBottomToTop.map {
            osrsRasterResourceIdentity(5, realm.id, it)
        }
        val transition = osrsRasterCompositionTransition(upper, planeZero)

        assertEquals(listOf(0), transition.reused.map { it.plane })
        assertEquals(listOf(1), transition.obsoleteLayersTopToBottom.map { it.plane })
        assertEquals(listOf(1), transition.obsoleteSources.map { it.plane })
        assertTrue(transition.replacementPreparedBeforeRemoval)
        assertEquals(1.0f, transition.desiredBottomToTop.single().opacity)
    }

    @Test
    fun `style generation changes resource identity while request changes do not exist in key`() {
        val layer = osrsRasterCompositionFor(realm, 0).layersBottomToTop.single()
        val generationOne = osrsRasterResourceIdentity(1, realm.id, layer)
        val generationTwo = osrsRasterResourceIdentity(2, realm.id, layer)
        val repeatedGenerationOne = osrsRasterResourceIdentity(1, realm.id, layer)

        assertEquals(generationOne, repeatedGenerationOne)
        assertNotEquals(generationOne.sourceId, generationTwo.sourceId)
        assertNotEquals(generationOne.layerId, generationTwo.layerId)
    }
}
