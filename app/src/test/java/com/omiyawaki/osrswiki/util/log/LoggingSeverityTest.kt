package com.omiyawaki.osrswiki.util.log

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class LoggingSeverityTest {

    @Test
    fun debugMessagesAreNotLoggedAsErrors() {
        ShadowLog.clear()

        L.d("normal navigation trace")

        val log = ShadowLog.getLogsForTag("PageLoadTrace").single()
        assertEquals(Log.DEBUG, log.type)
    }
}
