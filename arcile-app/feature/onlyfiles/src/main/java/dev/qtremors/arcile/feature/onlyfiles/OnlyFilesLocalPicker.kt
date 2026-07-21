package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

internal enum class OnlyFilesLocalPickerMode { IMPORT_FILES, IMPORT_FOLDER, EXPORT, MOVE_OUT }

internal data class OnlyFilesLocalPickerState(
    val mode: OnlyFilesLocalPickerMode,
    val current: SaveDestinationDirectory? = null,
    val entries: List<FileModel> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

@Composable
internal fun OnlyFilesLocalPickerDialog(
    state: OnlyFilesLocalPickerState,
    onOpenDirectory: (String) -> Unit,
    onToggleFile: (String) -> Unit,
    onUp: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectingFiles = state.mode == OnlyFilesLocalPickerMode.IMPORT_FILES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.current != null) IconButton(onClick = onUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onlyfiles_back))
                }
                Text(
                    state.current?.name ?: stringResource(R.string.onlyfiles_storage_locations),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.current?.let { Text(it.path, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (state.isLoading) Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator()
                } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    items(state.entries, key = FileModel::absolutePath) { file ->
                        val itemClick: () -> Unit = {
                            if (file.isDirectory) onOpenDirectory(file.absolutePath)
                            else if (selectingFiles) onToggleFile(file.absolutePath)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .bounceClickable { itemClick() }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(if (file.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile, null)
                            Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (selectingFiles && !file.isDirectory) {
                                ExpressiveSwitch(
                                    checked = file.absolutePath in state.selectedPaths,
                                    onCheckedChange = { onToggleFile(file.absolutePath) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val enabled = !state.isLoading && if (selectingFiles) state.selectedPaths.isNotEmpty()
            else state.current?.canSave == true
            Button(
                onClick = onChoose,
                enabled = enabled,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = enabled) { onChoose() }
            ) {
                Text(stringResource(
                    if (state.mode == OnlyFilesLocalPickerMode.IMPORT_FILES ||
                        state.mode == OnlyFilesLocalPickerMode.IMPORT_FOLDER
                    ) R.string.onlyfiles_import_files else R.string.onlyfiles_choose_destination
                ))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { onDismiss() }
            ) {
                Text(stringResource(R.string.onlyfiles_cancel))
            }
        }
    )
}

@Composable
internal fun OnlyFilesPickerDialogs(state: OnlyFilesUiState, viewModel: OnlyFilesViewModel) {
    state.folderPicker?.let { picker ->
        VaultFolderPickerDialog(
            picker,
            viewModel::openVaultFolder,
            viewModel::navigateVaultFolderUp,
            viewModel::chooseVaultFolder,
            viewModel::cancelVaultFolderPicker
        )
    }
    state.localPicker?.let { picker ->
        OnlyFilesLocalPickerDialog(
            picker,
            viewModel::openLocalPickerDirectory,
            viewModel::toggleLocalPickerFile,
            viewModel::navigateLocalPickerUp,
            viewModel::chooseLocalPicker,
            viewModel::cancelLocalPicker
        )
    }
}
