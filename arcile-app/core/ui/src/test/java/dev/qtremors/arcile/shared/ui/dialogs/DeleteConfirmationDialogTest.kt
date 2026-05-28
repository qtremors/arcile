package dev.qtremors.arcile.shared.ui.dialogs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.DeleteDestination
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
    fun `trash mode shows decision copy and toggles permanent delete`() {
        var toggleCount = 0

        composeRule.setContent {
            ArcileTestTheme {
                DeleteConfirmationDialog(
                    selectedCount = 2,
                    isPermanentDeleteChecked = false,
                    isPermanentDeleteToggleEnabled = true,
                    onConfirm = {},
                    onDismiss = {},
                    onTogglePermanentDelete = { toggleCount++ },
                    decision = DeleteDecision(
                        destination = DeleteDestination.Trash,
                        selectedCount = 2,
                        totalBytes = 2048L,
                        fileCount = 1,
                        folderCount = 1,
                        irreversible = false
                    )
                )
            }
        }

        composeRule.onNodeWithText("Destination: Trash Bin").assertExists()
        composeRule.onNodeWithText("Selected items will be moved to the Trash Bin. You can restore them later.").assertExists()
        composeRule.onNodeWithText("2 items • 2.0 KB • 1 folders").assertExists()
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
                    onTogglePermanentDelete = {},
                    decision = DeleteDecision(
                        destination = DeleteDestination.Permanent,
                        selectedCount = 1,
                        totalBytes = 0L,
                        fileCount = 1,
                        folderCount = 0,
                        irreversible = true
                    )
                )
            }
        }

        composeRule.onNodeWithText("Destination: Permanent delete").assertExists()
        composeRule.onNodeWithText("Selected items will be permanently deleted. This action cannot be undone.").assertExists()
        composeRule.onNodeWithText("Irreversible: files cannot be restored from Arcile Trash.").assertExists()
        composeRule.onNodeWithTag("permanent_delete_switch", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `mixed mode shows blocked decision without confirm action`() {
        composeRule.setContent {
            ArcileTestTheme {
                DeleteConfirmationDialog(
                    selectedCount = 3,
                    isPermanentDeleteChecked = true,
                    isPermanentDeleteToggleEnabled = false,
                    onConfirm = {},
                    onDismiss = {},
                    onTogglePermanentDelete = {},
                    decision = DeleteDecision(
                        destination = DeleteDestination.MixedBlocked,
                        selectedCount = 3,
                        totalBytes = 0L,
                        fileCount = 2,
                        folderCount = 1,
                        irreversible = true
                    )
                )
            }
        }

        composeRule.onNodeWithText("Mixed delete blocked").assertExists()
        composeRule.onNodeWithText("This selection combines reversible trash and permanent delete destinations. Delete these groups separately to avoid accidental data loss.").assertExists()
    }
}
