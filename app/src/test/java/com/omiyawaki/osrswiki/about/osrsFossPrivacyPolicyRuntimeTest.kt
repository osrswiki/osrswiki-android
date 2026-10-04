package com.omiyawaki.osrswiki.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.BuildConfig
import com.omiyawaki.osrswiki.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class osrsFossPrivacyPolicyRuntimeTest {

    @Test
    fun inAppPrivacyStringsMatchTheActiveDistributionFlavor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = context.getString(R.string.privacy_policy_content)
        val teaser = context.getString(R.string.about_privacy_description)
        val combined = "$teaser\n$content"

        if (BuildConfig.FLAVOR == "foss") {
            assertFalse(combined.contains("crash reports", ignoreCase = true))
            assertFalse(combined.contains("in-app purchases processed through Google Play", ignoreCase = true))
            assertTrue(content.contains("osrswiki-feedback.omiyawaki.workers.dev"))
            assertTrue(content.contains("i.ytimg.com"))
            assertTrue(content.contains("prices.runescape.wiki"))
            assertTrue(content.contains("SpeechRecognizer"))
            assertTrue(content.contains("does not include Google Play Billing"))
        } else {
            assertTrue(content.contains("crash reports", ignoreCase = true))
            assertTrue(content.contains("Google Play Services"))
            assertTrue(content.contains("in-app purchases", ignoreCase = true))
        }
    }
}
