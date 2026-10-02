package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.UiPreferences
import dev.qtremors.arcile.core.ui.theme.ThemePreset
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.FileOpenPreferences
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.feature.settings.SettingsPreferences
import dev.qtremors.arcile.core.ui.settings.AppStartPageSelector
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
    fun `browser tabs row forwards preference`() {
        var requestedValue: Boolean? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsSwitchRow(
                    title = "Browser tabs",
                    description = "Show tabs",
                    checked = false,
                    switchTag = "browser_tabs_switch",
                    rowTag = "browser_tabs_setting_row",
                    onCheckedChange = { requestedValue = it }
                )
            }
        }

        composeRule.onNodeWithTag("browser_tabs_switch").performClick()

        assertEquals(true, requestedValue)
    }

    @Test
    fun `start page selector forwards selected page`() {
        var selectedPage: AppStartPage? = null
        composeRule.setContent {
            ArcileTestTheme {
                AppStartPageSelector(
                    currentPage = AppStartPage.HOME,
                    onPageSelected = { selectedPage = it }
                )
            }
        }

        composeRule.onNodeWithTag("app_start_page_browser").performClick()

        assertEquals(AppStartPage.BROWSER, selectedPage)
    }

    @Test
    fun `start page choices expand to take available horizontal space`() {
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.width(360.dp)) {
                    AppStartPageSelector(
                        currentPage = AppStartPage.HOME,
                        onPageSelected = {}
                    )
                }
            }
        }

        val homeWidth = composeRule.onNodeWithTag("app_start_page_home").fetchSemanticsNode().size.width
        val browserWidth = composeRule.onNodeWithTag("app_start_page_browser").fetchSemanticsNode().size.width

        assertEquals(homeWidth, browserWidth)
        assertTrue(homeWidth > 0)
    }

    @Test
    fun `enabling double line filenames disables marquee`() {
        val updatedTheme = UiPreferences(
            doubleLineFilenames = false,
            marqueeFilenames = true
        ).withDoubleLineFilenames(true)

        assertTrue(updatedTheme.doubleLineFilenames)
        assertFalse(updatedTheme.marqueeFilenames)
    }

    @Test
    fun `enabling marquee filenames disables double line mode`() {
        val updatedTheme = UiPreferences(
            doubleLineFilenames = true,
            marqueeFilenames = false
        ).withMarqueeFilenames(true)

        assertTrue(updatedTheme.marqueeFilenames)
        assertFalse(updatedTheme.doubleLineFilenames)
    }

    @Test
    fun `filename display mode derives and maps accurately`() {
        val single = UiPreferences(doubleLineFilenames = false, marqueeFilenames = false)
        assertEquals(FilenameDisplayMode.SINGLE_LINE, single.filenameDisplayMode)

        val twoLines = single.withFilenameDisplayMode(FilenameDisplayMode.TWO_LINES)
        assertEquals(FilenameDisplayMode.TWO_LINES, twoLines.filenameDisplayMode)
        assertTrue(twoLines.doubleLineFilenames)
        assertFalse(twoLines.marqueeFilenames)

        val autoScroll = twoLines.withFilenameDisplayMode(FilenameDisplayMode.AUTO_SCROLL)
        assertEquals(FilenameDisplayMode.AUTO_SCROLL, autoScroll.filenameDisplayMode)
        assertFalse(autoScroll.doubleLineFilenames)
        assertTrue(autoScroll.marqueeFilenames)

        val backToSingle = autoScroll.withFilenameDisplayMode(FilenameDisplayMode.SINGLE_LINE)
        assertEquals(FilenameDisplayMode.SINGLE_LINE, backToSingle.filenameDisplayMode)
        assertFalse(backToSingle.doubleLineFilenames)
        assertFalse(backToSingle.marqueeFilenames)
    }

    @Test
    fun `landscape dual pane setting updates independently`() {
        val original = UiPreferences(marqueeFilenames = true)
        val updated = original.withLandscapeDualPane(true)

        assertTrue(updated.landscapeDualPaneEnabled)
        assertTrue(updated.marqueeFilenames)
    }

    @Test
    fun `folder icon setting updates independently`() {
        val original = UiPreferences(marqueeFilenames = true)
        val updated = original.withFolderIcons(true)

        assertFalse(original.folderIconsEnabled)
        assertTrue(updated.folderIconsEnabled)
        assertTrue(updated.marqueeFilenames)
    }

    @Test
    fun `busy external cache cannot launch a second clear`() {
        var clearCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(fileCount = 3, isBusy = true),
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
                    onClearExternalCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("external_cache_setting_row").performClick()

        assertEquals(1, clearCount)
    }

    @Test
    fun `empty external cache has no clear action`() {
        var clearCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(fileCount = 0, isBusy = false),
                    onClearExternalCache = { clearCount += 1 }
                )
            }
        }

        composeRule.onNodeWithText("No temporary files to clear").assertExists()
        composeRule.onNodeWithTag("external_cache_setting_row").assertIsNotEnabled()
        assertEquals(0, clearCount)
    }

    @Test
    fun `storage section waits for volume discovery`() {
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    cache = SettingsExternalCacheState(),
                    onClearExternalCache = {}
                )
            }
        }

        composeRule.onNodeWithTag("storage_volumes_loading").assertExists()
    }

    @Test
    fun `storage section displays volumes and handles classification`() {
        val volume = StorageVolume(
            id = "sd-card-1",
            storageKey = "sd-card-1",
            path = "/storage/0000-0000",
            name = "SD Card",
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = false,
            isRemovable = true,
            kind = StorageKind.EXTERNAL_UNCLASSIFIED
        )
        var classifiedKey: String? = null
        var classifiedKind: StorageKind? = null
        var classificationCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                SettingsStorageSection(
                    volumes = listOf(volume),
                    cache = SettingsExternalCacheState(fileCount = 0, isBusy = false),
                    onSetVolumeClassification = { key, kind ->
                        classifiedKey = key
                        classifiedKind = kind
                        classificationCount++
                    },
                    onClearExternalCache = {}
                )
            }
        }

        composeRule.onNodeWithText("SD Card").assertExists()
        composeRule.onNodeWithText("Classify as SD").performClick()
        assertEquals("sd-card-1", classifiedKey)
        assertEquals(StorageKind.SD_CARD, classifiedKind)
        assertEquals(1, classificationCount)
    }

    @Test
    fun `filename display selector updates mode`() {
        var selectedMode: FilenameDisplayMode? = null
        composeRule.setContent {
            ArcileTestTheme {
                FilenameDisplaySelector(
                    currentMode = FilenameDisplayMode.SINGLE_LINE,
                    onModeSelected = { selectedMode = it }
                )
            }
        }

        composeRule.onNodeWithText("Two lines").performClick()
        assertEquals(FilenameDisplayMode.TWO_LINES, selectedMode)
    }

    @Test
    fun `filename choices remain visible in narrow space`() {
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.width(160.dp)) {
                    FilenameDisplaySelector(
                        currentMode = FilenameDisplayMode.SINGLE_LINE,
                        onModeSelected = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Auto-scroll").assertIsDisplayed()
    }

    @Test
    fun `theme preset choices remain visible in narrow space`() {
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.width(160.dp)) {
                    ThemePresetSelector(currentPreset = ThemePreset.NONE, onPresetSelected = {})
                }
            }
        }

        composeRule.onNodeWithText("Custom").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_preset_choices").performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Tokyo Night").assertIsDisplayed()
    }

    @Test
    fun `theme preset toggle selects a preset`() {
        var selected: ThemePreset? = null
        composeRule.setContent {
            ArcileTestTheme {
                ThemePresetSelector(currentPreset = ThemePreset.NONE, onPresetSelected = { selected = it })
            }
        }

        composeRule.onNodeWithText("Dracula").performClick()
        assertEquals(ThemePreset.DRACULA, selected)
    }

    @Test
    fun `filename choices expand to take available horizontal space`() {
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.width(360.dp)) {
                    FilenameDisplaySelector(
                        currentMode = FilenameDisplayMode.SINGLE_LINE,
                        onModeSelected = {}
                    )
                }
            }
        }

        val singleLineWidth = composeRule.onNodeWithText("Single line").fetchSemanticsNode().size.width
        val twoLinesWidth = composeRule.onNodeWithText("Two lines").fetchSemanticsNode().size.width
        val autoScrollWidth = composeRule.onNodeWithText("Auto-scroll").fetchSemanticsNode().size.width

        assertEquals(singleLineWidth, twoLinesWidth)
        assertEquals(twoLinesWidth, autoScrollWidth)
        assertTrue(singleLineWidth > 0)
    }

    @Test
    fun `theme preset choices expand to take available horizontal space`() {
        composeRule.setContent {
            ArcileTestTheme {
                Box(Modifier.width(360.dp)) {
                    ThemePresetSelector(currentPreset = ThemePreset.NONE, onPresetSelected = {})
                }
            }
        }

        val defaultWidth = composeRule.onNodeWithText("Default").fetchSemanticsNode().size.width
        val draculaWidth = composeRule.onNodeWithText("Dracula").fetchSemanticsNode().size.width
        val tokyoNightWidth = composeRule.onNodeWithText("Tokyo Night").fetchSemanticsNode().size.width
        val customWidth = composeRule.onNodeWithText("Custom").fetchSemanticsNode().size.width

        assertEquals(defaultWidth, draculaWidth)
        assertEquals(draculaWidth, tokyoNightWidth)
        assertTrue(customWidth > defaultWidth)
    }

    @Test
    fun `file opening sheet exposes a text extension choice`() {
        var request: Pair<String, FileOpenBehavior>? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsFileOpeningSection(
                    behaviors = emptyMap(),
                    onBehaviorChange = { key, behavior -> request = key to behavior },
                    onBehaviorRemove = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose how files open from the browser").performClick()
        composeRule.onNodeWithTag("file_open_group_documents").performScrollTo().performClick()
        revealExtension("documents", "css")
        composeRule.onNodeWithTag("file_extension_css").performClick()

        assertEquals(FileOpenPreferences.extensionKey("css") to FileOpenBehavior.EXTERNAL, request)
    }

    @Test
    fun `file opening sheet includes installed plugin extensions`() {
        var request: Pair<String, FileOpenBehavior>? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsFileOpeningSection(
                    behaviors = emptyMap(),
                    pluginExtensions = setOf("stl"),
                    onBehaviorChange = { key, behavior -> request = key to behavior },
                    onBehaviorRemove = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose how files open from the browser").performClick()
        composeRule.onNodeWithTag("file_open_group_other").performScrollTo().performClick()
        revealExtension("other", "stl")
        composeRule.onNodeWithTag("file_extension_stl").performClick()

        assertEquals(FileOpenPreferences.extensionKey("stl") to FileOpenBehavior.EXTERNAL, request)
    }

    @Test
    fun `custom extension action sends files to another app`() {
        var request: Pair<String, FileOpenBehavior>? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsFileOpeningSection(
                    behaviors = emptyMap(),
                    onBehaviorChange = { key, behavior -> request = key to behavior },
                    onBehaviorRemove = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose how files open from the browser").performClick()
        composeRule.onNodeWithTag("file_open_group_other").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_file_extension").performScrollTo().performTextInput(".stl")
        composeRule.onNodeWithContentDescription("Add extension").performScrollTo().performClick()

        assertEquals(FileOpenPreferences.extensionKey("stl") to FileOpenBehavior.EXTERNAL, request)
    }

    @Test
    fun `user added extension can be removed from the sheet`() {
        var removedKey: String? = null
        val behaviors = mutableStateMapOf(
            FileOpenPreferences.extensionKey("stl") to FileOpenBehavior.EXTERNAL
        )
        composeRule.setContent {
            ArcileTestTheme {
                SettingsFileOpeningSection(
                    behaviors = behaviors,
                    onBehaviorChange = { _, _ -> },
                    onBehaviorRemove = {
                        removedKey = it
                        behaviors.remove(it)
                    }
                )
            }
        }

        composeRule.onNodeWithText("Choose how files open from the browser").performClick()
        composeRule.onNodeWithTag("file_open_group_other").performScrollTo().performClick()
        composeRule.onNodeWithTag("remove_file_extension_stl").assertDoesNotExist()
        composeRule.onNodeWithTag("file_extension_stl").performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithTag("remove_file_extension_stl").performScrollTo().performClick()

        assertEquals(FileOpenPreferences.extensionKey("stl"), removedKey)
        composeRule.onNodeWithTag("remove_file_extension_stl").assertDoesNotExist()
    }

    @Test
    fun `user added extension hold reveals and hides delete`() {
        val behaviors = mutableStateMapOf(
            FileOpenPreferences.extensionKey("stl") to FileOpenBehavior.EXTERNAL
        )
        composeRule.setContent {
            ArcileTestTheme {
                SettingsFileOpeningSection(
                    behaviors = behaviors,
                    onBehaviorChange = { _, _ -> },
                    onBehaviorRemove = { behaviors.remove(it) }
                )
            }
        }

        composeRule.onNodeWithText("Choose how files open from the browser").performClick()
        composeRule.onNodeWithTag("file_open_group_other").performScrollTo().performClick()
        composeRule.onNodeWithTag("remove_file_extension_stl").assertDoesNotExist()

        composeRule.onNodeWithTag("file_extension_stl").performScrollTo().performTouchInput { longClick() }
        composeRule.onNodeWithTag("remove_file_extension_stl").assertIsDisplayed()

        composeRule.onNodeWithTag("file_extension_stl").performTouchInput { longClick() }
        composeRule.onNodeWithTag("remove_file_extension_stl").assertDoesNotExist()
    }

    @Test
    fun `long extension choice stays visible without a menu`() {
        var request: Pair<String, FileOpenBehavior>? = null
        composeRule.setContent {
            ArcileTestTheme {
                SettingsFileOpeningSection(
                    behaviors = emptyMap(),
                    pluginExtensions = setOf(
                        "proprietaryformat", "anotherlongextension", "verylongfileextension"
                    ),
                    onBehaviorChange = { key, behavior -> request = key to behavior },
                    onBehaviorRemove = {}
                )
            }
        }

        composeRule.onNodeWithText("Choose how files open from the browser").performClick()
        composeRule.onNodeWithTag("file_open_group_other").performScrollTo().performClick()
        revealExtension("other", "verylongfileextension")
        composeRule.onNodeWithTag("file_extension_verylongfileextension").performClick()
        assertEquals(
            FileOpenPreferences.extensionKey("verylongfileextension") to FileOpenBehavior.EXTERNAL,
            request
        )
    }

    private fun revealExtension(group: String, extension: String) {
        val choice = composeRule.onNodeWithTag("file_extension_$extension")
        choice.performScrollTo()
        choice.assertIsDisplayed()
    }

    @Test
    fun `settings landing opens a section page and returns`() {
        composeRule.setContent {
            ArcileTestTheme {
                SettingsScreen(
                    pluginExtensions = emptySet(),
                    state = SettingsScreenState(
                        theme = UiPreferences(),
                        preferences = SettingsPreferences(),
                        backup = PreferencesBackupUiState.Idle
                    ),
                    navigationActions = SettingsNavigationActions({}, {}, {}),
                    preferenceActions = SettingsPreferenceActions(
                        themeChange = {},
                        showThumbnailsChange = {},
                        homeRecentCarouselLimitChange = {},
                        showHiddenFilesChange = {},
                        appStartPageChange = {},
                        browserTabsEnabledChange = {},
                        rememberLastFolderChange = {},
                        expandableAppBarChange = {},
                        activityRecordingChange = {},
                        browserScrollbarEnabledChange = {},
                        galleryScrollbarEnabledChange = {},
                        fileOpenBehaviorChange = { _, _ -> },
                        fileOpenBehaviorRemove = {}
                    ),
                    backupActions = SettingsBackupActions({}, {}),
                    storageActions = SettingsStorageActions({})
                )
            }
        }

        composeRule.onNodeWithText("File name display").assertDoesNotExist()
        composeRule.onNodeWithText("Theme, file names, and feedback").performClick()
        composeRule.onNodeWithText("File name display").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Theme, file names, and feedback").assertExists()
    }
}
