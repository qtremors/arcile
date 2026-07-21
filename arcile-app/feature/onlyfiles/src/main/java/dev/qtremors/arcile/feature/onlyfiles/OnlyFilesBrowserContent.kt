package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Fingerprint
import java.io.File
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.VaultThumbnailRequest
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemPresentation
import dev.qtremors.arcile.core.ui.lists.FileList
import androidx.compose.foundation.layout.size
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.storageCard
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import java.text.DateFormat

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VaultBrowser(
    state: OnlyFilesUiState,
    onOpen: (VaultNodeMetadata) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectRange: (List<String>) -> Unit,
    onLoadMore: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit
) {
    val models = remember(state.displayedNodes) { state.displayedNodes.map(VaultNodeMetadata::toSharedFileModel) }
    val byOpaquePath = remember(state.displayedNodes, models) {
        models.zip(state.displayedNodes).associate { it.first.absolutePath to it.second }
    }
    val selectedPaths = models.filter { model ->
        byOpaquePath[model.absolutePath]?.ref?.nodeId?.value in state.selectedNodeIds
    }.map(FileModel::absolutePath).toSet()
    if (models.isEmpty()) {
        if (state.isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                variant = if (state.searchQuery.isBlank()) EmptyStateVariant.Folder else EmptyStateVariant.Search,
                icon = if (state.searchQuery.isBlank()) Icons.Outlined.FolderOpen else null,
                title = stringResource(if (state.searchQuery.isBlank()) R.string.onlyfiles_empty_folder else R.string.onlyfiles_no_results),
                description = "",
                action = if (state.searchQuery.isBlank()) {
                    {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onImportFiles,
                                modifier = Modifier.bounceClickable { onImportFiles() }
                            ) { Text(stringResource(R.string.onlyfiles_import_files)) }
                            TextButton(
                                onClick = onImportFolder,
                                modifier = Modifier.bounceClickable { onImportFolder() }
                            ) { Text(stringResource(R.string.onlyfiles_import_folder)) }
                        }
                    }
                } else null
            )
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        val presentation = FileItemPresentation(
            showThumbnails = true,
            showDetails = true,
            thumbnailData = { file, size ->
                byOpaquePath[file.absolutePath]?.takeIf { it.isViewableImage() || it.isViewableVideo() }?.let { node ->
                    VaultThumbnailRequest(
                        node.ref.vaultId.value, node.ref.nodeId.value, node.ref.parentId.value, node.revision, size
                    )
                }
            }
        )
        val open: (String) -> Unit = { path -> byOpaquePath[path]?.let(onOpen) }
        val toggle: (String) -> Unit = { path -> byOpaquePath[path]?.ref?.nodeId?.value?.let(onToggleSelection) }
        val range: (List<String>) -> Unit = { paths -> onSelectRange(paths.mapNotNull { byOpaquePath[it]?.ref?.nodeId?.value }) }
        if (state.layout == OnlyFilesLayout.LIST) {
            FileList(
                files = models, selectedFiles = selectedPaths, onNavigateTo = open, onOpenFile = open,
                onToggleSelection = toggle, onSelectMultiple = range, modifier = Modifier.weight(1f),
                presentation = presentation
            )
        } else {
            FileGrid(
                files = models, selectedFiles = selectedPaths, onNavigateTo = open, onOpenFile = open,
                onToggleSelection = toggle, onSelectMultiple = range, modifier = Modifier.weight(1f),
                presentation = presentation
            )
        }
        val hasMore = if (state.searchQuery.isBlank()) state.nextPageToken != null else state.searchNextPageToken != null
        if (hasMore) TextButton(onClick = onLoadMore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.onlyfiles_load_more))
        }
        if (state.isSearching) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(36.dp))
            }
        }
    }
}

private fun VaultNodeMetadata.toSharedFileModel(): FileModel {
    val capabilities = StorageNodeCapabilities(
        canRead = ref.capabilities.canRead,
        canWrite = ref.capabilities.canRename,
        canDelete = ref.capabilities.canDeletePermanently,
        canRename = ref.capabilities.canRename,
        canCopy = ref.capabilities.canCopy,
        canMove = ref.capabilities.canMove,
        canExport = ref.capabilities.canExport,
        canShare = ref.capabilities.canShare,
        canOpenWith = ref.capabilities.canOpenWith
    )
    val storageRef = StorageNodeRef.vault(ref.vaultId.value, ref.nodeId.value, capabilities)
    return FileModel(
        name, storageRef.displayPath.absolutePath, sizeBytes, modifiedAtMillis, isDirectory, extension,
        name.startsWith('.'), mimeType, nodeRef = storageRef
    )
}

@Composable
internal fun VaultList(
    vaults: List<VaultSummary>,
    imports: Map<VaultId, VaultImportState>,
    onOpen: (VaultSummary) -> Unit,
    onUnlockBiometric: (VaultSummary) -> Unit,
    onCancelImport: (VaultId) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vaults, key = { it.id.value }) { vault ->
            Surface(
                modifier = Modifier.fillMaxWidth().bounceClickable { onOpen(vault) },
                shape = MaterialTheme.shapes.storageCard,
                color = if (vault.isUnlocked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = if (vault.isUnlocked) 1.dp else 0.dp
            ) {
                val context = LocalContext.current
                val biometricEnrolled = remember(vault.id) {
                    val root = File(context.noBackupFilesDir, "onlyfiles-biometric")
                    File(root, "${vault.id.value}.bio").exists()
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (vault.isUnlocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                if (vault.isUnlocked) Icons.Outlined.FolderOpen else Icons.Outlined.Lock,
                                null,
                                Modifier.padding(12.dp),
                                tint = if (vault.isUnlocked) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(vault.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (vault.isAvailable) {
                                    stringResource(
                                        if (vault.locationKind == VaultLocationKind.APP_PRIVATE) R.string.onlyfiles_app_storage
                                        else R.string.onlyfiles_user_folder
                                    ) + " · " + DateFormat.getDateInstance().format(vault.createdAtMillis)
                                } else stringResource(R.string.onlyfiles_folder_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (biometricEnrolled && !vault.isUnlocked) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .bounceClickable { onUnlockBiometric(vault) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Enabled",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    imports[vault.id]?.let { import ->
                        Spacer(Modifier.height(12.dp))
                        val progress = import.progress
                        val total = progress?.totalBytes
                        if (total != null && total > 0L) {
                            LinearProgressIndicator({ progress.bytesCopied.toFloat() / total }, Modifier.fillMaxWidth().clip(CircleShape))
                        } else LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.onlyfiles_importing, progress?.completedItems ?: 0, progress?.totalItems ?: 0),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { onCancelImport(vault.id) }, enabled = !import.isCancelling) {
                                Text(stringResource(R.string.onlyfiles_cancel_import))
                            }
                        }
                    }
                }
            }
        }
    }
}
