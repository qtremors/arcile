package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.feature.recentfiles.RecentFilesState
import dev.qtremors.arcile.feature.recentfiles.ui.RecentFilesScreen
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentFilesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders duplicate recent file paths without lazy key collision`() {
        val duplicate = recentScreenFile(
            name = "Friday at 4-35 pm (1).aac",
            path = "/storage/emulated/0/Recordings/Friday at 4-35 pm (1).aac"
        )

        composeRule.setContent {
            ArcileTestTheme {
                RecentFilesScreen(
                    state = RecentFilesState(
                        recentFiles = listOf(duplicate, duplicate),
                        displayedRecentFiles = listOf(duplicate, duplicate),
                        isLoading = false,
                        hasMore = false,
                        todayStart = 0L,
                        yesterdayStart = 0L
                    ),
                    onNavigateBack = {},
                    onOpenFile = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onShareSelected = {},
                    onSelectAll = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Friday at 4-35 pm (1).aac").assertCountEquals(2)
    }

    @Test
    fun `sort action opens browser presentation sheet`() {
        composeRule.setContent {
            ArcileTestTheme {
                RecentFilesScreen(
                    state = recentScreenState(),
                    onNavigateBack = {},
                    onOpenFile = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onShareSelected = {},
                    onSelectAll = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sort").performClick()

        composeRule.onNodeWithText("Sort recent files").assertExists()
        composeRule.onNodeWithText("View mode").assertExists()
        composeRule.onNodeWithText("Grid View").assertExists()
    }

    @Test
    fun `search mode exposes filter action`() {
        composeRule.setContent {
            ArcileTestTheme {
                RecentFilesScreen(
                    state = recentScreenState().copy(
                        searchQuery = "photo",
                        searchResults = listOf(recentScreenFile("photo.jpg", "/storage/emulated/0/DCIM/photo.jpg"))
                    ),
                    onNavigateBack = {},
                    onOpenFile = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onShareSelected = {},
                    onSelectAll = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Filters").performClick()

        composeRule.onNodeWithText("Search Filters").assertExists()
    }

    @Test
    fun `grid presentation renders recent file`() {
        composeRule.setContent {
            ArcileTestTheme {
                RecentFilesScreen(
                    state = recentScreenState().copy(
                        presentation = FileListingPreferences(viewMode = FileViewMode.GRID)
                    ),
                    onNavigateBack = {},
                    onOpenFile = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onShareSelected = {},
                    onSelectAll = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithText("photo.jpg").assertExists()
    }

    @Test
    fun `grid presentation renders grouped date header`() {
        composeRule.setContent {
            ArcileTestTheme {
                RecentFilesScreen(
                    state = recentScreenState().copy(
                        presentation = FileListingPreferences(
                            sortOption = FileSortOption.DATE_NEWEST,
                            viewMode = FileViewMode.GRID
                        )
                    ),
                    onNavigateBack = {},
                    onOpenFile = {},
                    onToggleSelection = {},
                    onClearSelection = {},
                    onRequestDeleteSelected = {},
                    onConfirmDelete = {},
                    onTogglePermanentDelete = {},
                    onDismissDeleteConfirmation = {},
                    onShareSelected = {},
                    onSelectAll = {},
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithText("Today").assertExists()
        composeRule.onNodeWithText("photo.jpg").assertExists()
    }
}

private fun recentScreenState(): RecentFilesState {
    val file = recentScreenFile("photo.jpg", "/storage/emulated/0/DCIM/photo.jpg")
    return RecentFilesState(
        recentFiles = listOf(file),
        displayedRecentFiles = listOf(file),
        isLoading = false,
        hasMore = false,
        todayStart = 0L,
        yesterdayStart = 0L
    )
}

private fun recentScreenFile(name: String, path: String) = FileModel(
    name = name,
    absolutePath = path,
    size = 128L,
    lastModified = 1_700_000_000_000L,
    isDirectory = false,
    extension = name.substringAfterLast('.', ""),
    isHidden = false
)
