package com.omiyawaki.osrswiki.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class osrsFossPrivacyPolicyContractTest {

    private val fossStrings = File("src/foss/res/values/strings.xml").readText()
    private val playStrings = File("src/main/res/values/strings.xml").readText()
    private val fossPolicyPage = File("src/foss/assets/legal/privacy-policy.html")
    private val privacyFragment = File(
        "src/main/java/com/omiyawaki/osrswiki/about/PrivacyPolicyFragment.kt"
    ).readText()
    private val aboutLayout = File("src/main/res/layout/fragment_about.xml").readText()

    @Test
    fun fossFlavorOverridesPrivacyCopyInsteadOfInheritingPlayPolicy() {
        assertTrue(fossStrings.contains("name=\"about_privacy_description\""))
        assertTrue(fossStrings.contains("name=\"privacy_policy_content\""))
        assertTrue(fossStrings.contains("name=\"privacy_policy_last_updated\""))
        assertTrue(
            "in-app FOSS policy must load R.string.privacy_policy_content so flavor overrides apply",
            privacyFragment.contains("R.string.privacy_policy_content")
        )
        assertTrue(
            aboutLayout.contains("""android:text="@string/about_privacy_description"""")
        )
    }

    @Test
    fun fossPrivacyCopyDoesNotClaimCrashMetricsOrPlayIap() {
        fossPrivacyDocuments().forEach { (label, text) ->
            val normalized = text.lowercase()
            listOf(
                "crash reports",
                "app performance metrics",
                "performance metrics",
                "in-app purchases processed through google play",
                "app distribution and in-app purchases",
                "retained for up to 90 days"
            ).forEach { forbidden ->
                assertFalse("$label must not claim '$forbidden'", normalized.contains(forbidden))
            }
        }
    }

    @Test
    fun fossPrivacyPageDescribesActualFossDataFlows() {
        fossPrivacyDocuments().forEach { (label, text) ->
            val normalized = text.lowercase()
            assertTrue("$label must name the Cloudflare feedback worker", normalized.contains("osrswiki-feedback.omiyawaki.workers.dev"))
            assertTrue("$label must mention device model in feedback", normalized.contains("device model"))
            assertTrue("$label must mention android version in feedback", normalized.contains("android version"))
            assertTrue("$label must mention YouTube thumbs", normalized.contains("i.ytimg.com"))
            assertTrue("$label must mention GE chart host", normalized.contains("prices.runescape.wiki"))
            assertTrue("$label must mention SpeechRecognizer", normalized.contains("speechrecognizer"))
            assertTrue("$label must mention the wiki host", normalized.contains("oldschool.runescape.wiki"))
            assertTrue(
                "$label must say FOSS does not include Google Play Billing",
                normalized.contains("does not include google play billing")
            )
        }
    }

    @Test
    fun playPrivacyPolicyRemainsUnchangedForStoreIapAndCrashCopy() {
        val playPolicy = playStrings.substringAfter("name=\"privacy_policy_content\"")
            .substringBefore("</string>")
            .lowercase()
        assertTrue(playPolicy.contains("crash reports"))
        assertTrue(playPolicy.contains("google play services"))
        assertTrue(playPolicy.contains("in-app purchases"))
        val playHtml = listOf(
            File("../../../shared/legal/privacy-policy.html"),
            File("../../shared/legal/privacy-policy.html"),
            File("shared/legal/privacy-policy.html")
        ).first { it.isFile }.readText()
        assertFalse(
            "Play policy HTML is not the FOSS page",
            playHtml.contains("osrswiki-feedback.omiyawaki.workers.dev")
        )
    }

    private fun fossPrivacyDocuments(): List<Pair<String, String>> {
        assertTrue("FOSS policy HTML page must exist", fossPolicyPage.isFile)
        return listOf(
            "foss strings" to fossStrings,
            "foss policy page" to fossPolicyPage.readText()
        )
    }
}
