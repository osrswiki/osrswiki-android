package com.omiyawaki.osrswiki.undergroundmaps.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.undergroundmaps.model.osrsRealmAsset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class osrsRealmRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: osrsRealmRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.resolve("osrs-underground-realm-assets-v1").deleteRecursively()
        repository = osrsRealmRepository(context)
    }

    @Test
    fun `repository reads and validates packaged manifest asset`() = runBlocking {
        val catalog = repository.loadCatalog()

        assertTrue(catalog.manifest.candidate.isNotBlank())
        assertEquals("Gielinor Surface", catalog.surface.canonicalName)
        assertTrue(catalog.surface.isSurface)
        assertNotNull(catalog.surface.assetForPlane(0))
        assertEquals(catalog.realmCount, catalog.selectorCount)
    }

    @Test
    fun `staging hashes asset once then reuses verified immutable copy`() = runBlocking {
        val asset = smallestPackagedAsset()

        val first = repository.stage(asset)
        val second = repository.stage(asset)

        assertTrue(first.file.isFile)
        assertEquals(asset.mbtilesBytes, first.file.length())
        assertEquals(asset.mbtilesSha256, first.sha256)
        assertFalse(first.reusedVerifiedCopy)
        assertTrue(second.reusedVerifiedCopy)
        assertEquals(first.file, second.file)
    }

    @Test
    fun `checksum mismatch fails closed without publishing destination`() = runBlocking {
        val asset = smallestPackagedAsset().copy(
            mbtilesSha256 = "a".repeat(64)
        )

        val failure = runCatching { repository.stage(asset) }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalStateException)
        assertFalse(context.filesDir.resolve("osrs-underground-realm-assets-v1/${"a".repeat(64)}.mbtiles").exists())
    }

    private suspend fun smallestPackagedAsset(): osrsRealmAsset = repository
        .loadCatalog()
        .manifest
        .realms
        .asSequence()
        .flatMap { it.assets.asSequence() }
        .minBy { it.mbtilesBytes }
}
