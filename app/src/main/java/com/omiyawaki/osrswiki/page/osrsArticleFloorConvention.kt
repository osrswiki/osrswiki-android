package com.omiyawaki.osrswiki.page

import com.omiyawaki.osrswiki.settings.Prefs
import java.util.Locale

enum class osrsArticleFloorNumberingMode(val persistedValue: String) {
    AUTO("auto"),
    GB("gb"),
    US("us");

    fun convention(locale: Locale): osrsArticleFloorConvention = when (this) {
        AUTO -> osrsArticleFloorConvention.from(locale)
        GB -> osrsArticleFloorConvention.GB
        US -> osrsArticleFloorConvention.US
    }

    companion object {
        fun fromPersisted(value: String?): osrsArticleFloorNumberingMode =
            entries.firstOrNull { it.persistedValue == value } ?: AUTO
    }
}

/**
 * Wiki floor-number markup always contains both GB and US variants. Choose the
 * dialect from the device locale or an explicit Appearance override: regions
 * that number the entrance level as the 1st floor use the US labels; everyone
 * else uses the wiki's UK default.
 */
enum class osrsArticleFloorConvention {
    GB,
    US;

    val bodyClass: String
        get() = when (this) {
            GB -> "floornumber-setting-gb"
            US -> "floornumber-setting-us"
        }

    val hiddenDialectSelector: String
        get() = when (this) {
            GB -> ".floornumber-us, .floornumber-help"
            US -> ".floornumber-gb, .floornumber-help"
        }

    /**
     * Numeric map chrome analog of wiki Template:FloorNumber.
     *
     * Articles pair GB/US words for the same game plane (UK "1st floor" vs
     * US "2nd floor" one level above the entrance). Entrance is UK
     * "Ground floor" / US "1st floor", so GB shows game index `p` and US
     * shows `p + 1`. Tile selection still uses `p`.
     */
    fun displayPlane(gamePlane: Int): Int = when (this) {
        GB -> gamePlane
        US -> gamePlane + 1
    }

    val usEntranceIsFirstFloor: Boolean
        get() = this == US

    companion object {
        private val US_ENTRANCE_IS_FIRST_FLOOR = setOf(
            "US", "AS", "GU", "MP", "PR", "VI", "UM",
            "CA", "MX", "BR",
            "JP", "KR", "CN", "TW", "PH", "RU"
        )

        fun from(locale: Locale): osrsArticleFloorConvention {
            val region = locale.country.uppercase(Locale.ROOT)
            return if (region in US_ENTRANCE_IS_FIRST_FLOOR) US else GB
        }

        fun current(
            mode: osrsArticleFloorNumberingMode = osrsArticleFloorNumberingMode.AUTO,
            locale: Locale = Locale.getDefault()
        ): osrsArticleFloorConvention = mode.convention(locale)

        fun resolved(locale: Locale = Locale.getDefault()): osrsArticleFloorConvention =
            current(
                osrsArticleFloorNumberingMode.fromPersisted(Prefs.floorNumberingMode),
                locale
            )
    }
}
