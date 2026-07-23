package dev.qtremors.arcile.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.feature.home.ui.HomeContentIntents
import dev.qtremors.arcile.feature.home.ui.HomeNavigationIntents
import dev.qtremors.arcile.feature.home.ui.HomeScreen
import org.junit.Rule
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
        shareRecentFile = {},
        setVolumeClassification = { _, _ -> },
        hideClassificationPrompt = {}
    )
}
