package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.qtremors.arcile.core.ui.CollapsingWorkspaceTabs
import dev.qtremors.arcile.core.ui.WorkspaceScrollPosition
import dev.qtremors.arcile.core.ui.WorkspaceTabMenu
import dev.qtremors.arcile.core.ui.WorkspaceTabPresentation
import dev.qtremors.arcile.core.ui.WorkspaceTabsRow
import dev.qtremors.arcile.core.ui.rememberWorkspaceTabsVisible
import dev.qtremors.arcile.core.vault.domain.VAULT_MAX_WORKSPACE_TABS

@Composable
internal fun OnlyFilesWorkspaceTabs(
    state: OnlyFilesUiState,
    listingScroll: VaultListingScroll,
    viewModel: OnlyFilesViewModel
) = key(state.mountGeneration) {
    val vault = state.selectedVault ?: return@key
    if (!state.workspaceTabsEnabled) return@key
    val workspace = state.workspace.capture(state)
    if (workspace.tabs.isEmpty()) return@key
    val canAdd = workspace.tabs.size < VAULT_MAX_WORKSPACE_TABS
    val enabled = !workspace.switching && !workspace.restoringPins && !state.busy && !state.isSelectingRows
    val scroll = remember(
        listingScroll, state.currentDirectory?.id, state.searchScope?.lastOrNull()?.id,
        state.searchQuery, state.searchFilters, state.recursiveSearch
    ) { listingScroll.forState(state) }
    val grid = state.viewOptions.grid
    val visible = rememberWorkspaceTabsVisible(scroll to grid) {
        if (grid) WorkspaceScrollPosition(
            scroll.grid.isScrollInProgress, scroll.grid.firstVisibleItemIndex, scroll.grid.firstVisibleItemScrollOffset
        ) else WorkspaceScrollPosition(
            scroll.list.isScrollInProgress, scroll.list.firstVisibleItemIndex, scroll.list.firstVisibleItemScrollOffset
        )
    }
    val capture = { listingScroll.capture(state) }
    CollapsingWorkspaceTabs(visible) {
        WorkspaceTabsRow(
            tabs = workspace.tabs.map { tab ->
                WorkspaceTabPresentation(
                    tab.id,
                    tab.location.directory.name.ifBlank { vault.name },
                    Icons.Default.Home.takeIf { tab.primary }
                )
            },
            activeTabId = workspace.activeTabId,
            enabled = enabled,
            newTabEnabled = canAdd,
            onNewTab = { capture(); viewModel.addWorkspaceTab() },
            onSelectTab = { id ->
                if (id != workspace.activeTabId) {
                    capture()
                    viewModel.selectWorkspaceTab(id)
                }
            },
            tabMenu = { id, expanded, dismiss ->
                val tab = workspace.tabs.first { it.id == id }
                WorkspaceTabMenu(
                    expanded = expanded,
                    onDismiss = dismiss,
                    canDuplicate = canAdd,
                    onDuplicate = { capture(); viewModel.duplicateWorkspaceTab(id) },
                    pinned = tab.pinned.takeUnless { tab.primary },
                    onSetPinned = { viewModel.pinWorkspaceTab(id, it) },
                    onMoveLeft = { viewModel.moveWorkspaceTab(id, -1) }.takeIf { workspace.moveIndex(id, -1) != null },
                    onMoveRight = { viewModel.moveWorkspaceTab(id, 1) }.takeIf { workspace.moveIndex(id, 1) != null },
                    canCloseOthers = workspace.withoutOtherTabs(id).size < workspace.tabs.size,
                    onCloseOthers = {
                        if (workspace.withoutOtherTabs(id).none { it.id == workspace.activeTabId }) capture()
                        viewModel.closeOtherWorkspaceTabs(id)
                    },
                    onClose = {
                        if (id == workspace.activeTabId) capture()
                        viewModel.closeWorkspaceTab(id)
                    }.takeIf { tab.canClose }
                )
            }
        )
    }
}
