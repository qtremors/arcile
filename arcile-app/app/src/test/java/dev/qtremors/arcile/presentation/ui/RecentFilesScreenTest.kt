package dev.qtremors.arcile.presentation.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesState
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
}

private fun recentScreenFile(name: String, path: String) = FileModel(
    name = name,
    absolutePath = path,
    size = 128L,
    lastModified = 1_700_000_000_000L,
    isDirectory = false,
    extension = "aac",
    isHidden = false
)
