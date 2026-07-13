package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSectionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `thumbnail row forwards inverse preference`() {
        var requestedValue: Boolean? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsSwitchRow(
                    title = "Thumbnails",
                    description = "Show previews",
                    checked = true,
                    switchTag = "thumbnail_switch",
                    rowTag = "thumbnail_setting_row",
                    onCheckedChange = { requestedValue = it }
                )
            }
        }

        composeRule.onNodeWithTag("thumbnail_switch").performClick()

        assertEquals(false, requestedValue)
    }

    @Test
    fun `enabling double line filenames disables marquee`() {
        val updatedTheme = ThemeState(
            doubleLineFilenames = false,
            marqueeFilenames = true
        ).withDoubleLineFilenames(true)

        assertTrue(updatedTheme.doubleLineFilenames)
        assertFalse(updatedTheme.marqueeFilenames)
    }

    @Test
    fun `enabling marquee filenames disables double line mode`() {
        val updatedTheme = ThemeState(
            doubleLineFilenames = true,
            marqueeFilenames = false
        ).withMarqueeFilenames(true)

        assertTrue(updatedTheme.marqueeFilenames)
        assertFalse(updatedTheme.doubleLineFilenames)
    }

    @Test
    fun `busy external cache cannot launch a second clear`() {
        var clearCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(fileCount = 3, isBusy = true),
                    onOpenStorageManagement = {},
                    onClearExternalCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("external_cache_setting_row")
            .assertIsNotEnabled()
            .performClick()

        assertEquals(0, clearCount)
    }

    @Test
    fun `idle external cache launches one clear`() {
        var clearCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(fileCount = 3, isBusy = false),
                    onOpenStorageManagement = {},
                    onClearExternalCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("external_cache_setting_row").performClick()

        assertEquals(1, clearCount)
    }

}
