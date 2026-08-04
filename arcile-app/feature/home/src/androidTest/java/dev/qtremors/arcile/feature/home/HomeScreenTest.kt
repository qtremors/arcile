package dev.qtremors.arcile.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.feature.home.ui.HomeContentIntents
import dev.qtremors.arcile.feature.home.ui.HomeNavigationIntents
import dev.qtremors.arcile.feature.home.ui.HomeScreen
import dev.qtremors.arcile.feature.home.ui.HomeLayoutDialog
import dev.qtremors.arcile.core.storage.domain.HomeLayoutPreferences
import dev.qtremors.arcile.core.storage.domain.HomeSectionIds
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_rendersCategoriesAndFolders() {
        composeTestRule.setContent {
            HomeScreen(
                state = HomeState(isLoading = false),
                navigationIntents = testNavigationIntents(),
                contentIntents = testContentIntents()
            )
        }

        composeTestRule.onNodeWithText("Categories").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Access").fetchSemanticsNode()
        composeTestRule.onNodeWithText("Utilities").fetchSemanticsNode()
    }

    @Test
    fun homeScreen_showsStorageBarLoadingState() {
        composeTestRule.setContent {
            HomeScreen(
                state = HomeState(isLoading = true),
                navigationIntents = testNavigationIntents(),
                contentIntents = testContentIntents()
            )
        }

        composeTestRule.onNodeWithTag("storage_bar_loading").assertExists()
    }

    @Test
    fun homeScreen_omitsOnlyTheFirstVisibleSectionTitle() {
        composeTestRule.setContent {
            HomeScreen(
                state = HomeState(
                    isLoading = false,
                    homeLayoutPreferences = HomeLayoutPreferences(
                        orderedSectionIds = listOf(
                            HomeSectionIds.CATEGORIES,
                            HomeSectionIds.STORAGE,
                            HomeSectionIds.QUICK_ACCESS,
                            HomeSectionIds.UTILITIES,
                            HomeSectionIds.RECENT_FILES
                        ),
                        enabledSectionIds = setOf(
                            HomeSectionIds.CATEGORIES,
                            HomeSectionIds.STORAGE
                        )
                    )
                ),
                navigationIntents = testNavigationIntents(),
                contentIntents = testContentIntents()
            )
        }

        composeTestRule.onNodeWithText("Categories").assertDoesNotExist()
        composeTestRule.onNodeWithText("Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick Access").assertDoesNotExist()
    }

    @Test
    fun homeScreen_keepsTitleWhenFirstSectionHasHeaderAction() {
        composeTestRule.setContent {
            HomeScreen(
                state = HomeState(
                    isLoading = false,
                    homeLayoutPreferences = HomeLayoutPreferences(
                        orderedSectionIds = HomeSectionIds.ALL,
                        enabledSectionIds = setOf(HomeSectionIds.QUICK_ACCESS)
                    )
                ),
                navigationIntents = testNavigationIntents(),
                contentIntents = testContentIntents()
            )
        }

        composeTestRule.onNodeWithText("Quick Access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage").assertIsDisplayed()
    }

    @Test
    fun homeLayoutDialog_appliesEnabledStateChanges() {
        var appliedPreferences: HomeLayoutPreferences? = null
        composeTestRule.setContent {
            HomeLayoutDialog(
                preferences = HomeLayoutPreferences(),
                onDismiss = {},
                onApply = { appliedPreferences = it }
            )
        }

        composeTestRule.onNodeWithContentDescription("Show Categories on Home").performClick()
        composeTestRule.onNodeWithText("Apply").performClick()

        composeTestRule.runOnIdle {
            assertFalse(HomeSectionIds.CATEGORIES in checkNotNull(appliedPreferences).enabledSectionIds)
        }
    }

    @Test
    fun homeLayoutDialog_resetRestoresDefaultOrderAndVisibility() {
        var appliedPreferences: HomeLayoutPreferences? = null
        composeTestRule.setContent {
            HomeLayoutDialog(
                preferences = HomeLayoutPreferences(
                    orderedSectionIds = HomeSectionIds.ALL.reversed(),
                    enabledSectionIds = setOf(HomeSectionIds.STORAGE)
                ),
                onDismiss = {},
                onApply = { appliedPreferences = it }
            )
        }

        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.onNodeWithText("Apply").performClick()

        composeTestRule.runOnIdle {
            val applied = checkNotNull(appliedPreferences)
            assertEquals(HomeSectionIds.ALL, applied.orderedSectionIds)
            assertEquals(HomeSectionIds.ALL.toSet(), applied.enabledSectionIds)
        }
    }

    private fun testNavigationIntents() = HomeNavigationIntents(
        openFileBrowser = {},
        navigateToPath = {},
        openFileWithContext = { _, _ -> },
        categoryClick = {},
        settingsClick = {},
        navigateToTools = {},
        navigateToAbout = {},
        navigateToTrash = {},
        navigateToRecentFiles = {},
        navigateToQuickAccess = {},
        navigateToExternalFolder = {},
        openStorageDashboard = {},
        navigateToCleaner = {},
        navigateToActivity = {},
        navigateToOnlyFiles = {}
    )

    private fun testContentIntents() = HomeContentIntents(
        refresh = {},
        resumeRefresh = {},
        loadRootStorageUsage = {},
        shareRecentFile = {},
        setVolumeClassification = { _, _ -> },
        hideClassificationPrompt = {}
    )
}
