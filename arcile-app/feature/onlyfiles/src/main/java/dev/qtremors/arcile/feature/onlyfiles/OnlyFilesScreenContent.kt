@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.Breadcrumbs
import dev.qtremors.arcile.core.ui.ClipboardToolbar
import dev.qtremors.arcile.core.ui.OperationProgressDetailsSheet
import dev.qtremors.arcile.core.ui.R as UiR
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.vault.domain.VaultTransferAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlyFilesClipboardToolbar(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    modifier: Modifier = Modifier
) {
    val clipboard = state.clipboard.takeIf { state.selectedVaultId != null }
    val progress = state.transferProgress
    if (clipboard == null && progress == null) return

    var showDetails by remember(state.mountGeneration, progress != null) { mutableStateOf(false) }
    val context = LocalContext.current
    val count = progress?.totalTopLevelItems ?: clipboard?.sources?.size ?: 0
    val title = pluralStringResource(
        if (progress == null) UiR.plurals.clipboard_items_ready_to_paste else UiR.plurals.clipboard_item_count,
        count, count
    )
    val totalBytes = progress?.totalBytes?.takeIf { it > 0L }
    val subtitle = when {
        progress == null -> formatBytes(context, clipboard?.displayNodes?.sumOf(VaultNodeMetadata::sizeBytes) ?: 0L)
        totalBytes != null -> formatBytes(context, (totalBytes - progress.bytesTransferred).coerceAtLeast(0L))
        else -> pluralStringResource(
            UiR.plurals.transfer_progress_items, count, progress.completedTopLevelItems, count
        )
    }
    val transferFraction = progress?.let {
        if (totalBytes != null) it.bytesTransferred.toFloat() / totalBytes
        else it.completedTopLevelItems.toFloat() / count.coerceAtLeast(1)
    }?.coerceIn(0f, 1f)
    val isMove = if (progress == null) clipboard?.action == VaultClipboardAction.MOVE
        else progress.action == VaultTransferAction.MOVE
    ClipboardToolbar(
        title = title,
        subtitle = subtitle,
        icon = if (isMove) Icons.Default.ContentCut else Icons.Default.ContentCopy,
        onStatusClick = { showDetails = true },
        onPaste = (viewModel::paste).takeIf {
            progress == null && !state.busy && !state.workspace.switching && state.selectedVaultId != null
        },
        onCancel = if (progress == null) viewModel::clearClipboard else viewModel::cancelTransfer,
        modifier = modifier,
        progress = transferFraction
    )

    if (showDetails && progress != null) {
        val showNames = state.selectedVaultId != null && state.transferMountGeneration == state.mountGeneration
        val queue = state.pasteQueueNames.takeIf { showNames }.orEmpty()
        OperationProgressDetailsSheet(
            totalItems = progress.totalTopLevelItems,
            completedItems = progress.completedTopLevelItems,
            onCancel = viewModel::cancelTransfer,
            onDismissRequest = { showDetails = false },
            bytesProcessed = progress.bytesTransferred.takeIf { progress.startedAtMillis != null },
            totalBytes = totalBytes,
            startTimeMillis = progress.startedAtMillis,
            queueNames = queue,
            currentQueueIndex = progress.completedTopLevelItems.takeIf { it in queue.indices } ?: -1
        )
    } else if (showDetails && clipboard != null && clipboard.displayNodes.isNotEmpty()) {
        val files = remember(clipboard.displayNodes) { clipboard.displayNodes.map(VaultNodeMetadata::toSharedFileModel) }
        ClipboardContentsDialog(
            state = ClipboardState(if (isMove) ClipboardOperation.CUT else ClipboardOperation.COPY, files),
            onRemoveItem = viewModel::removeClipboardItem,
            onDismiss = { showDetails = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnlyFilesMainContent(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    listingScroll: VaultListingScroll,
    contentPadding: PaddingValues,
    onListingWidthChanged: (Int) -> Unit,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    getFileOpenBehaviors: () -> Map<String, FileOpenBehavior>,
    onOpenExternally: (VaultNodeMetadata) -> Unit,
    onDirectoryOpened: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onUnlockRequired: (VaultId) -> Unit,
    onShowCreateVault: () -> Unit
) {
    val context = LocalContext.current
    val hasFloatingToolbar = state.selectedNodeIds.isNotEmpty() ||
        (state.selectedVaultId != null && state.clipboard != null) || state.transferProgress != null ||
        state.archiveProgress != null
    val toolbarPadding = if (hasFloatingToolbar) MaterialTheme.spacing.toolbarBottomGap else 0.dp
    Box(Modifier.fillMaxSize().padding(contentPadding).padding(bottom = toolbarPadding)) {
        if (state.workspace.switching) {
            LoadingIndicator(Modifier.align(Alignment.Center))
        } else if (state.selectedVault == null) {
            VaultList(
                vaults = state.vaults,
                pinnedVaults = state.pinnedVaultIds,
                onPinVault = viewModel::setVaultPinned,
                imports = state.activeImports,
                biometricVaultIds = state.biometricVaultIds,
                onOpen = { if (!viewModel.openVault(it)) onUnlockRequired(it.id) },
                onUnlockBiometric = { vault ->
                    viewModel.prepareBiometricUnlock(vault.id) { challenge ->
                        val activity = context.findActivity()
                            ?: run { challenge.close(); return@prepareBiometricUnlock }
                        showBiometricPrompt(activity, challenge) { result ->
                            if (result.isSuccess) viewModel.biometricCompleted(vault.id)
                        }
                    }
                },
                onCancelImport = viewModel::cancelImport
            )
            if (state.vaults.isEmpty()) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    variant = EmptyStateVariant.Folder,
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.onlyfiles_empty),
                    description = "",
                    action = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onShowCreateVault,
                                modifier = Modifier.bounceClickable(onClick = onShowCreateVault)
                            ) { Text(stringResource(R.string.onlyfiles_create)) }
                            TextButton(
                                onClick = viewModel::beginAttachVault,
                                modifier = Modifier.bounceClickable(onClick = viewModel::beginAttachVault)
                            ) { Text(stringResource(R.string.onlyfiles_add_existing)) }
                        }
                    }
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                val vault = requireNotNull(state.selectedVault)
                val root = state.directoryStack.firstOrNull()
                if (root != null && !state.hasSearch) {
                    Breadcrumbs(
                        rootLabel = vault.name,
                        rootKey = root.id.value,
                        segments = state.directoryStack.drop(1).map { it.name to it.id.value },
                        onSegmentClick = viewModel::navigateToDirectory
                    )
                }
                PullToRefreshBox(
                    isRefreshing = state.isLoadingRows && state.displayedNodes.isNotEmpty(),
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f).onSizeChanged { onListingWidthChanged(it.width) }
                ) {
                    VaultBrowser(
                        state = state,
                        listingScroll = listingScroll,
                        loadFolderTotals = viewModel::folderTotals,
                        onOpen = { node ->
                            val vaultId = state.selectedVaultId
                            if (node.prefersExternalOpen(getFileOpenBehaviors())) {
                                onOpenExternally(node)
                            } else if (node.isViewableVideo() && vaultId != null) {
                                onPlayVideo(
                                    createVaultVideoPlaybackSession(
                                        state.displayedNodes,
                                        vaultId,
                                        node,
                                        state.screenshotProtectionEnabled,
                                        viewModel::openReader
                                    )
                                )
                            } else {
                                if (node.isDirectory) onDirectoryOpened()
                                viewModel.open(node)
                            }
                        },
                        onToggleSelection = viewModel::toggleSelection,
                        onSelectRange = viewModel::selectRange,
                        onLoadMore = viewModel::loadNextPage,
                        onRevealFinished = { found ->
                            viewModel.finishReveal(context.getString(R.string.onlyfiles_reveal_unavailable).takeUnless { found })
                        },
                        onGridSizeFinalized = viewModel::setGridCellSize,
                        onImportFiles = onImportFiles,
                        onImportFolder = onImportFolder
                    )
                }
            }
        }
        if (state.busy && state.transferProgress == null && state.pendingConflict == null && state.archiveProgress == null) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LoadingIndicator()
                            state.batchProgress?.let { progress ->
                                LinearProgressIndicator(
                                    progress = {
                                        progress.completedItems.toFloat() /
                                            progress.totalItems.coerceAtLeast(1).toFloat()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    androidx.compose.ui.res.pluralStringResource(
                                        R.plurals.onlyfiles_batch_progress,
                                        progress.totalItems,
                                        progress.completedItems,
                                        progress.totalItems
                                    ),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                progress.currentName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
