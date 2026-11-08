package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileActionSheet
import dev.qtremors.arcile.core.ui.Breadcrumbs
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.vault.domain.DirectoryId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnlyFilesLocationSheet(
    picker: OnlyFilesLocationPickerState,
    vaultName: String,
    viewModel: OnlyFilesViewModel
) {
    val pickingScope = picker.mode == OnlyFilesLocationMode.SEARCH_SCOPE
    val pickingDestination = picker.mode == OnlyFilesLocationMode.ARCHIVE_DESTINATION
    val importing = picker.mode == OnlyFilesLocationMode.IMPORT_DESTINATION
    ArcileActionSheet(onDismissRequest = viewModel::closeLocationPicker) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(when (picker.mode) {
                    OnlyFilesLocationMode.SEARCH_SCOPE -> R.string.onlyfiles_search_scope
                    OnlyFilesLocationMode.ARCHIVE_DESTINATION -> R.string.onlyfiles_choose_destination
                    OnlyFilesLocationMode.IMPORT_DESTINATION -> R.string.onlyfiles_choose_import_destination
                }),
                style = MaterialTheme.typography.titleLarge
            )
            Breadcrumbs(
                rootLabel = vaultName,
                rootKey = DirectoryId.Root.value,
                segments = picker.stack.drop(1).map { it.name to it.id.value },
                onSegmentClick = { viewModel.navigateLocationPicker(DirectoryId.of(it)) }
            )
            if (picker.loading && picker.folders.isEmpty()) {
                LoadingIndicator()
            } else if (picker.failed && picker.folders.isEmpty()) {
                TextButton(onClick = viewModel::retryLocationPicker) {
                    Text(stringResource(R.string.onlyfiles_retry_folder_list))
                }
            } else if (picker.folders.isEmpty()) {
                Text(stringResource(R.string.onlyfiles_no_subfolders))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(picker.folders, key = { it.ref.nodeId.value }) { folder ->
                    VaultLocationRow(folder.name, onClick = { viewModel.openLocationFolder(folder) })
                }
                if (picker.nextPageToken != null) {
                    item {
                        TextButton(onClick = viewModel::loadMoreLocationFolders, enabled = !picker.loading) {
                            Text(stringResource(R.string.onlyfiles_load_more))
                        }
                    }
                }
            }
            if (pickingScope) Button(onClick = viewModel::applySearchScope, enabled = !picker.loading) {
                Text(stringResource(R.string.onlyfiles_search_here))
            }
            if (pickingDestination) Button(onClick = viewModel::chooseArchiveDestination, enabled = !picker.loading) {
                Text(stringResource(R.string.onlyfiles_extract_here))
            }
            if (importing) OnlyFilesImportDestinationActions(
                enabled = !picker.loading && !picker.failed,
                onCopy = { viewModel.chooseBrowserImportDestination() },
                onMove = { viewModel.chooseBrowserImportDestination(move = true) }
            )
        }
    }
}

@Composable
private fun VaultLocationRow(name: String, location: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = location?.let { label -> { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
        modifier = Modifier.bounceClickable(onClick = onClick)
    )
}

@Composable
internal fun OnlyFilesImportDestinationActions(enabled: Boolean, onCopy: () -> Unit, onMove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCopy, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.onlyfiles_copy_here))
        }
        TextButton(onClick = onMove, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.onlyfiles_move_here))
        }
    }
}

@Composable
internal fun OnlyFilesMoveIntoVaultDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    dev.qtremors.arcile.core.ui.dialogs.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_move_into_vault)) },
        text = { Text(stringResource(R.string.onlyfiles_move_into_vault_confirm)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.onlyfiles_move_into_vault)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}
