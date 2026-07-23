package dev.qtremors.arcile.feature.storageusage.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.feature.storageusage.StorageUsageUiState
import dev.qtremors.arcile.core.ui.testing.ArcileTestTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageUsageMapTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `chart exposes selected node state description`() {
        setStorageUsageContent()

        composeRule
            .onNode(hasStateDescription("Internal selected, 1.0 KB, 2 items"))
            .assertExists()
    }

    @Test
    fun `fallback segment row selects node`() {
        var selectedPath: String? = null
        setStorageUsageContent(onSelectedNode = { selectedPath = it.path })

        composeRule
            .onNodeWithContentDescription("Downloads, 768.0 B, 1 items")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("/storage/emulated/0/Download", selectedPath)
    }

    @Test
    fun `fallback segment row drills into folder on double tap`() {
        var drilledPath: String? = null
        setStorageUsageContent(onDrilledNode = { drilledPath = it.path })

        composeRule
            .onNodeWithContentDescription("Downloads, 768.0 B, 1 items")
            .performTouchInput { doubleClick() }
        composeRule.waitForIdle()

        assertEquals("/storage/emulated/0/Download", drilledPath)
    }

    @Test
    fun `fallback segment row opens folder on long press and shows share`() {
        var openedPath: String? = null
        setStorageUsageContent(onOpenedPath = { openedPath = it })

        composeRule.onNodeWithText("768.0 B").assertExists()
        composeRule.onNodeWithText("75.0%").assertExists()
        composeRule
            .onNodeWithContentDescription("Downloads, 768.0 B, 1 items")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals("/storage/emulated/0/Download", openedPath)
    }

    @Test
    fun `long pressing chart center resets storage overview`() {
        var resetRequested = false
        setStorageUsageContent(onResetToOverview = { resetRequested = true })

        composeRule
            .onNodeWithContentDescription("Storage usage chart for Internal")
            .performTouchInput { longClick(center) }
        composeRule.waitForIdle()

        assertTrue(resetRequested)
    }

    @Test
    fun `root path labels zero as internal storage and remains clickable`() {
        var clickedIndex: Int? = null
        composeRule.setContent {
            ArcileTestTheme {
                StorageUsageBreadcrumbs(
                    breadcrumbs = listOf(storageUsageRoot().copy(name = "0")),
                    onBreadcrumbClick = { clickedIndex = it }
                )
            }
        }

        composeRule.onNodeWithText("Internal Storage").performClick()

        assertEquals(0, clickedIndex)
    }

    @Test
    fun `custom segment action selects node`() {
        setStorageUsageContent()

        val actions = composeRule
            .onNodeWithContentDescription("Storage usage chart for Internal")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        actions.first { it.label == "Select Documents" }.action()

        composeRule.onNode(hasStateDescription("Documents selected, 256.0 B, 1 items")).assertExists()
    }

    @Test
    fun `custom root action selects populated internal storage node`() {
        var selectedNode: StorageUsageNode? = null
        setStorageUsageContent(onSelectedNode = { selectedNode = it })

        val actions = composeRule
            .onNodeWithContentDescription("Storage usage chart for Internal")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        actions.first { it.label == "Select Internal" }.action()

        assertEquals("/storage/emulated/0", selectedNode?.path)
        assertEquals(2, selectedNode?.children?.size)
    }

    @Test
    fun `keyboard navigation selects next major segment`() {
        setStorageUsageContent()

        composeRule
            .onNodeWithContentDescription("Storage usage chart for Internal")
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule
            .onNodeWithContentDescription("Storage usage chart for Internal")
            .performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNode(hasStateDescription("Downloads selected, 768.0 B, 1 items")).assertExists()
    }

    @Test
    fun `sunburst segment generation is bounded for large trees`() {
        val root = StorageUsageNode(
            name = "Internal",
            path = "/storage/emulated/0",
            sizeBytes = 10_000,
            kind = StorageUsageNodeKind.Folder,
            childCount = 300,
            children = (0 until 300).map { folderIndex ->
                StorageUsageNode(
                    name = "Folder $folderIndex",
                    path = "/storage/emulated/0/folder$folderIndex",
                    sizeBytes = 100,
                    kind = StorageUsageNodeKind.Folder,
                    childCount = 50,
                    children = (0 until 50).map { fileIndex ->
                        StorageUsageNode(
                            name = "file$fileIndex.bin",
                            path = "/storage/emulated/0/folder$folderIndex/file$fileIndex.bin",
                            sizeBytes = 1,
                            kind = StorageUsageNodeKind.File,
                            childCount = 0
                        )
                    }
                )
            }
        )

        val count = boundedStorageUsageSunburstSegmentCount(root)
        org.junit.Assert.assertTrue("Segment count should be bounded by MAX_SUNBURST_SEGMENTS (240)", count in 1..240)
    }


    @Test
    fun `ring size limits calculation produces geometric thresholds`() {
        val limits = calculateRingSizeLimits(totalSize = 100_000_000L, ringWidth = 20f, visibleDepth = 5)
        assertEquals(6, limits.size)
        assert(limits[1] > limits[2])
    }

    @Test
    fun `volume overview leaves free capacity empty and splits only used capacity`() {
        val root = StorageUsageNode(
            name = "Internal",
            path = "/storage/emulated/0",
            sizeBytes = 25L,
            kind = StorageUsageNodeKind.Folder,
            childCount = 2,
            children = listOf(
                StorageUsageNode("DCIM", "/storage/emulated/0/DCIM", 15L, StorageUsageNodeKind.Folder, 0),
                StorageUsageNode("Download", "/storage/emulated/0/Download", 10L, StorageUsageNodeKind.Folder, 0)
            )
        )
        val segments = buildSegments(
            root = root,
            colors = listOf(androidx.compose.ui.graphics.Color.Red),
            centerRadius = 20f,
            ringWidth = 20f,
            maxDepth = 5,
            maxSegments = 100,
            maxChildrenPerNode = 20,
            volumeTotalBytes = 100L,
            volumeFreeBytes = 50L
        )

        val capacityRing = segments.filter { it.innerRadius == 20f }
        val rootSegment = capacityRing.single { it.node.path == root.path }
        val systemSegment = capacityRing.single { it.node.name == "System & Other" }
        val folderRing = segments.filter { it.innerRadius == 40f }

        assertFalse(segments.any { it.node.name == "Free Space" })
        assertSame(root, rootSegment.node)
        assertEquals(90f, rootSegment.sweepAngle, 0.001f)
        assertEquals(90f, systemSegment.sweepAngle, 0.001f)
        assertEquals(180f, capacityRing.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)
        assertEquals(90f, folderRing.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `focused internal storage children fill the first ring`() {
        val root = storageUsageRoot()
        val segments = buildSegments(
            root = root,
            colors = listOf(androidx.compose.ui.graphics.Color.Red),
            centerRadius = 20f,
            ringWidth = 20f,
            maxDepth = 5,
            maxSegments = 100,
            maxChildrenPerNode = 20,
            isVolumeRoot = false
        )

        val firstRing = segments.filter { it.innerRadius == 20f }
        assertEquals(root.children.map { it.path }, firstRing.map { it.node.path })
        assertEquals(360f, firstRing.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.001f)
    }



    private fun setStorageUsageContent(
        onSelectedNode: (StorageUsageNode) -> Unit = {},
        onDrilledNode: (StorageUsageNode) -> Unit = {},
        onOpenedPath: (String) -> Unit = {},
        onResetToOverview: () -> Unit = {}
    ) {
        val root = storageUsageRoot()
        composeRule.setContent {
            var selectedNode by remember { mutableStateOf<StorageUsageNode?>(root) }
            ArcileTestTheme {
                StorageUsageMap(
                    state = StorageUsageUiState(
                        scanState = StorageUsageScanState.Loaded(root),
                        currentRoot = root,
                        selectedNode = selectedNode,
                        breadcrumbs = listOf(root)
                    ),
                    onSelectNode = {
                        selectedNode = it
                        onSelectedNode(it)
                    },
                    onDrillInto = onDrilledNode,
                    onBreadcrumbClick = {},
                    onResetToOverview = onResetToOverview,
                    onOpenPath = onOpenedPath,
                    onOpenFile = {},
                    onRefresh = {}
                )
            }
        }
    }

    private fun storageUsageRoot(): StorageUsageNode {
        val downloads = StorageUsageNode(
            name = "Downloads",
            path = "/storage/emulated/0/Download",
            sizeBytes = 768,
            kind = StorageUsageNodeKind.Folder,
            childCount = 1,
            children = listOf(
                StorageUsageNode(
                    name = "movie.mp4",
                    path = "/storage/emulated/0/Download/movie.mp4",
                    sizeBytes = 768,
                    kind = StorageUsageNodeKind.File,
                    childCount = 0
                )
            )
        )
        val documents = StorageUsageNode(
            name = "Documents",
            path = "/storage/emulated/0/Documents",
            sizeBytes = 256,
            kind = StorageUsageNodeKind.Folder,
            childCount = 1
        )
        return StorageUsageNode(
            name = "Internal",
            path = "/storage/emulated/0",
            sizeBytes = 1024,
            kind = StorageUsageNodeKind.Folder,
            childCount = 2,
            children = listOf(downloads, documents)
        )
    }
}
