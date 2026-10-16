package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory

internal enum class VaultFolderPickerMode { CREATE, ATTACH }

internal data class VaultFolderPickerState(
    val mode: VaultFolderPickerMode,
    val current: SaveDestinationDirectory? = null,
    val entries: List<SaveDestinationDirectory> = emptyList(),
    val isLoading: Boolean = false
) {
    val canChoose: Boolean get() = !isLoading && current?.canSave == true
}

@Composable
internal fun VaultFolderPickerDialog(
    state: VaultFolderPickerState,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit
) {
    val files = remember(state.entries) { state.entries.map { FileModel(it.name, it.path, isDirectory = true) } }
    OnlyFilesStoragePicker(
        current = state.current,
        files = files,
        loading = state.isLoading,
        canChoose = state.canChoose,
        confirmLabel = if (state.mode == VaultFolderPickerMode.CREATE) R.string.onlyfiles_use_this_folder
        else R.string.onlyfiles_add_this_vault,
        onOpenDirectory = onOpen,
        onUp = onUp,
        onChoose = onChoose,
        onDismiss = onDismiss
    )
}
