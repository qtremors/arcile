package dev.qtremors.arcile.presentation.ui.components.dialogs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeleteConfirmationDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `trash mode shows trash copy and toggles permanent delete`() {
        var toggleCount = 0

        composeRule.setContent {
            ArcileTestTheme {
                DeleteConfirmationDialog(
                    selectedCount = 2,
                    isPermanentDeleteChecked = false,
                    isPermanentDeleteToggleEnabled = true,
                    onConfirm = {},
                    onDismiss = {},
                    onTogglePermanentDelete = { toggleCount++ }
                )
            }
        }

        composeRule.onNodeWithText("Delete 2 item(s)?").assertExists()
        composeRule.onNodeWithText("Selected items will be moved to the Trash Bin. You can restore them later.").assertExists()
        composeRule.onNodeWithText("Permanently delete").performClick()

        assertEquals(1, toggleCount)
    }

    @Test
    fun `permanent mode shows permanent copy and disabled toggle when locked`() {
        composeRule.setContent {
            ArcileTestTheme {
                DeleteConfirmationDialog(
                    selectedCount = 1,
                    isPermanentDeleteChecked = true,
                    isPermanentDeleteToggleEnabled = false,
                    onConfirm = {},
                    onDismiss = {},
                    onTogglePermanentDelete = {}
                )
            }
        }

        composeRule.onNodeWithText("Permanently delete 1 item(s)?").assertExists()
        composeRule.onNodeWithText("Selected items will be permanently deleted. This action cannot be undone.").assertExists()
        composeRule.onNodeWithText("Permanently delete").assertIsNotEnabled()
    }
}
