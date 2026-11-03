package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.R as UiR

/** Explains which plaintext an Undo writes or removes before it crosses the vault boundary. */
@Composable
internal fun OnlyFilesUndoConfirmationDialog(
    feedback: OnlyFilesOperationFeedback,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (val action = feedback.undo) {
        is OnlyFilesUndoAction.MovedIn -> R.string.onlyfiles_undo_move_in_message
        is OnlyFilesUndoAction.Plaintext -> when (action.kind) {
            OnlyFilesOperationKind.TRASH -> R.string.onlyfiles_undo_trash_message
            OnlyFilesOperationKind.MOVE_OUT -> R.string.onlyfiles_undo_move_out_message
            else -> R.string.onlyfiles_undo_export_message
        }
        else -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
        title = { Text(stringResource(R.string.onlyfiles_undo_confirm_title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { FilledTonalButton(onClick = onConfirm) { Text(stringResource(UiR.string.undo)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.cancel)) } }
    )
}
