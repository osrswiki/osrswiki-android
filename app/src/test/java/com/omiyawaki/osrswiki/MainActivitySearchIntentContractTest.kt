package com.omiyawaki.osrswiki

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivitySearchIntentContractTest {
    @Test
    fun navigationActionUsesSearchActivityHostAndConsumesOneShotAction() {
        val source = File("src/main/java/com/omiyawaki/osrswiki/MainActivity.kt").readText()
        val body = source.substringAfter("private fun handleIntentExtras(intent: Intent)")
            .substringBefore("override fun onSupportNavigateUp")

        assertTrue(body.contains("intent.action = null"))
        assertTrue(body.contains("startActivity(Intent(this, SearchActivity::class.java))"))
        assertFalse(body.contains("SearchFragment.newInstance"))
    }
}
