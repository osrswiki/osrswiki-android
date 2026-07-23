package com.omiyawaki.osrswiki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omiyawaki.osrswiki.network.model.ArticleParseApiResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppContextInstrumentedTest {
    @Test
    fun targetContext_hasExpectedPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.omiyawaki.osrswiki", appContext.packageName)
    }

    @Test
    fun appModelClasses_areReachableFromInstrumentation() {
        val response = ArticleParseApiResponse(parse = null)

        assertNotNull(response)
    }
}
