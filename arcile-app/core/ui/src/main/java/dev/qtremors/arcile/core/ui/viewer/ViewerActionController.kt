package dev.qtremors.arcile.core.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import kotlinx.coroutines.flow.MutableStateFlow

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ViewerActionEntryPoint {
    fun clipboardRepository(): ClipboardRepository
    fun fileBrowserRepository(): FileBrowserRepository
    fun fileMutationRepository(): FileMutationRepository
    fun trashRepository(): TrashRepository
    fun archivePathResolver(): ArchivePathResolver
    fun bulkFileOperationCoordinator(): BulkFileOperationCoordinator
}

class ViewerActionController internal constructor(
    val currentFile: FileModel?,
    val state: ViewerActionState,
    private val handler: ViewerActionHandler?,
    private val trashRepository: TrashRepository?
) {
    fun onAction(action: ViewerFileAction) {
        currentFile?.let { handler?.onAction(action, it) }
    }

    fun dismissDialogs() = handler?.dismissDialogs()

    fun rename(newName: String) {
        currentFile?.let { handler?.renameFile(it, newName) }
    }

    fun togglePermanentDelete() = handler?.togglePermanentDelete()

    fun confirmDelete() {
        currentFile?.let {
            handler?.deleteFile(
                currentFile = it,
                permanent = state.isPermanentDeleteChecked,
                trashRepository = trashRepository
            )
        }
    }
}

@Composable
fun rememberViewerActionController(
    currentFile: FileModel?,
    sourceScope: ViewerSourceScope,
    onFileRenamed: (oldPath: String, newFile: FileModel) -> Unit = { _, _ -> },
    onFileDeleted: (deletedPath: String) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
): ViewerActionController {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val latestOnRenamed by rememberUpdatedState(onFileRenamed)
    val latestOnDeleted by rememberUpdatedState(onFileDeleted)
    val latestOnFeedback by rememberUpdatedState(onFeedback)
    val dependencies = remember(applicationContext) {
        runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                ViewerActionEntryPoint::class.java
            )
        }.getOrNull()
    }
    val actionState = remember(currentFile?.reference, sourceScope) {
        MutableStateFlow(ViewerActionState())
    }
    val handler = remember(currentFile?.reference, sourceScope, dependencies) {
        dependencies?.let { entryPoint ->
            ViewerActionHandler(
                context = applicationContext,
                scope = coroutineScope,
                clipboardRepository = entryPoint.clipboardRepository(),
                fileBrowserRepository = entryPoint.fileBrowserRepository(),
                fileMutationRepository = entryPoint.fileMutationRepository(),
                archivePathResolver = entryPoint.archivePathResolver(),
                operationCoordinator = entryPoint.bulkFileOperationCoordinator(),
                sourceScope = sourceScope,
                state = actionState,
                onFileRenamed = { oldPath, newFile -> latestOnRenamed(oldPath, newFile) },
                onFileDeleted = { deletedPath -> latestOnDeleted(deletedPath) },
                onFeedback = { event -> latestOnFeedback(event) }
            )
        }
    }
    LaunchedEffect(handler, currentFile?.nodeRef?.capabilities) {
        currentFile?.let { handler?.updateAllowedActions(it.nodeRef.capabilities) }
    }
    val state by actionState.collectAsStateWithLifecycle()
    return ViewerActionController(
        currentFile = currentFile,
        state = state,
        handler = handler,
        trashRepository = dependencies?.trashRepository()
    )
}

@Composable
fun ViewerActionHost(controller: ViewerActionController) {
    ViewerActionDialogs(
        currentFile = controller.currentFile,
        state = controller.state,
        onDismissDialog = controller::dismissDialogs,
        onRenameConfirm = controller::rename,
        onDeleteConfirm = controller::confirmDelete,
        onTogglePermanentDelete = controller::togglePermanentDelete
    )
}
