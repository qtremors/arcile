package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.core.storage.domain.userMessage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class UndoDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val coroutineScope: CoroutineScope,
    private val fileMutationRepository: FileMutationRepository,
    private val clipboardRepository: ClipboardRepository,
    private val trashRepository: TrashRepository,
    private val refreshAction: () -> Unit
) {
    fun undoLastTrashMove() {
        val trashIds = state.value.pendingTrashUndoIds
        if (trashIds.isEmpty()) return
        state.update { it.copy(pendingTrashUndoIds = persistentListOf(), pendingUndoAction = null) }
        coroutineScope.launch {
            trashRepository.restoreFromTrash(trashIds).onSuccess {
                refreshAction()
            }.onFailure { error ->
                state.update { it.copy(error = error.toArcileError().userMessage) }
            }
        }
    }

    fun clearPendingTrashUndo() {
        state.update { it.copy(pendingTrashUndoIds = persistentListOf(), pendingUndoAction = null) }
    }

    fun undoLastOperation() {
        when (val undo = state.value.pendingUndoAction) {
            is BrowserUndoAction.Trash -> undoLastTrashMove()
            is BrowserUndoAction.Rename -> undoRename(undo)
            is BrowserUndoAction.Created -> undoCreated(undo)
            is BrowserUndoAction.Moved -> undoMove(undo)
            null -> Unit
        }
    }

    fun clearPendingUndo() {
        state.update { it.copy(pendingTrashUndoIds = persistentListOf(), pendingUndoAction = null) }
    }

    private fun undoRename(undo: BrowserUndoAction.Rename) {
        state.update { it.copy(pendingUndoAction = null) }
        val originalName = File(undo.originalPath).name
        coroutineScope.launch {
            fileMutationRepository.renameFile(undo.renamedPath, originalName).onSuccess {
                refreshAction()
            }.onFailure { error ->
                state.update { it.copy(error = error.toArcileError().userMessage) }
            }
        }
    }

    private fun undoCreated(undo: BrowserUndoAction.Created) {
        state.update { it.copy(pendingUndoAction = null) }
        coroutineScope.launch {
            fileMutationRepository.deletePermanently(listOf(undo.path)).onSuccess {
                refreshAction()
            }.onFailure { error ->
                state.update { it.copy(error = error.toArcileError().userMessage) }
            }
        }
    }

    private fun undoMove(undo: BrowserUndoAction.Moved) {
        state.update { it.copy(pendingUndoAction = null) }
        coroutineScope.launch {
            val groupedByOriginalParent = undo.entries.groupBy { parentStoragePath(it.originalPath) }
            for ((originalParent, entries) in groupedByOriginalParent) {
                if (originalParent.isBlank()) {
                    state.update { it.copy(error = UiText.StringResource(R.string.file_operation_undo_failed)) }
                    return@launch
                }
                clipboardRepository.moveFiles(
                    sourcePaths = entries.map { it.movedPath },
                    destinationPath = originalParent
                ).onFailure { error ->
                    state.update { it.copy(error = error.toArcileError().userMessage) }
                    return@launch
                }
            }
            refreshAction()
        }
    }

    private fun parentStoragePath(path: String): String {
        val normalized = path.replace('\\', '/').trimEnd('/')
        val index = normalized.lastIndexOf('/')
        return if (index > 0) normalized.substring(0, index) else ""
    }
}
