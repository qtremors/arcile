package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.presentation.browser.BrowserState
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `search result click opens file and clears search`() {
        var openedPath: String? = null
        var clearSearchCalls = 0

        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        browserSearchQuery = "report",
                        searchResults = listOf(browserFile("report.pdf", "/storage/emulated/0/Docs/report.pdf")),
                        isSearching = false,
                        isLoading = false
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
                    onOpenFile = { openedPath = it },
                    onToggleSelection = {},
                    onSelectMultiple = {},
                    onClearSelection = {},
                    onCreateFolder = {},
                    onCreateFile = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onRenameFile = { _, _ -> },
                    onSearchQueryChange = {},
                    onClearSearch = { clearSearchCalls += 1 },
                    onSortOptionChange = { _, _ -> },
                    onGridViewChange = {},
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("report.pdf").performClick()

        assertEquals("/storage/emulated/0/Docs/report.pdf", openedPath)
        assertEquals(1, clearSearchCalls)
    }

    @Test
    fun `volume root click navigates into selected volume`() {
        var navigatedPath: String? = null

        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isVolumeRootScreen = true,
                        isLoading = false,
                        storageVolumes = listOf(browserVolume("primary", "Internal", "/storage/emulated/0"))
                    ),
                    onNavigateBack = {},
                    onNavigateTo = { navigatedPath = it },
                    onOpenFile = {},
                    onToggleSelection = {},
                    onSelectMultiple = {},
                    onClearSelection = {},
                    onCreateFolder = {},
                    onCreateFile = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onRenameFile = { _, _ -> },
                    onSearchQueryChange = {},
                    onClearSearch = {},
                    onSortOptionChange = { _, _ -> },
                    onGridViewChange = {},
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Internal").performClick()

        assertEquals("/storage/emulated/0", navigatedPath)
    }
}

private fun browserFile(name: String, path: String) = FileModel(
    name = name,
    absolutePath = path,
    size = 20L,
    lastModified = 50L,
    isDirectory = false,
    extension = name.substringAfterLast('.', ""),
    isHidden = false
)

private fun browserVolume(id: String, name: String, path: String) = StorageVolume(
    id = id,
    storageKey = id,
    name = name,
    path = path,
    totalBytes = 2_000L,
    freeBytes = 500L,
    isPrimary = true,
    isRemovable = false,
    kind = StorageKind.INTERNAL
)
