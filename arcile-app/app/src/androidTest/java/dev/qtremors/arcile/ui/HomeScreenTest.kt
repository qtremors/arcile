package dev.qtremors.arcile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.presentation.home.HomeState
import dev.qtremors.arcile.presentation.ui.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_rendersCategoriesAndFolders() {
        var openFileBrowserCalled = false
        var navigateToToolsCalled = false

        composeTestRule.setContent {
            HomeScreen(
                state = HomeState(isLoading = false),
                onOpenFileBrowser = { openFileBrowserCalled = true },
                onNavigateToPath = {},
                onOpenFile = {},
                onCategoryClick = {},
                onSettingsClick = {},
                onNavigateToTools = { navigateToToolsCalled = true },
                onNavigateToAbout = {},
                onNavigateToTrash = {},
                onNavigateToRecentFiles = {},
                onOpenStorageDashboard = {},
                onSearchQueryChange = {},
                onSearchFiltersChange = {},
                onToggleSearchFilterMenu = {},
                onRefresh = {},
                onResumeRefresh = {},
                onSetVolumeClassification = { _, _ -> },
                onHideClassificationPrompt = {}
            )
        }

        // Verify Categories section title is displayed
        composeTestRule.onNodeWithText("Categories").assertIsDisplayed()

        // Verify Folders section title is displayed
        composeTestRule.onNodeWithText("Folders").assertIsDisplayed()

        // Verify Utilities section title is displayed
        composeTestRule.onNodeWithText("Utilities").assertIsDisplayed()

        // Verify Recent Files section title is displayed
        composeTestRule.onNodeWithText("Recent Files").assertIsDisplayed()
    }
}
