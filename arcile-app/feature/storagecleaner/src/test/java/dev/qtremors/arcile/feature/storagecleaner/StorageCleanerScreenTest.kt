package dev.qtremors.arcile.feature.storagecleaner

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerScreen
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageCleanerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `high risk candidates show warning and require acknowledgement before delete`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        groups = listOf(
                            CleanerGroup(
                                CleanerGroupType.Junk,
                                listOf(
                                    CleanerCandidate(
                                        name = "debug.log",
                                        absolutePath = "/storage/emulated/0/Android/data/com.example/cache/debug.log",
                                        size = 128L,
                                        lastModified = 0L,
                                        groupTypes = setOf(CleanerGroupType.Junk),
                                        riskLevel = CleanerRiskLevel.High,
                                        riskReasons = setOf(CleanerRiskReason.SystemOwnedPath)
                                    )
                                )
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onUndoClean = {},
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Junk files"))
        composeRule.onNodeWithText("Junk files").performClick()
        composeRule.onNodeWithText("High").assertExists()
        composeRule.onNodeWithText("system-owned path").assertExists()
        composeRule.onNodeWithText("/storage/emulated/0/Android/data/com.example/cache/debug.log").performClick()
        composeRule.onNodeWithText("Move 1 to Trash • 128.0 B").performClick()

        composeRule.onNodeWithText("I reviewed these high-risk files and still want to move them to Trash.").assertExists()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun `select all skips high risk candidates`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        groups = listOf(
                            CleanerGroup(
                                CleanerGroupType.Junk,
                                listOf(
                                    CleanerCandidate(
                                        name = "safe.tmp",
                                        absolutePath = "/storage/emulated/0/cache/safe.tmp",
                                        size = 64L,
                                        lastModified = 0L,
                                        groupTypes = setOf(CleanerGroupType.Junk),
                                        riskLevel = CleanerRiskLevel.Low,
                                        riskReasons = setOf(CleanerRiskReason.TemporaryOrCache)
                                    ),
                                    CleanerCandidate(
                                        name = "debug.log",
                                        absolutePath = "/storage/emulated/0/Android/data/com.example/cache/debug.log",
                                        size = 128L,
                                        lastModified = 0L,
                                        groupTypes = setOf(CleanerGroupType.Junk),
                                        riskLevel = CleanerRiskLevel.High,
                                        riskReasons = setOf(CleanerRiskReason.SystemOwnedPath)
                                    )
                                )
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onUndoClean = {},
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Junk files"))
        composeRule.onNodeWithText("Junk files").performClick()
        composeRule.onAllNodesWithText("Select all")[0].performClick()
        composeRule.onNodeWithText("Move 1 to Trash • 64.0 B").performClick()

        composeRule.onNodeWithText("1 selected file(s) will be moved to Arcile Trash. You can restore them later.").assertExists()
    }
}
