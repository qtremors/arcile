package dev.qtremors.arcile.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.feature.quickaccess.QuickAccessState
import dev.qtremors.arcile.feature.quickaccess.QuickAccessScreen
import dev.qtremors.arcile.feature.quickaccess.QuickAccessActions
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
            QuickAccessItem("2", "WhatsApp", "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media", QuickAccessType.STANDARD, isPinned = true),
            QuickAccessItem("3", "Work", "/work", QuickAccessType.CUSTOM, isPinned = true),
            QuickAccessItem("4", "Android Data", "uri://data", QuickAccessType.SAF_TREE, isPinned = true)
        )

        composeTestRule.setContent {
            QuickAccessScreen(
                state = QuickAccessState(items = items, isLoading = false),
                actions = testActions()
            )
        }

        // Verify section headers are displayed
        composeTestRule.onNodeWithText("SYSTEM FOLDERS").assertIsDisplayed()
        composeTestRule.onNodeWithText("APP FOLDERS").assertIsDisplayed()
        composeTestRule.onNodeWithText("FILES APP SECTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("CUSTOM FOLDERS").assertIsDisplayed()

        // Verify labels are displayed
        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithText("WhatsApp").assertIsDisplayed()
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
                actions = testActions(navigateToPath = { navigatedPath = it })
            )
        }

        // Click on the item (label or whole area)
        composeTestRule.onNodeWithText("Downloads").performClick()

        // Verify navigation callback was triggered
        assert(navigatedPath == "/downloads")
    }

    @Test
    fun quickAccessScreen_toggleShowOnHome_triggersToggleCallback() {
        var toggledItem: QuickAccessItem? = null
        val item = QuickAccessItem("1", "Downloads", "/downloads", QuickAccessType.STANDARD, isPinned = true)

        composeTestRule.setContent {
            QuickAccessScreen(
                state = QuickAccessState(items = listOf(item), isLoading = false),
                actions = testActions(togglePin = { toggledItem = it })
            )
        }

        composeTestRule.onNodeWithContentDescription("Downloads Show on Home").performClick()

        assert(toggledItem == item)
    }
}

private fun testActions(
    navigateToPath: (String) -> Unit = {},
    togglePin: (QuickAccessItem) -> Unit = {}
) = QuickAccessActions(
    navigateBack = {},
    navigateToPath = navigateToPath,
    navigateToSaf = {},
    togglePin = togglePin,
    removeItem = {},
    addCustomFolder = { _, _ -> },
    requestSafFolder = {},
    addFilesShortcut = {},
    addAndroidDataShortcut = {},
    addAndroidObbShortcut = {},
    reorderItems = {}
)
