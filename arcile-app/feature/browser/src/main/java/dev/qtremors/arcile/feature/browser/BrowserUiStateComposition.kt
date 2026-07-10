package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.feature.browser.delegate.BrowserArchivePasswordPrompt
import dev.qtremors.arcile.feature.browser.delegate.BrowserArchiveWorkflowState
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictState
import dev.qtremors.arcile.feature.browser.delegate.BrowserDeleteWorkflowState
import dev.qtremors.arcile.feature.browser.delegate.BrowserRevealState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal data class BrowserPrimaryControllerState(
    val navigation: BrowserNavigationState,
    val search: BrowserSearchState,
    val selection: BrowserSelectionState,
    val properties: BrowserPropertiesState
)

internal data class BrowserWorkflowControllerState(
    val operation: BrowserOperationState,
    val conflicts: BrowserConflictState,
    val deletion: BrowserDeleteWorkflowState,
    val archive: BrowserArchiveWorkflowState,
    val reveal: BrowserRevealState
)

internal fun CoroutineScope.composeBrowserUiState(
    navigation: StateFlow<BrowserNavigationState>,
    search: StateFlow<BrowserSearchState>,
    selection: StateFlow<BrowserSelectionState>,
    properties: StateFlow<BrowserPropertiesState>,
    operation: StateFlow<BrowserOperationState>,
    conflicts: StateFlow<BrowserConflictState>,
    deletion: StateFlow<BrowserDeleteWorkflowState>,
    archive: StateFlow<BrowserArchiveWorkflowState>,
    reveal: StateFlow<BrowserRevealState>
): StateFlow<BrowserUiState> {
    val primary = combine(navigation, search, selection, properties, ::BrowserPrimaryControllerState)
    val workflows = combine(operation, conflicts, deletion, archive, reveal, ::BrowserWorkflowControllerState)
    val updates = combine(primary, workflows, ::composeBrowserSnapshot)
        .stateIn(
            this,
            SharingStarted.Eagerly,
            composeBrowserSnapshot(
                BrowserPrimaryControllerState(
                    navigation.value,
                    search.value,
                    selection.value,
                    properties.value
                ),
                BrowserWorkflowControllerState(
                    operation.value,
                    conflicts.value,
                    deletion.value,
                    archive.value,
                    reveal.value
                )
            )
        )
    return BrowserSnapshotStateFlow(
        updates = updates,
        snapshot = {
            composeBrowserSnapshot(
                BrowserPrimaryControllerState(
                    navigation.value,
                    search.value,
                    selection.value,
                    properties.value
                ),
                BrowserWorkflowControllerState(
                    operation.value,
                    conflicts.value,
                    deletion.value,
                    archive.value,
                    reveal.value
                )
            )
        }
    )
}

@OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
private class BrowserSnapshotStateFlow(
    private val updates: StateFlow<BrowserUiState>,
    private val snapshot: () -> BrowserUiState
) : StateFlow<BrowserUiState> {
    override val value: BrowserUiState
        get() = snapshot()

    override val replayCache: List<BrowserUiState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<BrowserUiState>): Nothing =
        updates.collect(collector)
}

private fun composeBrowserSnapshot(
    primary: BrowserPrimaryControllerState,
    workflows: BrowserWorkflowControllerState
): BrowserUiState {
    val search = primary.search
    val selection = primary.selection
    val properties = primary.properties
    val operation = workflows.operation
    val conflicts = workflows.conflicts
    val deletion = workflows.deletion
    val archive = workflows.archive
    val reveal = workflows.reveal

    return BrowserUiState(
        location = primary.navigation.location.copy(
            archiveContext = primary.navigation.archiveContext.withArchivePrompt(archive.passwordPrompt)
        ),
        listing = primary.navigation.listing,
        search = search,
        selection = selection,
        dialogs = BrowserDialogState(
            pasteConflicts = conflicts.conflicts,
            showConflictDialog = conflicts.isVisible,
            showTrashConfirmation = deletion.showTrashConfirmation,
            showPermanentDeleteConfirmation = deletion.showPermanentDeleteConfirmation,
            showMixedDeleteExplanation = deletion.showMixedDeleteExplanation,
            deleteDecision = deletion.deleteDecision,
            isPermanentDeleteChecked = deletion.isPermanentDeleteChecked,
            isShredChecked = deletion.isShredChecked,
            isPermanentDeleteToggleEnabled = deletion.isPermanentDeleteToggleEnabled,
            pendingNativeAction = deletion.pendingNativeAction
        ),
        propertiesState = properties,
        operation = operation,
        pendingArchiveExtraction = archive.pendingExtraction,
        pendingRevealFilePath = reveal.path,
        pendingRevealReady = reveal.isReady
    )
}

internal fun BrowserArchiveContext?.withArchivePrompt(
    prompt: BrowserArchivePasswordPrompt?
): BrowserArchiveContext? {
    if (prompt != null) {
        val current = this ?: BrowserArchiveContext(archivePath = prompt.archivePath)
        return current.copy(
            password = prompt.password ?: current.password,
            passwordRequired = prompt.isRequired,
            pendingPasswordAction = prompt.action
        )
    }
    return this?.let {
        if (it.pendingPasswordAction == ArchivePasswordAction.EXTRACT) {
            it.copy(passwordRequired = false)
        } else {
            it
        }
    }
}
