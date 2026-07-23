package dev.qtremors.arcile.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.qtremors.arcile.core.presentation.FolderTab
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderTabsRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `large folder collections only measure the selected viewport`() {
        val tabs = buildList {
            add(FolderTab(path = null, label = "All", count = 5_000, totalSizeBytes = 5_000))
            repeat(5_000) { index ->
                add(
                    FolderTab(
                        path = "/storage/emulated/0/Folder $index",
                        label = "Folder $index",
                        count = 1,
                        totalSizeBytes = 1
                    )
                )
            }
        }
        val selectedPath = tabs.last().path

        composeRule.setContent {
            ArcileTestTheme {
                FolderTabsRow(
                    tabs = tabs,
                    selectedPath = selectedPath,
                    onSelectTab = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Folder 4999", substring = true).assertExists()
    }
}
