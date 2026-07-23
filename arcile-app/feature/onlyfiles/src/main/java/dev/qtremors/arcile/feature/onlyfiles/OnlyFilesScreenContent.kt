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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.video.VideoPlaybackSession
import dev.qtremors.arcile.core.vault.domain.VaultSummary

@Composable
internal fun OnlyFilesBottomBar(state: OnlyFilesUiState, viewModel: OnlyFilesViewModel) {
    if (state.clipboard == null && state.transferProgress == null) return
    Column {
        state.clipboard?.let { clipboard ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.onlyfiles_clipboard_count,
                            clipboard.sources.size,
                            clipboard.sources.size
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = viewModel::clearClipboard,
                        modifier = Modifier.bounceClickable { viewModel.clearClipboard() }
                    ) { Text(stringResource(R.string.onlyfiles_clear)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::paste,
                        enabled = !state.busy,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.bounceClickable(enabled = !state.busy) { viewModel.paste() }
                    ) { Text(stringResource(R.string.onlyfiles_paste)) }
                }
            }
        }
        state.transferProgress?.let { progress ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = {
                            progress.completedTopLevelItems.toFloat() /
                                progress.totalTopLevelItems.coerceAtLeast(1)
                        },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            progress.currentName ?: stringResource(R.string.onlyfiles_processing),
                            Modifier.weight(1f),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = viewModel::cancelTransfer,
                            modifier = Modifier.bounceClickable { viewModel.cancelTransfer() }
                        ) { Text(stringResource(R.string.onlyfiles_cancel)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OnlyFilesMainContent(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    contentPadding: PaddingValues,
    onPlayVideo: (VideoPlaybackSession) -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onUnlockRequired: (VaultSummary) -> Unit,
    onShowCreateVault: () -> Unit
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        if (state.selectedVault == null) {
            VaultList(
                vaults = state.vaults,
                imports = state.activeImports,
                biometricVaultIds = state.biometricVaultIds,
                onOpen = { if (!viewModel.openVault(it)) onUnlockRequired(it) },
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
            VaultBrowser(
                state = state,
                onOpen = { node ->
                    val vaultId = state.selectedVaultId
                    if (node.isViewableVideo() && vaultId != null) {
                        onPlayVideo(
                            createVaultVideoPlaybackSession(
                                state.displayedNodes,
                                vaultId,
                                node,
                                viewModel::openReader
                            )
                        )
                    } else {
                        viewModel.open(node)
                    }
                },
                onToggleSelection = viewModel::toggleSelection,
                onSelectRange = viewModel::selectRange,
                onLoadMore = viewModel::loadNextPage,
                onImportFiles = onImportFiles,
                onImportFolder = onImportFolder
            )
        }
        if (state.busy && state.transferProgress == null && state.pendingConflict == null) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}
