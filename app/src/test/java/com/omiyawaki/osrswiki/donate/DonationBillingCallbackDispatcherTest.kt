package com.omiyawaki.osrswiki.donate

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DonationBillingCallbackDispatcherTest {

    @Test
    fun disposePreventsQueuedMainThreadCallbacks() {
        val dispatcher = DonationBillingCallbackDispatcher(Handler(Looper.getMainLooper()))
        var deliveries = 0
        val posted = CountDownLatch(1)

        Thread {
            dispatcher.dispatch {
                deliveries += 1
            }
            posted.countDown()
        }.start()

        posted.await(1, TimeUnit.SECONDS)
        dispatcher.dispose()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, deliveries)
    }

    @Test
    fun disposeIsIdempotentAndKeepsLaterCallbacksQuiet() {
        val dispatcher = DonationBillingCallbackDispatcher(Handler(Looper.getMainLooper()))
        var deliveries = 0

        dispatcher.dispose()
        dispatcher.dispose()
        dispatcher.dispatch {
            deliveries += 1
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, deliveries)
    }
}
