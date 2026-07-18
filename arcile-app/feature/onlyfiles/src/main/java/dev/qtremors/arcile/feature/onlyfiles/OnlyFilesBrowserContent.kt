package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemPresentation
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import java.text.DateFormat

@Composable
internal fun SearchRow(state: OnlyFilesUiState, viewModel: OnlyFilesViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            state.searchQuery,
            viewModel::updateSearch,
            modifier = Modifier.weight(1f),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(stringResource(R.string.onlyfiles_search)) },
            singleLine = true
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Checkbox(state.recursiveSearch, onCheckedChange = { viewModel.toggleRecursiveSearch() })
            Text(stringResource(R.string.onlyfiles_subfolders), style = MaterialTheme.typography.labelSmall)
        }
    }
}

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
    if (models.isEmpty() && !state.isSearching) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(if (state.searchQuery.isBlank()) R.string.onlyfiles_empty_folder else R.string.onlyfiles_no_results))
            if (state.searchQuery.isBlank()) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onImportFiles) { Text(stringResource(R.string.onlyfiles_import_files)) }
                TextButton(onClick = onImportFolder) { Text(stringResource(R.string.onlyfiles_import_folder)) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        val presentation = FileItemPresentation(showThumbnails = false, showDetails = true)
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
        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
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
    onCancelImport: (VaultId) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vaults, key = { it.id.value }) { vault ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(vault) },
                shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (vault.isUnlocked) Icons.Outlined.FolderOpen else Icons.Outlined.Lock, null)
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
                    }
                    imports[vault.id]?.let { import ->
                        Spacer(Modifier.height(12.dp))
                        val progress = import.progress
                        val total = progress?.totalBytes
                        if (total != null && total > 0L) {
                            LinearProgressIndicator({ progress.bytesCopied.toFloat() / total }, Modifier.fillMaxWidth())
                        } else LinearProgressIndicator(Modifier.fillMaxWidth())
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
