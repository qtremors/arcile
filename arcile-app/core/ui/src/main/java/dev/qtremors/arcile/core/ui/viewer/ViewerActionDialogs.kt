package dev.qtremors.arcile.core.ui.viewer

import androidx.compose.runtime.Composable
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog

@Composable
fun ViewerActionDialogs(
    currentFile: FileModel?,
    state: ViewerActionState,
    onDismissDialog: () -> Unit,
    onRenameConfirm: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onTogglePermanentDelete: () -> Unit = {}
) {
    if (state.showRenameDialog && currentFile != null) {
        RenameDialog(
            currentName = currentFile.name,
            onDismiss = onDismissDialog,
            onConfirm = { newName ->
                onRenameConfirm(newName)
            }
        )
    }

    if (state.showDeleteDialog && currentFile != null) {
        DeleteConfirmationDialog(
            selectedCount = 1,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = !state.isPermanentDeleteOnly,
            onConfirm = onDeleteConfirm,
            onDismiss = onDismissDialog,
            onTogglePermanentDelete = onTogglePermanentDelete
        )
    }

    if (state.showPropertiesDialog) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = onDismissDialog
        )
    }
}
