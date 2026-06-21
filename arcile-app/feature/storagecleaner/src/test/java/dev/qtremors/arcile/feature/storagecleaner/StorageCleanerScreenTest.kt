package dev.qtremors.arcile.feature.storagecleaner

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerBackAction
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerScreen
import dev.qtremors.arcile.feature.storagecleaner.ui.resolveStorageCleanerBackAction
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Assert.assertEquals
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
    fun `cleaner back priority dismisses local state before navigation`() {
        assertEquals(
            StorageCleanerBackAction.DismissIgnoredItems,
            resolveStorageCleanerBackAction(showIgnoredItems = true, showDeleteConfirm = true, hasActiveDetails = true)
        )
        assertEquals(
            StorageCleanerBackAction.DismissDeleteConfirmation,
            resolveStorageCleanerBackAction(showIgnoredItems = false, showDeleteConfirm = true, hasActiveDetails = true)
        )
        assertEquals(
            StorageCleanerBackAction.DismissDetails,
            resolveStorageCleanerBackAction(showIgnoredItems = false, showDeleteConfirm = false, hasActiveDetails = true)
        )
        assertEquals(
            StorageCleanerBackAction.NavigateBack,
            resolveStorageCleanerBackAction(showIgnoredItems = false, showDeleteConfirm = false, hasActiveDetails = false)
        )
    }

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
        composeRule.onNodeWithText("High risk").assertExists()
        composeRule.onNodeWithText("System-owned path").assertExists()
        composeRule.onNode(hasTestTag("checkbox_/storage/emulated/0/Android/data/com.example/cache/debug.log")).performClick()
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

    @Test
    fun `cleaner shows thumbnail cache controls`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(),
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onUndoClean = {},
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNodeWithText("Thumbnail cache").assertExists()
        composeRule.onNodeWithText("Clear").assertExists()
    }

    @Test
    fun `duplicate compare sheet opens from duplicate group`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        groups = listOf(
                            CleanerGroup(
                                CleanerGroupType.Duplicates,
                                listOf(
                                    CleanerCandidate(
                                        name = "same.jpg",
                                        absolutePath = "/storage/emulated/0/DCIM/same.jpg",
                                        size = 128L,
                                        lastModified = 1L,
                                        groupTypes = setOf(CleanerGroupType.Duplicates),
                                        duplicateGroupKey = "128:test"
                                    ),
                                    CleanerCandidate(
                                        name = "same.jpg",
                                        absolutePath = "/storage/emulated/0/Download/same.jpg",
                                        size = 128L,
                                        lastModified = 2L,
                                        groupTypes = setOf(CleanerGroupType.Duplicates),
                                        duplicateGroupKey = "128:test"
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

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Duplicate files"))
        composeRule.onNodeWithText("Duplicate files").performClick()
        composeRule.onNodeWithText("Compare").performClick()

        composeRule.onNodeWithText("Compare duplicates").assertExists()
        composeRule.onAllNodesWithText("Keep file").assertCountEquals(2)
    }

    @Test
    fun `duplicate compare delete icon asks for confirmation before cleaning one file`() {
        var cleanedPaths: List<String>? = null
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        groups = listOf(
                            CleanerGroup(
                                CleanerGroupType.Duplicates,
                                listOf(
                                    CleanerCandidate(
                                        name = "same.jpg",
                                        absolutePath = "/storage/emulated/0/DCIM/same.jpg",
                                        size = 128L,
                                        lastModified = 1L,
                                        groupTypes = setOf(CleanerGroupType.Duplicates),
                                        duplicateGroupKey = "128:test"
                                    ),
                                    CleanerCandidate(
                                        name = "same.jpg",
                                        absolutePath = "/storage/emulated/0/Download/same.jpg",
                                        size = 128L,
                                        lastModified = 2L,
                                        groupTypes = setOf(CleanerGroupType.Duplicates),
                                        duplicateGroupKey = "128:test"
                                    )
                                )
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { paths, _ -> cleanedPaths = paths },
                    onUndoClean = {},
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Duplicate files"))
        composeRule.onNodeWithText("Duplicate files").performClick()
        composeRule.onNodeWithText("Compare").performClick()
        composeRule.onAllNodesWithContentDescription("Delete this file")[0].performScrollTo().performClick()

        composeRule.onNodeWithText("Move selected files to Trash?").assertExists()
        assertEquals(null, cleanedPaths)
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) {
            cleanedPaths != null
        }
        assertEquals(listOf("/storage/emulated/0/DCIM/same.jpg"), cleanedPaths)
    }

    @Test
    fun `section settings opens from details sheet`() {
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
                                        lastModified = 1_000L,
                                        groupTypes = setOf(CleanerGroupType.Junk),
                                        riskLevel = CleanerRiskLevel.Low,
                                        riskReasons = setOf(CleanerRiskReason.TemporaryOrCache)
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
        composeRule.onAllNodesWithContentDescription("Section settings")[0].performClick()

        composeRule.onNodeWithText("Show this section").assertExists()
        composeRule.onNodeWithText("Ignore name patterns").assertExists()
    }

    @Test
    fun `ignore action reports exact path`() {
        var ignoredPath: String? = null
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
                                        lastModified = 1_000L,
                                        groupTypes = setOf(CleanerGroupType.Junk),
                                        riskLevel = CleanerRiskLevel.Low,
                                        riskReasons = setOf(CleanerRiskReason.TemporaryOrCache)
                                    )
                                )
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onUndoClean = {},
                    onClearMessages = {},
                    onIgnorePath = { ignoredPath = it }
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Junk files"))
        composeRule.onNodeWithText("Junk files").performClick()
        composeRule.onNodeWithText("Ignore").performClick()

        assertEquals("/storage/emulated/0/cache/safe.tmp", ignoredPath)
    }

    @Test
    fun `duplicate metadata shows seconds and no dynamic related app label`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        groups = listOf(
                            CleanerGroup(
                                CleanerGroupType.Duplicates,
                                listOf(
                                    CleanerCandidate(
                                        name = "same.jpg",
                                        absolutePath = "/storage/emulated/0/Android/data/com.whatsapp/cache/same.jpg",
                                        size = 128L,
                                        lastModified = 1_000L,
                                        groupTypes = setOf(CleanerGroupType.Duplicates),
                                        riskLevel = CleanerRiskLevel.High,
                                        riskReasons = setOf(CleanerRiskReason.AppLikeFolder),
                                        duplicateGroupKey = "128:test"
                                    ),
                                    CleanerCandidate(
                                        name = "same-copy.jpg",
                                        absolutePath = "/storage/emulated/0/Download/same-copy.jpg",
                                        size = 128L,
                                        lastModified = 1_000L,
                                        groupTypes = setOf(CleanerGroupType.Duplicates),
                                        duplicateGroupKey = "128:test"
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

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Duplicate files"))
        composeRule.onNodeWithText("Duplicate files").performClick()
        composeRule.onNodeWithText("Compare").performClick()

        composeRule.onNode(
            hasTestTag("cleaner_duplicate_timestamp_/storage/emulated/0/Android/data/com.whatsapp/cache/same.jpg") and
                hasText(":01", substring = true)
        ).assertExists()
        composeRule.onAllNodesWithText("Related to WhatsApp").assertCountEquals(0)
    }
}
