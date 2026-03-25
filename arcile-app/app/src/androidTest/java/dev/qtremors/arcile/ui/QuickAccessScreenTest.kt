package dev.qtremors.arcile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.domain.QuickAccessItem
import dev.qtremors.arcile.domain.QuickAccessType
import dev.qtremors.arcile.presentation.quickaccess.QuickAccessState
import dev.qtremors.arcile.presentation.ui.QuickAccessScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickAccessScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun quickAccessScreen_rendersSections() {
        val items = listOf(
            QuickAccessItem("1", "Downloads", "/downloads", QuickAccessType.STANDARD, isPinned = true),
            QuickAccessItem("2", "Work", "/work", QuickAccessType.CUSTOM, isPinned = true),
            QuickAccessItem("3", "Android Data", "uri://data", QuickAccessType.SAF_TREE, isPinned = true)
        )

        composeTestRule.setContent {
            QuickAccessScreen(
                state = QuickAccessState(items = items, isLoading = false),
                onNavigateBack = {},
                onNavigateToPath = {},
                onNavigateToSaf = {},
                onTogglePin = {},
                onRemoveItem = {},
                onAddCustomFolder = { _, _ -> },
                onAddSafFolder = { _, _ -> }
            )
        }

        // Verify section headers are displayed
        composeTestRule.onNodeWithText("SYSTEM FOLDERS").assertIsDisplayed()
        composeTestRule.onNodeWithText("CUSTOM FOLDERS").assertIsDisplayed()
        composeTestRule.onNodeWithText("SCOPED STORAGE").assertIsDisplayed()

        // Verify labels are displayed
        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText("Android Data").assertIsDisplayed()
    }

    @Test
    fun quickAccessScreen_clickItem_triggersNavigation() {
        var navigatedPath = ""
        val items = listOf(
            QuickAccessItem("1", "Downloads", "/downloads", QuickAccessType.STANDARD, isPinned = true)
        )

        composeTestRule.setContent {
            QuickAccessScreen(
                state = QuickAccessState(items = items, isLoading = false),
                onNavigateBack = {},
                onNavigateToPath = { navigatedPath = it },
                onNavigateToSaf = {},
                onTogglePin = {},
                onRemoveItem = {},
                onAddCustomFolder = { _, _ -> },
                onAddSafFolder = { _, _ -> }
            )
        }

        // Click on the item (label or whole area)
        composeTestRule.onNodeWithText("Downloads").performClick()

        // Verify navigation callback was triggered
        assert(navigatedPath == "/downloads")
    }
}
