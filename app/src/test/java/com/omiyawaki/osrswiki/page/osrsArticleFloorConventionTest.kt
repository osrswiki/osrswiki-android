package com.omiyawaki.osrswiki.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class osrsArticleFloorConventionTest {

    @Test
    fun usEnglishAndCanadianLocalesUseUsFloorLabels() {
        assertEquals(osrsArticleFloorConvention.US, osrsArticleFloorConvention.from(Locale.US))
        assertEquals(osrsArticleFloorConvention.US, osrsArticleFloorConvention.from(Locale("en", "CA")))
        assertEquals(osrsArticleFloorConvention.US, osrsArticleFloorConvention.from(Locale("en", "PH")))
        assertEquals(osrsArticleFloorConvention.US, osrsArticleFloorConvention.from(Locale.JAPAN))
        assertEquals("floornumber-setting-us", osrsArticleFloorConvention.US.bodyClass)
        assertEquals(".floornumber-gb, .floornumber-help", osrsArticleFloorConvention.US.hiddenDialectSelector)
    }

    @Test
    fun ukAndOtherLocalesUseTheWikiDefaultGbFloorLabels() {
        assertEquals(osrsArticleFloorConvention.GB, osrsArticleFloorConvention.from(Locale.UK))
        assertEquals(osrsArticleFloorConvention.GB, osrsArticleFloorConvention.from(Locale("en", "AU")))
        assertEquals(osrsArticleFloorConvention.GB, osrsArticleFloorConvention.from(Locale("en", "NZ")))
        assertEquals(osrsArticleFloorConvention.GB, osrsArticleFloorConvention.from(Locale("en", "IE")))
        assertEquals("floornumber-setting-gb", osrsArticleFloorConvention.GB.bodyClass)
        assertEquals(".floornumber-us, .floornumber-help", osrsArticleFloorConvention.GB.hiddenDialectSelector)
    }

    @Test
    fun appearanceOverrideSelectsAnExplicitFloorDialect() {
        assertEquals(
            osrsArticleFloorConvention.GB,
            osrsArticleFloorConvention.current(osrsArticleFloorNumberingMode.GB, Locale.US)
        )
        assertEquals(
            osrsArticleFloorConvention.US,
            osrsArticleFloorConvention.current(osrsArticleFloorNumberingMode.US, Locale.UK)
        )
        assertEquals(
            osrsArticleFloorConvention.US,
            osrsArticleFloorNumberingMode.AUTO.convention(Locale.US)
        )
    }

    @Test
    fun numericMapDigitsFollowWikiEntranceOffset() {
        for (plane in 0..3) {
            assertEquals(plane, osrsArticleFloorConvention.GB.displayPlane(plane))
            assertEquals(plane + 1, osrsArticleFloorConvention.US.displayPlane(plane))
        }
        assertEquals(
            1,
            osrsArticleFloorNumberingMode.AUTO.convention(Locale.US).displayPlane(0)
        )
        assertEquals(
            0,
            osrsArticleFloorNumberingMode.AUTO.convention(Locale.UK).displayPlane(0)
        )
        assertEquals(
            0,
            osrsArticleFloorConvention.current(osrsArticleFloorNumberingMode.GB, Locale.US)
                .displayPlane(0)
        )
        assertEquals(
            1,
            osrsArticleFloorConvention.current(osrsArticleFloorNumberingMode.US, Locale.UK)
                .displayPlane(0)
        )
        assertFalse(osrsArticleFloorConvention.GB.usEntranceIsFirstFloor)
        assertTrue(osrsArticleFloorConvention.US.usEntranceIsFirstFloor)
    }
}
