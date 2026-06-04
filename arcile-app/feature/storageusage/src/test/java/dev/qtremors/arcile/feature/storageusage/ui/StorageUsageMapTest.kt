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
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.feature.storageusage.StorageUsageUiState
import dev.qtremors.arcile.testutil.ArcileTestTheme
import org.junit.Assert.assertEquals
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
    fun `custom segment action selects node`() {
        setStorageUsageContent()

        val actions = composeRule
            .onNodeWithContentDescription("Storage usage chart for Internal")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        actions.first { it.label == "Select Downloads" }.action()

        composeRule.onNode(hasStateDescription("Downloads selected, 768.0 B, 1 items")).assertExists()
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

    private fun setStorageUsageContent(
        onSelectedNode: (StorageUsageNode) -> Unit = {}
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
                    onDrillInto = {},
                    onBreadcrumbClick = {},
                    onOpenPath = {},
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
