package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FolderStatsStatus
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.presentation.browser.BrowserFileOperationUiState
import dev.qtremors.arcile.presentation.browser.BrowserState
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Internal").performClick()

        assertEquals("/storage/emulated/0", navigatedPath)
    }

    @Test
    fun `category screen hides stale folder breadcrumbs`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        isCategoryScreen = true,
                        activeCategoryName = "Images",
                        currentPath = "/storage/emulated/0/Download",
                        currentVolumeId = "primary",
                        storageVolumes = listOf(browserVolume("primary", "Internal", "/storage/emulated/0"))
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Images").assertExists()
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
    }

    @Test
    fun `empty folder renders folder empty state`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        files = emptyList()
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Empty Directory").assertExists()
    }

    @Test
    fun `empty browser search renders search empty state`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        browserSearchQuery = "missing",
                        searchResults = emptyList(),
                        isSearching = false,
                        isLoading = false
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("No results found").assertExists()
        composeRule.onNodeWithText("We couldn't find anything matching \"missing\". Try a different keyword or filters.").assertExists()
    }

    @org.junit.Ignore("Outdated UI test, ModalBottomSheet interactions fail in Robolectric after 0.6.0")
    @Test
    fun `browser controls sheet shows grid controls when opened`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        browserViewMode = BrowserViewMode.GRID,
                        files = listOf(browserFile("photo.jpg", "/storage/emulated/0/Download/photo.jpg"))
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onRoot().printToLog("SEMANTICS")
        composeRule.onNodeWithContentDescription("Sort", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Name (A to Z)").assertExists()
    }

    @Test
    fun `folder rows render cached stats subtitle`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        files = listOf(browserFolder("Docs", "/storage/emulated/0/Download/Docs")),
                        folderStatsByPath = mapOf(
                            "/storage/emulated/0/Download/Docs" to FolderStats(
                                fileCount = 3,
                                totalBytes = 2048L,
                                cachedAt = System.currentTimeMillis()
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("3 files • 2.0 KB").assertExists()
    }

    @Test
    fun `folder rows show neutral subtitle immediately without calculating placeholder`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        files = listOf(browserFolder("Android", "/storage/emulated/0/Download/Android"))
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Folder").assertExists()
        composeRule.onAllNodesWithText("Calculating…").assertCountEquals(0)
    }

    @Test
    fun `folder rows show partial access subtitle`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        files = listOf(browserFolder("Android", "/storage/emulated/0/Download/Android")),
                        folderStatsByPath = mapOf(
                            "/storage/emulated/0/Download/Android" to FolderStats(
                                fileCount = 3,
                                totalBytes = 2048L,
                                cachedAt = System.currentTimeMillis(),
                                status = FolderStatsStatus.Partial
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("3 files • 2.0 KB").assertExists()
    }

    @Test
    fun `properties dialog opens from selection overflow`() {
        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        selectedFiles = setOf("/storage/emulated/0/Download/Docs"),
                        isPropertiesVisible = true,
                        properties = dev.qtremors.arcile.presentation.browser.PropertiesUiModel(
                            title = "Docs",
                            pathSummary = "/storage/emulated/0/Download/Docs",
                            itemCount = 1,
                            fileCount = 0,
                            folderCount = 1,
                            totalBytes = 2048L,
                            newestModifiedAt = 20L,
                            oldestModifiedAt = 20L,
                            mimeTypeSummary = null,
                            extensionSummary = null,
                            hiddenCount = 0,
                            accessStatus = dev.qtremors.arcile.domain.PropertiesAccessStatus.Partial,
                            folderFileCount = 3,
                            folderTotalBytes = 2048L,
                            isSingleItem = true,
                            isDirectory = true
                        )
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = {},
                    onShareSelected = {},
                    onOpenProperties = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Properties").assertExists()
        composeRule.onNodeWithText("Docs").assertExists()
    }

    @Test
    fun `active file operation replaces create fab with progress action`() {
        var cancelCalls = 0

        composeRule.setContent {
            ArcileTestTheme {
                BrowserScreen(
                    state = BrowserState(
                        isLoading = false,
                        currentPath = "/storage/emulated/0/Download",
                        activeFileOperation = BrowserFileOperationUiState(
                            type = BulkFileOperationType.COPY,
                            totalItems = 3,
                            completedItems = 1
                        )
                    ),
                    onNavigateBack = {},
                    onNavigateTo = {},
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
                    onPresentationChange = { _, _ -> },
                    onClearError = {},
                    onCopySelected = {},
                    onCutSelected = {},
                    onPasteFromClipboard = {},
                    onCancelClipboard = { cancelCalls += 1 },
                    onClearActiveFileOperation = { },
                    onShareSelected = {},
                    onCreateFakeFile = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Cancel transfer").assertExists()
        composeRule.onAllNodesWithContentDescription("Create new").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Cancel transfer").performClick()

        assertEquals(1, cancelCalls)
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

private fun browserFolder(name: String, path: String) = FileModel(
    name = name,
    absolutePath = path,
    size = 0L,
    lastModified = 50L,
    isDirectory = true,
    extension = "",
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
