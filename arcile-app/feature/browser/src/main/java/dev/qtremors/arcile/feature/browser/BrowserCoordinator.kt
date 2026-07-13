package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveController
import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveWorkflowState
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictController
import dev.qtremors.arcile.feature.browser.delegate.BrowserNavigationController
import dev.qtremors.arcile.feature.browser.delegate.BrowserOperationController
import dev.qtremors.arcile.feature.browser.delegate.SearchController
import dev.qtremors.arcile.feature.browser.delegate.SelectionController

internal class BrowserCoordinator(
    private val navigation: BrowserNavigationController,
    private val search: SearchController,
    private val selection: SelectionController,
    private val archive: BrowserArchiveController,
    private val conflicts: BrowserConflictController,
    private val operation: BrowserOperationController
) {
    fun onLocationChanged() {
        selection.clear()
        archive.dismissWorkflow()
        conflicts.dismiss()
    }

    fun navigateBack(allowVolumeRootFallback: Boolean): Boolean = when {
        search.state.value.browserSearchQuery.isNotBlank() -> {
            search.updateQuery("")
            true
        }
        selection.state.value.selectedFiles.isNotEmpty() -> {
            selection.clear()
            true
        }
        else -> navigation.navigateBack(allowVolumeRootFallback)
    }

    fun selectFolderTab(path: String?) {
        selection.clear()
        navigation.selectFolderTab(path)
    }

    fun refreshAfterMutation() {
        navigation.refresh()
    }

    fun onArchiveWorkflowChanged(state: BrowserArchiveWorkflowState) {
        navigation.applyArchiveWorkflow(state)
    }

    fun onLocalMutationCompleted(status: dev.qtremors.arcile.core.presentation.UiText, undo: BrowserUndoAction) {
        operation.recordLocalMutation(status, undo)
        navigation.refresh()
    }
}
