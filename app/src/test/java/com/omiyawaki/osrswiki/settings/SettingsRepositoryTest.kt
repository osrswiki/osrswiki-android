package com.omiyawaki.osrswiki.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.omiyawaki.osrswiki.page.ReaderGesturePolicy
import com.omiyawaki.osrswiki.page.ReaderSwipeAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sharedPreferences = context.getSharedPreferences(
            "appearance-repository-${System.nanoTime()}",
            Context.MODE_PRIVATE
        )
    }

    @After
    fun tearDown() {
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun defaultsAreTypedAndNormalizedForFutureRelaunches() {
        val settings = SettingsRepository(sharedPreferences).currentSettings()

        assertEquals(AppThemeMode.FOLLOW_SYSTEM, settings.themeMode)
        assertFalse(settings.collapseTables)
        assertEquals(ReaderTextScale.DEFAULT, settings.reader.textScale)
        assertTrue(settings.reader.swipeRightBackEnabled)
        assertTrue(settings.reader.swipeLeftContentsEnabled)
        assertEquals("auto", sharedPreferences.getString(SettingsRepository.KEY_APP_THEME_MODE, null))
        assertEquals(100, sharedPreferences.getInt(SettingsRepository.KEY_READER_TEXT_SCALE_PERCENT, -1))
    }

    @Test
    fun repositorySettersSurviveANewRepositoryInstance() {
        val firstLaunch = SettingsRepository(sharedPreferences)
        firstLaunch.setAppThemeMode(SettingsRepository.THEME_DARK)
        firstLaunch.setCollapseTablesEnabled(false)
        firstLaunch.setReaderTextScale(1.37f)
        firstLaunch.setSwipeRightBackEnabled(false)
        firstLaunch.setSwipeLeftContentsEnabled(true)

        val relaunched = SettingsRepository(sharedPreferences).currentSettings()

        assertEquals(AppThemeMode.DARK, relaunched.themeMode)
        assertFalse(relaunched.collapseTables)
        assertEquals(1.37f, relaunched.reader.textScale)
        assertFalse(relaunched.reader.swipeRightBackEnabled)
        assertTrue(relaunched.reader.swipeLeftContentsEnabled)
    }

    @Test
    fun legacyRawReaderMultiplierMigratesToClampedPercentageOnce() {
        sharedPreferences.edit()
            .putFloat(AppearancePreferenceKeys.LEGACY_READER_TEXT_SCALE, 1.25f)
            .apply()

        val migrated = SettingsRepository(sharedPreferences).currentSettings()

        assertEquals(1.25f, migrated.reader.textScale)
        assertEquals(125, sharedPreferences.getInt(SettingsRepository.KEY_READER_TEXT_SCALE_PERCENT, -1))
        assertFalse(sharedPreferences.contains(AppearancePreferenceKeys.LEGACY_READER_TEXT_SCALE))
    }

    @Test
    fun malformedValuesMigrateToSafeDefaultsAndBounds() {
        sharedPreferences.edit()
            .putString(SettingsRepository.KEY_APP_THEME_MODE, "sepia")
            .putString(SettingsRepository.KEY_COLLAPSE_TABLES, "not-a-boolean")
            .putString(SettingsRepository.KEY_SWIPE_RIGHT_BACK, "not-a-boolean")
            .putInt(SettingsRepository.KEY_READER_TEXT_SCALE_PERCENT, 12)
            .apply()

        val low = SettingsRepository(sharedPreferences).currentSettings()

        assertEquals(AppThemeMode.FOLLOW_SYSTEM, low.themeMode)
        assertFalse(low.collapseTables)
        assertTrue(low.reader.swipeRightBackEnabled)
        assertEquals(ReaderTextScale.MIN, low.reader.textScale)
        assertEquals(ReaderTextScale.MIN_PERCENT, sharedPreferences.getInt(SettingsRepository.KEY_READER_TEXT_SCALE_PERCENT, -1))

        sharedPreferences.edit()
            .putInt(SettingsRepository.KEY_READER_TEXT_SCALE_PERCENT, 900)
            .apply()

        val high = SettingsRepository(sharedPreferences).currentSettings()
        assertEquals(ReaderTextScale.MAX, high.reader.textScale)
        assertEquals(ReaderTextScale.MAX_PERCENT, sharedPreferences.getInt(SettingsRepository.KEY_READER_TEXT_SCALE_PERCENT, -1))
    }

    @Test
    fun viewModelActionsPersistThroughTheRepository() {
        val repository = SettingsRepository(sharedPreferences)
        val viewModel = SettingsViewModel(repository)

        viewModel.onThemeSelected(SettingsRepository.THEME_LIGHT)
        viewModel.onSwitchSettingToggled(SettingsRepository.KEY_COLLAPSE_TABLES, false)
        viewModel.onSwitchSettingToggled(SettingsRepository.KEY_SWIPE_RIGHT_BACK, false)
        viewModel.onSwitchSettingToggled(SettingsRepository.KEY_SWIPE_LEFT_CONTENTS, false)
        viewModel.onReaderTextScaleChanged(140)

        val relaunched = SettingsRepository(sharedPreferences).currentSettings()
        assertEquals(AppThemeMode.LIGHT, relaunched.themeMode)
        assertFalse(relaunched.collapseTables)
        assertEquals(ReaderTextScale.MAX, relaunched.reader.textScale)
        assertFalse(relaunched.reader.swipeRightBackEnabled)
        assertFalse(relaunched.reader.swipeLeftContentsEnabled)
    }

    @Test
    fun gesturePolicySeesRepositoryChangesImmediately() {
        val repository = SettingsRepository(sharedPreferences)

        assertTrue(
            ReaderGesturePolicy.isEnabled(
                ReaderSwipeAction.BACK,
                repository.settingsState.value.reader
            )
        )
        assertTrue(
            ReaderGesturePolicy.isEnabled(
                ReaderSwipeAction.CONTENTS,
                repository.settingsState.value.reader
            )
        )

        repository.setSwipeRightBackEnabled(false)
        repository.setSwipeLeftContentsEnabled(false)

        assertFalse(
            ReaderGesturePolicy.isEnabled(
                ReaderSwipeAction.BACK,
                repository.settingsState.value.reader
            )
        )
        assertFalse(
            ReaderGesturePolicy.isEnabled(
                ReaderSwipeAction.CONTENTS,
                repository.settingsState.value.reader
            )
        )
    }
}
