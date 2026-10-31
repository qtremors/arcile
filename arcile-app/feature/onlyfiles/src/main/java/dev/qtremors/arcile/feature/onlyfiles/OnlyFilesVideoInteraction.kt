package dev.qtremors.arcile.feature.onlyfiles

import android.media.MediaDataSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.viewer.ViewerFileAction
import dev.qtremors.arcile.core.ui.viewer.ViewerActionPolicy
import dev.qtremors.arcile.core.ui.viewer.ViewerSourceScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.qtremors.arcile.core.ui.ArcileSnackbarHost
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.showArcileFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.qtremors.arcile.core.ui.metadata.readVideoVisualMetadata
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class OnlyFilesVideoInteraction(
    private val viewModel: OnlyFilesViewModel,
    private val vaultId: VaultId,
    private val mountGeneration: Long
) {
    private var request by mutableStateOf<Pair<ExternalAction, VaultNodeMetadata>?>(null)
    private var mutation by mutableStateOf<Pair<ViewerFileAction, VaultNodeMetadata>?>(null)
    private var wantedSize by mutableStateOf(0)
    private var attemptedPage: String? = null
    private val listing = viewModel.state.value
    private var queue: OnlyFilesVideoQueue? = null

    fun attach(queue: OnlyFilesVideoQueue) {
        this.queue = queue
    }

    fun loadMore() {
        attemptedPage = null
        wantedSize = (queue?.size ?: 0) + 4
    }

    fun action(file: FileModel, action: ViewerFileAction) {
        val current = viewModel.state.value
        val node = queue?.node(file.reference) ?: return
        val allowed = ViewerActionPolicy.resolveAllowedActions(ViewerSourceScope.Vault, node.toSharedFileModel().nodeRef.capabilities)
        if (!isCurrent(current) || current.busy || action !in allowed) return
        when (action) {
            ViewerFileAction.Copy -> viewModel.copy(listOf(node))
            ViewerFileAction.Cut -> viewModel.move(listOf(node))
            ViewerFileAction.Rename, ViewerFileAction.Delete, ViewerFileAction.CreateArchive -> {
                mutation = action to node
                queue?.overlay(true)
            }
            else -> Unit
        }
    }

    fun request(action: ExternalAction, node: VaultNodeMetadata) {
        if (isCurrent(viewModel.state.value)) {
            request = action to node
            queue?.overlay(true)
        }
    }

    private fun isCurrent(state: OnlyFilesUiState): Boolean =
        state.selectedVaultId == vaultId && state.mountGeneration == mountGeneration

    @Composable
    fun Host(onClosed: () -> Unit, content: @Composable () -> Unit) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val activeQueue = queue
        val snapshot = activeQueue?.sourceUpdates?.collectAsStateWithLifecycle()?.value
        if (isCurrent(state) && snapshot?.files?.isEmpty() != true) {
            val feedback = remember { SnackbarHostState() }
            val sameListing = state.currentDirectory?.id == listing.currentDirectory?.id &&
                state.searchQuery == listing.searchQuery && state.searchFilters == listing.searchFilters &&
                state.searchScope == listing.searchScope && state.viewOptions == listing.viewOptions
            LaunchedEffect(state.displayedNodes, sameListing) {
                if (sameListing) activeQueue?.merge(state.displayedNodes)
            }
            LaunchedEffect(wantedSize, state.isLoadingRows, state.nextPageToken, state.searchNextPageToken, activeQueue?.size) {
                if (sameListing && wantedSize > (activeQueue?.size ?: 0) && !state.isLoadingRows && !state.busy) {
                    val next = if (state.hasSearch) state.searchNextPageToken else state.nextPageToken
                    if (next == null) wantedSize = 0
                    else if (next != attemptedPage) {
                        attemptedPage = next
                        viewModel.loadNextPage()
                    }
                }
            }
            val context = LocalContext.current
            LaunchedEffect(state.message, state.operationFeedback?.id) {
                val operation = state.operationFeedback?.takeIf { it.belongsTo(state) && state.message == null }
                if (operation != null) {
                    operation.undoResult?.let { activeQueue?.applyUndo(it) }
                    // Global playback remains in its own route; source actions here do not navigate out of it.
                    val action = operation.event({}, { viewModel.dismissOperationFeedback(operation.id) },
                        onUndo = { viewModel.undoOperationFeedback(operation) })
                    val event = if (operation.undo != null) action else action.copy(actionLabel = null, onAction = null)
                    feedback.showArcileFeedback(context, event)
                } else state.message?.let { message ->
                    feedback.showArcileFeedback(context, ArcileFeedbackEvent(
                        UiText.Dynamic(message), onDismiss = viewModel::clearMessage
                    ))
                }
            }
            Box(Modifier.fillMaxSize()) {
                content()
                state.archiveProgress?.let { progress ->
                    OnlyFilesArchiveToolbar(progress, viewModel::cancelArchiveOperation,
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }
                ArcileSnackbarHost(feedback, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { state.operationFeedback?.severity ?: ArcileFeedbackSeverity.Info }
            }
            request?.let { (action, node) ->
                VaultExternalAccessDialogs(action, listOf(node), viewModel) {
                    request = null
                    activeQueue?.overlay(false)
                }
            }
            mutation?.let { (action, node) ->
                fun dismiss() {
                    mutation = null
                    activeQueue?.overlay(false)
                }
                when (action) {
                    ViewerFileAction.CreateArchive -> VaultCreateZipDialog(listOf(node), state.nodes.mapTo(mutableSetOf(), VaultNodeMetadata::name),
                        onDismiss = ::dismiss, destinationPath = state.directoryStack.joinToString("/") { it.name }) { name, level, message, password ->
                        dismiss()
                        viewModel.createZip(listOf(node), name, level, message, password)
                    }
                    ViewerFileAction.Rename -> RenameDialog(
                        currentName = node.name, onDismiss = ::dismiss, saveInput = false,
                        onConfirm = { name ->
                            dismiss()
                            viewModel.rename(node, name) { updated -> activeQueue?.merge(listOf(updated)) }
                        }
                    )
                    ViewerFileAction.Delete -> VaultDeleteConfirmation(listOf(node), onDismiss = ::dismiss) {
                        dismiss()
                        viewModel.delete(listOf(node)) { removed -> activeQueue?.remove(removed) }
                    }
                    else -> Unit
                }
            }
        } else {
            LaunchedEffect(Unit) {
                request = null
                mutation = null
                activeQueue?.remove(activeQueue.refsByOpaqueId.keys.toSet())
                onClosed()
            }
        }
    }
}

internal suspend fun readVaultVideoMetadata(
    node: VaultNodeMetadata,
    file: FileModel,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>
) = withContext(Dispatchers.IO) {
    val reader = openReader(node.ref).getOrNull() ?: return@withContext null
    reader.use {
        val source = object : MediaDataSource() {
            override fun getSize(): Long = reader.sizeBytes
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                reader.readAt(position, buffer, offset, size)
            override fun close() = Unit
        }
        readVideoVisualMetadata(file.reference, reader.sizeBytes, file.mimeType) { it.setDataSource(source) }
    }
}
