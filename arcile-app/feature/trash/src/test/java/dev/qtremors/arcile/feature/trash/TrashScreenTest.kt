package dev.qtremors.arcile.feature.trash

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.feature.trash.TrashState
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrashScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty trash renders trash empty state`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ArcileTestTheme {
                TrashScreen(
                    state = TrashState(isLoading = false),
                    navigationActions = testNavigationActions(),
                    selectionActions = testSelectionActions(),
                    fileActions = testFileActions(),
                    restoreActions = testRestoreActions(),
                    deleteActions = testDeleteActions(),
                    presentationActions = testPresentationActions(),
                    feedbackActions = testFeedbackActions()
                )
            }
        }

        composeRule.onNodeWithText("Trash is empty").assertExists()
    }

    @Test
    fun `empty trash search renders search empty state`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            ArcileTestTheme {
                TrashScreen(
                    state = TrashState(
                        isLoading = false,
                        searchQuery = "invoice",
                        searchResults = emptyList(),
                        isSearching = false
                    ),
                    navigationActions = testNavigationActions(),
                    selectionActions = testSelectionActions(),
                    fileActions = testFileActions(),
                    restoreActions = testRestoreActions(),
                    deleteActions = testDeleteActions(),
                    presentationActions = testPresentationActions(),
                    feedbackActions = testFeedbackActions()
                )
            }
        }

        composeRule.onNodeWithText("No results found").assertExists()
    }

    @Test
    fun `restore icon confirms before restoring one item`() {
        val item = screenTrashItem()
        var restoredItemId: String? = null
        composeRule.setContent {
            ArcileTestTheme {
                TrashScreen(
                    state = TrashState(
                        isLoading = false,
                        trashFiles = listOf(item),
                        visibleTrashFiles = listOf(item)
                    ),
                    navigationActions = testNavigationActions(),
                    selectionActions = testSelectionActions(),
                    fileActions = testFileActions(),
                    restoreActions = TrashRestoreActions(
                        restoreSelected = {},
                        restoreItem = { restoredItemId = it },
                        dismissDestinationPicker = {},
                        restoreToDestination = { _, _ -> },
                        undoLastRestore = {},
                        clearPendingUndo = {}
                    ),
                    deleteActions = testDeleteActions(),
                    presentationActions = testPresentationActions(),
                    feedbackActions = testFeedbackActions()
                )
            }
        }

        composeRule.onNodeWithContentDescription("Restore holiday.jpg").performClick()
        composeRule.onNodeWithText("Restore “holiday.jpg”?").assertExists()
        composeRule.onNodeWithText("Restore").performClick()

        assertEquals(item.id, restoredItemId)
    }
}

private fun testNavigationActions() = TrashNavigationActions {}

private fun testSelectionActions() = TrashSelectionActions({}, {}, {}, {}, {})

private fun testFileActions() = TrashFileActions({}, {}, {})

private fun testRestoreActions() = TrashRestoreActions({}, {}, {}, { _, _ -> }, {}, {})

private fun testDeleteActions() = TrashDeleteActions({}, {}, {})

private fun testPresentationActions() = TrashPresentationActions({}, {}, {}, {}, null)

private fun testFeedbackActions() = TrashFeedbackActions({}, {}, {})

private fun screenTrashItem() = TrashMetadata(
    id = "trash-item-1",
    originalPath = "/storage/emulated/0/DCIM/holiday.jpg",
    deletionTime = System.currentTimeMillis(),
    fileModel = FileModel(
        name = "holiday.jpg",
        absolutePath = "/storage/emulated/0/.arcile/.trash/trash-item-1",
        size = 1_024L,
        lastModified = 1_700_000_000_000L,
        extension = "jpg",
        mimeType = "image/jpeg"
    ),
    sourceVolumeId = "primary",
    sourceStorageKind = StorageKind.INTERNAL
)
