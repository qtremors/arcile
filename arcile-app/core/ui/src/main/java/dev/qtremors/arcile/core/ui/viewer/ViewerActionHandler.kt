package dev.qtremors.arcile.core.ui.viewer

import android.content.Context
import android.content.Intent
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.SelectionPropertiesLoader
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.ArchiveCollisionStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.ui.showArcileToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ViewerActionHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clipboardRepository: ClipboardRepository,
    fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val archivePathResolver: ArchivePathResolver? = null,
    private val operationCoordinator: BulkFileOperationCoordinator? = null,
    private val sourceScope: ViewerSourceScope,
    val state: MutableStateFlow<ViewerActionState>,
    private val onFileRenamed: ((oldPath: String, newFile: FileModel) -> Unit)? = null,
    private val onFileDeleted: ((deletedPath: String) -> Unit)? = null,
    private val onFeedback: ((ArcileFeedbackEvent) -> Unit)? = null
) {
    private val clipboardController = ClipboardController(clipboardRepository)
    private val propertiesLoader = SelectionPropertiesLoader(
        scope = scope,
        repository = fileBrowserRepository,
        onStateChange = { properties ->
            state.update {
                it.copy(
                    showPropertiesDialog = properties.isVisible,
                    isPropertiesLoading = properties.isLoading,
                    properties = properties.properties
                )
            }
        },
        onError = {
            state.update { s ->
                s.copy(
                    error = it.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.error_load_properties_failed)
                )
            }
        }
    )

    fun updateAllowedActions(capabilities: StorageNodeCapabilities) {
        val allowed = ViewerActionPolicy.resolveAllowedActions(sourceScope, capabilities)
        state.update { it.copy(allowedActions = allowed) }
    }

    fun onAction(action: ViewerFileAction, currentFile: FileModel) {
        if (action !in state.value.allowedActions) return
        when (action) {
            ViewerFileAction.Rename -> {
                state.update { it.copy(showRenameDialog = true) }
            }
            ViewerFileAction.Copy -> {
                clipboardController.store(ClipboardOperation.COPY, listOf(currentFile))
                onFeedback?.invoke(
                    ArcileFeedbackEvent(
                        message = UiText.StringResource(R.string.copied_to_clipboard)
                    )
                )
            }
            ViewerFileAction.Cut -> {
                clipboardController.store(ClipboardOperation.CUT, listOf(currentFile))
                onFeedback?.invoke(
                    ArcileFeedbackEvent(
                        message = UiText.StringResource(R.string.copied_to_clipboard)
                    )
                )
            }
            ViewerFileAction.Delete -> {
                val isPermanent = !currentFile.nodeRef.capabilities.canTrash || sourceScope == ViewerSourceScope.ManagedTrash
                state.update {
                    it.copy(
                        showDeleteDialog = true,
                        isPermanentDeleteOnly = isPermanent,
                        isPermanentDeleteChecked = isPermanent
                    )
                }
            }
            ViewerFileAction.Share -> {
                shareFile(currentFile)
            }
            ViewerFileAction.OpenWith -> {
                openWithChooser(currentFile)
            }
            ViewerFileAction.CreateArchive -> {
                createZip(currentFile)
            }
            ViewerFileAction.Properties -> {
                propertiesLoader.open(listOf(currentFile.reference))
            }
        }
    }

    fun dismissDialogs() {
        state.update {
            it.copy(
                showRenameDialog = false,
                showDeleteDialog = false,
                showPropertiesDialog = false
            )
        }
    }

    fun togglePermanentDelete() {
        state.update {
            if (it.isPermanentDeleteOnly) it
            else it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked)
        }
    }

    fun renameFile(currentFile: FileModel, newName: String) {
        dismissDialogs()
        scope.launch {
            val result = fileMutationRepository.renameFile(currentFile.reference, newName)
            result.onSuccess { newFile ->
                onFileRenamed?.invoke(currentFile.reference, newFile)
            }.onFailure { error ->
                state.update {
                    it.copy(
                        error = error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_file_operation_failed)
                    )
                }
            }
        }
    }

    fun deleteFile(currentFile: FileModel, permanent: Boolean, trashRepository: dev.qtremors.arcile.core.storage.domain.TrashRepository? = null) {
        dismissDialogs()
        scope.launch {
            val result = if (!permanent && currentFile.nodeRef.capabilities.canTrash && trashRepository != null) {
                trashRepository.moveToTrash(listOf(currentFile.reference))
            } else {
                fileMutationRepository.deletePermanently(listOf(currentFile.reference))
            }
            result.onSuccess {
                onFileDeleted?.invoke(currentFile.reference)
            }.onFailure { error ->
                state.update {
                    it.copy(
                        error = error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_file_operation_failed)
                    )
                }
            }
        }
    }

    private fun shareFile(file: FileModel) {
        scope.launch {
            try {
                val targets = ExternalFileAccessHelper.createShareTargets(
                    context,
                    listOf(
                        ExternalFileAccessHelper.ExternalFileReference(
                            path = file.reference,
                            displayName = file.name,
                            sizeBytes = file.size,
                            mimeType = file.mimeType
                        )
                    )
                )
                if (targets.isNotEmpty()) {
                    val uri = targets.first().uri
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = file.mimeType ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TITLE, file.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share))
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                context.showArcileToast(context.getString(R.string.error_file_operation_failed))
            }
        }
    }

    private fun openWithChooser(file: FileModel) {
        scope.launch {
            try {
                val openIntent = ExternalFileAccessHelper.createOpenIntent(
                    context,
                    ExternalFileAccessHelper.ExternalFileReference(
                        path = file.reference,
                        displayName = file.name,
                        sizeBytes = file.size,
                        mimeType = file.mimeType
                    )
                )
                val chooser = Intent.createChooser(openIntent, context.getString(R.string.action_open_with))
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(chooser)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                context.showArcileToast(context.getString(R.string.cannot_open_file, context.getString(R.string.no_app_found)))
            }
        }
    }

    private fun createZip(file: FileModel) {
        val resolver = archivePathResolver ?: return
        val coordinator = operationCoordinator ?: return
        val parentPath = storageParentPath(file.reference) ?: return
        scope.launch {
            val destination = resolver.resolve(
                ArchivePathRequest(
                    sourcePaths = listOf(file.reference),
                    parentPath = parentPath,
                    format = ArchiveFormat.ZIP,
                    collisionStyle = ArchiveCollisionStyle.UNDERSCORE
                )
            ).getOrElse { error ->
                state.update {
                    it.copy(
                        error = error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_file_operation_failed)
                    )
                }
                return@launch
            }
            coordinator.startOperation(
                type = BulkFileOperationType.CREATE_ARCHIVE,
                sourcePaths = listOf(file.reference),
                destinationPath = destination,
                resolutions = emptyMap(),
                archiveFormat = ArchiveFormat.ZIP,
                archiveCompressionLevel = ArchiveCompressionLevel.STORE
            )
        }
    }
}
