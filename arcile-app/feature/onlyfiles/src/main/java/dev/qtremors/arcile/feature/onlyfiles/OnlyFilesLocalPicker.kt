package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/**
 * Full-screen storage picker shared by imports, exports, backups and the vault folder picker.
 * Moving between folders keeps the current list on screen with a thin progress bar until the
 * next folder is ready, so the picker never flashes or changes size.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    Dialog(
        onDismissRequest = if (atRoots) onDismiss else onUp,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = if (atRoots) onDismiss else onUp) {
                            Icon(
                                if (atRoots) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(if (atRoots) UiR.string.cancel else UiR.string.back)
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                current?.name ?: stringResource(R.string.onlyfiles_storage_locations),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            current?.let {
                                Text(
                                    it.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.StartEllipsis
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0)
                )
                // Space is reserved so the list below never moves when loading starts or stops.
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (loading && files.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        files.isEmpty() && loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                        files.isEmpty() -> Text(
                            stringResource(if (atRoots) R.string.onlyfiles_no_storage else R.string.onlyfiles_empty_folder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                        else -> key(current?.path) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                // Room for the floating actions so the last row can scroll clear of them.
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp)
                            ) {
                                items(files, key = FileModel::reference) { file ->
                                    StorageLocationRow(
                                        name = file.name,
                                        isDirectory = file.isDirectory,
                                        path = file.reference.takeIf { atRoots },
                                        isVolume = atRoots,
                                        selected = selectedPaths?.let { file.reference in it }.takeUnless { file.isDirectory },
                                        onClick = {
                                            if (loading) return@StorageLocationRow
                                            if (file.isDirectory) onOpenDirectory(file.reference)
                                            else onToggleFile(file.reference)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    PickerActions(
                        selectedCount = selectedPaths?.size ?: 0,
                        canChoose = canChoose,
                        confirmLabel = confirmLabel,
                        onChoose = onChoose,
                        onDismiss = onDismiss,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

/** Floating cancel and confirm controls in the same pill style as Arcile's selection toolbars. */
@Composable
private fun PickerActions(
    selectedCount: Int,
    canChoose: Boolean,
    confirmLabel: Int,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onDismiss,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, stringResource(UiR.string.cancel))
            }
        }
        val label = stringResource(confirmLabel)
        Surface(
            onClick = onChoose,
            enabled = canChoose,
            shape = CircleShape,
            color = if (canChoose) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (canChoose) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = if (canChoose) 3.dp else 0.dp,
            modifier = Modifier.height(56.dp).animateContentSize()
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(
                    if (selectedCount > 0) "$label \u00b7 $selectedCount" else label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
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
