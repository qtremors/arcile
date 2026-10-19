package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.lists.StorageLocationRow
import dev.qtremors.arcile.core.ui.R as UiR

internal enum class OnlyFilesLocalPickerMode {
    IMPORT_FILES, MOVE_FILES_IN, IMPORT_FOLDER, EXPORT, MOVE_OUT, BACKUP_DESTINATION, BACKUP_SOURCE;

    val selectsFiles: Boolean get() = this == IMPORT_FILES || this == MOVE_FILES_IN
}

internal data class OnlyFilesLocalPickerState(
    val mode: OnlyFilesLocalPickerMode,
    val current: SaveDestinationDirectory? = null,
    val entries: List<FileModel> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = false
) {
    val canChoose: Boolean get() = !isLoading && when (mode) {
        OnlyFilesLocalPickerMode.IMPORT_FILES, OnlyFilesLocalPickerMode.MOVE_FILES_IN -> selectedPaths.isNotEmpty()
        OnlyFilesLocalPickerMode.IMPORT_FOLDER, OnlyFilesLocalPickerMode.BACKUP_SOURCE -> current != null
        OnlyFilesLocalPickerMode.EXPORT, OnlyFilesLocalPickerMode.MOVE_OUT,
        OnlyFilesLocalPickerMode.BACKUP_DESTINATION -> current?.canSave == true
    }
}

@Composable
internal fun OnlyFilesLocalPickerDialog(
    state: OnlyFilesLocalPickerState,
    onOpenDirectory: (String) -> Unit,
    onToggleFile: (String) -> Unit,
    onUp: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit
) {
    val label = when (state.mode) {
        OnlyFilesLocalPickerMode.IMPORT_FILES -> R.string.onlyfiles_import_files
        OnlyFilesLocalPickerMode.MOVE_FILES_IN -> R.string.onlyfiles_move_into_vault
        OnlyFilesLocalPickerMode.IMPORT_FOLDER -> R.string.onlyfiles_import_folder
        OnlyFilesLocalPickerMode.EXPORT, OnlyFilesLocalPickerMode.MOVE_OUT -> R.string.onlyfiles_choose_destination
        OnlyFilesLocalPickerMode.BACKUP_DESTINATION -> R.string.onlyfiles_choose_backup_destination
        OnlyFilesLocalPickerMode.BACKUP_SOURCE -> R.string.onlyfiles_choose_backup_source
    }
    OnlyFilesStoragePicker(
        current = state.current,
        files = state.entries,
        loading = state.isLoading,
        canChoose = state.canChoose,
        confirmLabel = label,
        onOpenDirectory = onOpenDirectory,
        onUp = onUp,
        onChoose = onChoose,
        onDismiss = onDismiss,
        selectedPaths = state.selectedPaths.takeIf { state.mode.selectsFiles },
        onToggleFile = onToggleFile
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnlyFilesStoragePicker(
    current: SaveDestinationDirectory?,
    files: List<FileModel>,
    loading: Boolean,
    canChoose: Boolean,
    confirmLabel: Int,
    onOpenDirectory: (String) -> Unit,
    onUp: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
    selectedPaths: Set<String>? = null,
    onToggleFile: (String) -> Unit = {}
) {
    val atRoots = current == null
    AlertDialog(
        onDismissRequest = if (atRoots) onDismiss else onUp,
        properties = DialogProperties(dismissOnClickOutside = atRoots),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!atRoots) IconButton(onClick = onUp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(UiR.string.back))
                }
                Text(
                    current?.name ?: stringResource(R.string.onlyfiles_storage_locations),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current?.let {
                    Text(it.path, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val count = selectedPaths?.size ?: 0
                if (count > 0) Text(pluralStringResource(UiR.plurals.clipboard_item_count, count, count))
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { LoadingIndicator() }
                } else if (files.isEmpty()) {
                    Text(stringResource(if (atRoots) R.string.onlyfiles_no_storage else R.string.onlyfiles_empty_folder))
                } else key(current?.path) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                        items(files, key = FileModel::reference) { file ->
                            StorageLocationRow(
                                name = file.name,
                                isDirectory = file.isDirectory,
                                path = file.reference.takeIf { atRoots },
                                isVolume = atRoots,
                                selected = selectedPaths?.let { file.reference in it }.takeUnless { file.isDirectory },
                                onClick = {
                                    if (file.isDirectory) onOpenDirectory(file.reference)
                                    else onToggleFile(file.reference)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onChoose, enabled = canChoose) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) }
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
