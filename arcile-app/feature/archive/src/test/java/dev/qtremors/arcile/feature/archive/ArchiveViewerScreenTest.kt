package dev.qtremors.arcile.feature.archive

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.feature.archive.ArchiveViewerState
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
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
                    navigationActions = ArchiveNavigationActions(
                        navigateBack = {},
                        navigateUpInArchive = { false },
                        openFolder = {},
                        searchQueryChange = {}
                    ),
                    extractionActions = ArchiveExtractionActions(
                        extractAll = {},
                        extractCurrentFolder = {},
                        submitPassword = {},
                        selectNameEncoding = {},
                        cancelExtraction = {},
                        clearError = {},
                        clearOperationStatusMessage = {},
                        clearActiveOperation = {}
                    ),
                    conflictActions = ArchiveConflictActions(
                        setResolution = { _, _ -> },
                        applyResolutionToAll = {},
                        confirmResolutions = {},
                        dismissConflicts = {}
                    ),
                    selectionActions = ArchiveSelectionActions(
                        toggleItem = {},
                        clear = {},
                        extractSelected = {},
                        selectAll = {}
                    )
                )
            }
        }

        composeRule.onNodeWithText("Archive is empty").assertExists()
        composeRule.onNodeWithText("This archive does not contain any files or folders.").assertExists()
    }
}
