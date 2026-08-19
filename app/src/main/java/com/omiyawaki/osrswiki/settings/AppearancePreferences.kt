package com.omiyawaki.osrswiki.settings

import android.content.SharedPreferences
import com.omiyawaki.osrswiki.page.osrsArticleFloorNumberingMode
import kotlin.math.roundToInt

enum class AppThemeMode(val persistedValue: String) {
    LIGHT("light"),
    DARK("dark"),
    FOLLOW_SYSTEM("auto");

    companion object {
        fun fromPersistedValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: FOLLOW_SYSTEM
    }
}

data class ReaderPreferences(
    val textScale: Float = ReaderTextScale.DEFAULT,
    val swipeRightBackEnabled: Boolean = true,
    val swipeLeftContentsEnabled: Boolean = true
)

data class AppearancePreferences(
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val collapseTables: Boolean = false,
    val wrapTableCells: Boolean = false,
    val reader: ReaderPreferences = ReaderPreferences(),
    val floorNumberingMode: String = osrsArticleFloorNumberingMode.AUTO.persistedValue
)

object AppearancePreferenceKeys {
    const val APP_THEME_MODE = "app_theme_mode"
    const val COLLAPSE_TABLES = "collapseTables"
    const val WRAP_TABLE_CELLS = "wrap_table_cells"
    const val READER_TEXT_SCALE_PERCENT = "reader_text_scale_percent"
    const val SWIPE_RIGHT_BACK = "swipe_right_back"
    const val SWIPE_LEFT_CONTENTS = "swipe_left_contents"
    const val FLOOR_NUMBERING = "floor_numbering"

    // An unreleased/legacy reader prototype stored a raw multiplier under this key. Keeping the
    // migration makes upgrades and locally restored preference backups deterministic.
    internal const val LEGACY_READER_TEXT_SCALE = "reader_text_scale"
}

object ReaderTextScale {
    const val MIN = 0.85f
    const val MAX = 1.40f
    const val DEFAULT = 1.00f
    const val MIN_PERCENT = 85
    const val MAX_PERCENT = 140
    const val DEFAULT_PERCENT = 100

    fun clamp(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN, MAX) else DEFAULT

    fun fromPercent(percent: Int): Float =
        percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f

    fun toPercent(scale: Float): Int =
        (clamp(scale) * 100f).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)
}

internal object AppearancePreferencesCodec {
    fun readAndMigrate(sharedPreferences: SharedPreferences): AppearancePreferences {
        val stored = sharedPreferences.all
        val themeMode = AppThemeMode.fromPersistedValue(
            stored[AppearancePreferenceKeys.APP_THEME_MODE] as? String
        )
        val collapseTables = stored.booleanOrDefault(
            AppearancePreferenceKeys.COLLAPSE_TABLES,
            defaultValue = false
        )
        val wrapTableCells = stored.booleanOrDefault(
            AppearancePreferenceKeys.WRAP_TABLE_CELLS,
            defaultValue = false
        )
        val swipeRightBack = stored.booleanOrDefault(
            AppearancePreferenceKeys.SWIPE_RIGHT_BACK,
            defaultValue = true
        )
        val swipeLeftContents = stored.booleanOrDefault(
            AppearancePreferenceKeys.SWIPE_LEFT_CONTENTS,
            defaultValue = true
        )
        val floorNumberingMode = osrsArticleFloorNumberingMode.fromPersisted(
            stored[AppearancePreferenceKeys.FLOOR_NUMBERING] as? String
        ).persistedValue
        val textScalePercent = migratedTextScalePercent(stored)

        val editor = sharedPreferences.edit()
        var needsWrite = false

        if (stored[AppearancePreferenceKeys.APP_THEME_MODE] != themeMode.persistedValue) {
            editor.putString(AppearancePreferenceKeys.APP_THEME_MODE, themeMode.persistedValue)
            needsWrite = true
        }
        if (stored[AppearancePreferenceKeys.COLLAPSE_TABLES] !is Boolean) {
            editor.putBoolean(AppearancePreferenceKeys.COLLAPSE_TABLES, collapseTables)
            needsWrite = true
        }
        if (stored[AppearancePreferenceKeys.WRAP_TABLE_CELLS] !is Boolean) {
            editor.putBoolean(AppearancePreferenceKeys.WRAP_TABLE_CELLS, wrapTableCells)
            needsWrite = true
        }
        if (stored[AppearancePreferenceKeys.SWIPE_RIGHT_BACK] !is Boolean) {
            editor.putBoolean(AppearancePreferenceKeys.SWIPE_RIGHT_BACK, swipeRightBack)
            needsWrite = true
        }
        if (stored[AppearancePreferenceKeys.SWIPE_LEFT_CONTENTS] !is Boolean) {
            editor.putBoolean(AppearancePreferenceKeys.SWIPE_LEFT_CONTENTS, swipeLeftContents)
            needsWrite = true
        }
        if (stored[AppearancePreferenceKeys.FLOOR_NUMBERING] != floorNumberingMode) {
            editor.putString(AppearancePreferenceKeys.FLOOR_NUMBERING, floorNumberingMode)
            needsWrite = true
        }
        if (stored[AppearancePreferenceKeys.READER_TEXT_SCALE_PERCENT] != textScalePercent) {
            editor.putInt(AppearancePreferenceKeys.READER_TEXT_SCALE_PERCENT, textScalePercent)
            needsWrite = true
        }
        if (stored.containsKey(AppearancePreferenceKeys.LEGACY_READER_TEXT_SCALE)) {
            editor.remove(AppearancePreferenceKeys.LEGACY_READER_TEXT_SCALE)
            needsWrite = true
        }
        if (needsWrite) {
            editor.apply()
        }

        return AppearancePreferences(
            themeMode = themeMode,
            collapseTables = collapseTables,
            wrapTableCells = wrapTableCells,
            reader = ReaderPreferences(
                textScale = ReaderTextScale.fromPercent(textScalePercent),
                swipeRightBackEnabled = swipeRightBack,
                swipeLeftContentsEnabled = swipeLeftContents
            ),
            floorNumberingMode = floorNumberingMode
        )
    }

    private fun migratedTextScalePercent(stored: Map<String, *>): Int {
        val current = stored[AppearancePreferenceKeys.READER_TEXT_SCALE_PERCENT]
        val currentPercent = current.asPercent(rawScaleMultiplier = false)
        if (currentPercent != null) {
            return currentPercent.coerceIn(
                ReaderTextScale.MIN_PERCENT,
                ReaderTextScale.MAX_PERCENT
            )
        }

        val legacy = stored[AppearancePreferenceKeys.LEGACY_READER_TEXT_SCALE]
        return legacy.asPercent(rawScaleMultiplier = true)
            ?.coerceIn(ReaderTextScale.MIN_PERCENT, ReaderTextScale.MAX_PERCENT)
            ?: ReaderTextScale.DEFAULT_PERCENT
    }

    private fun Any?.asPercent(rawScaleMultiplier: Boolean): Int? {
        val number = when (this) {
            is Number -> toFloat()
            is String -> toFloatOrNull()
            else -> null
        } ?: return null
        if (!number.isFinite()) return null
        val percent = if (rawScaleMultiplier) number * 100f else number
        return percent.roundToInt()
    }

    private fun Map<String, *>.booleanOrDefault(key: String, defaultValue: Boolean): Boolean =
        this[key] as? Boolean ?: defaultValue
}
