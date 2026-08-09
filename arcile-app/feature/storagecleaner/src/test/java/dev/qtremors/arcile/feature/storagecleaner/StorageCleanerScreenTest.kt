package dev.qtremors.arcile.feature.storagecleaner

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanProgress
import dev.qtremors.arcile.core.ui.image.ThumbnailCacheStats
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import dev.qtremors.arcile.feature.storagecleaner.ui.CleanerCandidateRow
import dev.qtremors.arcile.feature.storagecleaner.ui.DuplicateGroupCard
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerGroupScreen
import dev.qtremors.arcile.feature.storagecleaner.ui.StorageCleanerScreen
import dev.qtremors.arcile.feature.storagecleaner.ui.cleanFilePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `cleaner path only removes the primary storage prefix`() {
        assertEquals("Download/report.pdf", cleanFilePath("/storage/emulated/0/Download/report.pdf"))
        assertEquals("", cleanFilePath("/storage/emulated/0"))
        assertEquals(
            "/mnt/media_rw/storage/emulated/0/report.pdf",
            cleanFilePath("/mnt/media_rw/storage/emulated/0/report.pdf")
        )
    }

    @Test
    fun `empty duplicate group renders safely`() {
        composeRule.setContent {
            ArcileTestTheme {
                DuplicateGroupCard(
                    filesInGroup = emptyList(),
                    selectedFiles = emptySet(),
                    onSelectedFilesChange = {},
                    onCompare = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `candidate checkbox invokes one selection callback`() {
        val file = candidate(CleanerGroupType.Junk)
        var callbackCount = 0
        composeRule.setContent {
            ArcileTestTheme {
                CleanerCandidateRow(
                    file = file,
                    selected = false,
                    onToggle = { callbackCount += 1 }
                )
            }
        }

        composeRule.onNode(hasTestTag("checkbox_${file.absolutePath}")).performClick()
        composeRule.runOnIdle { assertEquals(1, callbackCount) }
    }

    @Test
    fun `overview card opens its page without refresh or long press actions`() {
        var openedType: CleanerGroupType? = null
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(),
                    onNavigateBack = {},
                    onRefresh = {},
                    onOpenGroup = { openedType = it },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Junk files"))
        val card = composeRule.onNodeWithText("Junk files")
        assertFalse(card.fetchSemanticsNode().config.contains(SemanticsActions.OnLongClick))
        card.performClick()
        composeRule.onAllNodesWithContentDescription("Refresh").assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(CleanerGroupType.Junk, openedType) }
    }

    @Test
    fun `overview manual refresh retains detailed scan progress`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        isScanning = true,
                        isPullToRefreshing = true,
                        scanningGroups = CleanerGroupType.entries.toSet(),
                        scanProgress = StorageCleanerScanProgress(
                            scannedFiles = 240,
                            progressFraction = 0.6f,
                            estimatedRemainingMillis = 20_000L
                        )
                    ),
                    onNavigateBack = {},
                    onRefresh = {},
                    onClearMessages = {}
                )
            }
        }

        composeRule.onAllNodesWithText(
            "240 items scanned • 60% • about 20s left",
            useUnmergedTree = true
        )[0].assertExists()
    }

    @Test
    fun `empty cleaner page keeps scroll semantics for pull refresh`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = stateWith(CleanerGroupType.Junk),
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).assertExists()
        composeRule.onNodeWithText("Nothing found").assertExists()
    }

    @Test
    fun `unloaded cleaner page remains refreshable after failure`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = StorageCleanerState(),
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasScrollAction()).assertExists()
        composeRule.onNodeWithText("Nothing found").assertExists()
    }

    @Test
    fun `overview shows thumbnail cache controls and ignored items`() {
        val ignoredPath = "/storage/emulated/0/Download/APK/MyInsta_v2.apk"
        var restoredPath: String? = null
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerScreen(
                    state = StorageCleanerState(
                        rules = StorageCleanerRules(ignoredPaths = setOf(ignoredPath)),
                        thumbnailCache = CleanerThumbnailCacheState(
                            stats = ThumbnailCacheStats(
                                diskBytes = 512L,
                                memoryBytes = 256L,
                                loadedCount = 3,
                                failedCount = 1
                            ),
                            isLoading = false
                        )
                    ),
                    onNavigateBack = {},
                    onRefresh = {},
                    onClearMessages = {},
                    onUnignorePath = { restoredPath = it }
                )
            }
        }

        composeRule.onNodeWithText("Thumbnail cache").assertExists()
        composeRule.onAllNodesWithContentDescription("Ignored everywhere")[0].performClick()
        composeRule.onNodeWithText("MyInsta_v2.apk").assertExists()
        composeRule.onNodeWithText("Stop ignoring").performClick()
        assertEquals(ignoredPath, restoredPath)
    }

    @Test
    fun `cleaner page shows progress and no refresh button`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = StorageCleanerState(
                        isScanning = true,
                        isPullToRefreshing = true,
                        scanningGroups = setOf(CleanerGroupType.Junk),
                        scanProgress = StorageCleanerScanProgress(
                            scannedFiles = 120,
                            progressFraction = 0.5f,
                            estimatedRemainingMillis = 10_000L
                        )
                    ),
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNodeWithText("120 items scanned • 50% • about 10s left").assertExists()
        composeRule.onAllNodesWithContentDescription("Refresh Junk files").assertCountEquals(0)
    }

    @Test
    fun `high risk candidates require acknowledgement before delete`() {
        val highRisk = candidate(
            type = CleanerGroupType.Junk,
            path = "/storage/emulated/0/Android/data/com.example/cache/debug.log",
            riskLevel = CleanerRiskLevel.High,
            reasons = setOf(CleanerRiskReason.SystemOwnedPath)
        )
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = stateWith(CleanerGroupType.Junk, highRisk),
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasTestTag("checkbox_${highRisk.absolutePath}")).performClick()
        composeRule.onNodeWithText("Move 1 to Trash • 64.0 B").performClick()
        composeRule.onNodeWithText("I reviewed these high-risk files and still want to move them to Trash.").assertExists()
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
    }

    @Test
    fun `restored confirmation stays disabled until candidates are validated`() {
        val highRisk = candidate(
            type = CleanerGroupType.Junk,
            path = "/storage/emulated/0/Android/data/com.example/cache/debug.log",
            riskLevel = CleanerRiskLevel.High,
            reasons = setOf(CleanerRiskReason.SystemOwnedPath)
        )
        val state = mutableStateOf(stateWith(CleanerGroupType.Junk, highRisk))
        var cleanCalls = 0
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = state.value,
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> cleanCalls += 1 },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasTestTag("checkbox_${highRisk.absolutePath}")).performClick()
        composeRule.onNodeWithText("Move 1 to Trash • 64.0 B").performClick()
        composeRule.runOnIdle { state.value = StorageCleanerState() }
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
        composeRule.runOnIdle {
            state.value = stateWith(CleanerGroupType.Junk, highRisk)
        }
        composeRule.onNodeWithText("Delete").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, cleanCalls) }
    }

    @Test
    fun `section settings open from cleaner page`() {
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = stateWith(CleanerGroupType.Junk, candidate(CleanerGroupType.Junk)),
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Junk files rules")[0].performClick()
        composeRule.onNodeWithText("Changes here apply only to Junk files.").assertExists()
        composeRule.onNodeWithText("Show this cleaner").assertExists()
        composeRule.onNodeWithText("Excluded names").assertExists()
    }

    @Test
    fun `duplicate comparison and selection survive state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        val first = candidate(
            type = CleanerGroupType.Duplicates,
            path = "/storage/emulated/0/DCIM/same-one.jpg",
            duplicateGroupKey = "128:test"
        )
        val second = first.copy(
            name = "same-two.jpg",
            absolutePath = "/storage/emulated/0/Download/same-two.jpg",
            lastModified = 2L
        )
        restorationTester.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = stateWith(CleanerGroupType.Duplicates, first, second),
                    type = CleanerGroupType.Duplicates,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNodeWithText("Compare").performClick()
        composeRule.onAllNodesWithText("Keep file")[0].performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Compare duplicates").assertExists()
        composeRule.onAllNodesWithText("Keep file").assertCountEquals(1)
        composeRule.onAllNodesWithText("Move 1 to Trash • 64.0 B").assertCountEquals(2)
    }

    @Test
    fun `selection survives while restored cleaner data reloads`() {
        val file = candidate(CleanerGroupType.Junk)
        val state = mutableStateOf(stateWith(CleanerGroupType.Junk, file))
        composeRule.setContent {
            ArcileTestTheme {
                StorageCleanerGroupScreen(
                    state = state.value,
                    type = CleanerGroupType.Junk,
                    onNavigateBack = {},
                    onRefresh = {},
                    onCleanFiles = { _, _ -> },
                    onClearMessages = {}
                )
            }
        }

        composeRule.onNode(hasTestTag("checkbox_${file.absolutePath}")).performClick()
        composeRule.runOnIdle { state.value = StorageCleanerState() }
        composeRule.onNodeWithText("Move 1 to Trash • 64.0 B").assertDoesNotExist()
        composeRule.runOnIdle {
            state.value = stateWith(CleanerGroupType.Junk, file)
        }
        composeRule.onNodeWithText("Move 1 to Trash • 64.0 B").assertExists()
    }

    private fun stateWith(type: CleanerGroupType, vararg candidates: CleanerCandidate) =
        StorageCleanerState(
            groups = listOf(CleanerGroup(type, candidates.toList())),
            loadedGroups = setOf(type)
        )

    private fun candidate(
        type: CleanerGroupType,
        path: String = "/storage/emulated/0/cache/safe.tmp",
        riskLevel: CleanerRiskLevel = CleanerRiskLevel.Low,
        reasons: Set<CleanerRiskReason> = emptySet(),
        duplicateGroupKey: String? = null
    ) = CleanerCandidate(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 64L,
        lastModified = 1L,
        groupTypes = setOf(type),
        riskLevel = riskLevel,
        riskReasons = reasons,
        duplicateGroupKey = duplicateGroupKey
    )
}
