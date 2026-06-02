package dev.qtremors.arcile.feature.archive

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.feature.archive.ArchiveViewerState
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArchiveViewerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty archive renders archive empty state`() {
        composeRule.setContent {
            ArcileTestTheme {
                ArchiveViewerScreen(
                    state = ArchiveViewerState(
                        archivePath = "/storage/emulated/0/Download/empty.zip",
                        isLoading = false
                    ),
                    onNavigateBack = {},
                    onNavigateUpInArchive = { false },
                    onOpenFolder = {},
                    onSearchQueryChange = {},
                    onExtractAll = {},
                    onExtractCurrentFolder = {},
                    onSubmitPassword = {},
                    onSelectNameEncoding = {},
                    onSetConflictResolution = { _, _ -> },
                    onApplyConflictResolutionToAll = {},
                    onConfirmConflictResolutions = {},
                    onDismissConflicts = {},
                    onClearError = {},
                    onCancelExtraction = {},
                    onClearOperationStatusMessage = {},
                    onClearActiveOperation = {},
                    onToggleItemSelection = {},
                    onClearSelection = {},
                    onExtractSelected = {},
                    onSelectAll = {}
                )
            }
        }

        composeRule.onNodeWithText("Archive is empty").assertExists()
        composeRule.onNodeWithText("This archive does not contain any files or folders.").assertExists()
    }
}
