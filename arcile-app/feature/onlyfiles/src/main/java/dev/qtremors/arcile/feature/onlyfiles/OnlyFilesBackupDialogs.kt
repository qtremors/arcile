package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

@Composable
internal fun OnlyFilesBackupDialogs(state: OnlyFilesUiState, viewModel: OnlyFilesViewModel) {
    state.backupRequest?.let {
        VaultBackupCredentialsDialog(it, viewModel::dismissBackupRequest, viewModel::submitBackup)
    }
    state.backupInProgress?.let { mode ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(stringResource(if (mode == VaultBackupMode.CREATE) {
                    R.string.onlyfiles_creating_backup
                } else R.string.onlyfiles_restoring_backup))
            },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = viewModel::cancelBackup) { Text(stringResource(R.string.onlyfiles_cancel)) }
            }
        )
    }
}

@Composable
private fun VaultBackupCredentialsDialog(
    request: VaultBackupRequest,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit
) {
    val creating = request.mode == VaultBackupMode.CREATE
    var label by remember(request) { mutableStateOf("") }
    var password by remember(request) { mutableStateOf("") }
    var confirmation by remember(request) { mutableStateOf("") }
    var reveal by remember(request) { mutableStateOf(false) }
    var weakConfirmed by remember(request, password) { mutableStateOf(false) }
    val weak = creating && rememberPasswordWeak(password)
    val action = stringResource(if (creating) R.string.onlyfiles_create_backup else R.string.onlyfiles_restore_backup)
    val mismatch = creating && confirmation.isNotEmpty() && confirmation != password
    val valid = label.isNotBlank() && password.isNotEmpty() &&
        (!creating || (confirmation == password && (!weak || weakConfirmed)))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (creating) R.string.onlyfiles_backup_description else R.string.onlyfiles_restore_backup_description))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.onlyfiles_name)) },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                PasswordField(password, { password = it }, reveal, { reveal = !reveal })
                if (creating) {
                    PasswordField(
                        confirmation, { confirmation = it }, reveal, { reveal = !reveal },
                        label = R.string.onlyfiles_confirm_password, isError = mismatch, showKeyboardSwitch = false
                    )
                    if (mismatch) Text(stringResource(R.string.onlyfiles_password_mismatch), color = MaterialTheme.colorScheme.error)
                }
                if (weak) ShortPasswordConsent(weakConfirmed) { weakConfirmed = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(label.trim(), password, weakConfirmed) },
                enabled = valid,
                shape = ExpressiveShapes.medium
            ) { Text(action) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ExpressiveShapes.medium) {
                Text(stringResource(R.string.onlyfiles_cancel))
            }
        }
    )
}
