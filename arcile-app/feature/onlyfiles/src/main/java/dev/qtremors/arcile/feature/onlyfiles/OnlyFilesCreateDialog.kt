package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun CreateVaultDialog(
    onDismiss: () -> Unit,
    onCreateAppPrivate: (String, String) -> Unit,
    onChooseFolder: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val mismatch = confirm.isNotEmpty() && password != confirm
    val valid = name.isNotBlank() && password.isNotEmpty() && confirm.isNotEmpty() && password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.onlyfiles_name)) }, singleLine = true)
                PasswordField(password, { password = it }, reveal, { reveal = !reveal })
                OutlinedTextField(
                    confirm,
                    { confirm = it },
                    label = { Text(stringResource(R.string.onlyfiles_confirm_password)) },
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = mismatch,
                    singleLine = true
                )
                if (mismatch) Text(stringResource(R.string.onlyfiles_password_mismatch), color = MaterialTheme.colorScheme.error)
                if (password.isNotEmpty() && password.length < 12) {
                    Text(stringResource(R.string.onlyfiles_short_password), color = MaterialTheme.colorScheme.tertiary)
                }
                Text(stringResource(R.string.onlyfiles_folder_storage_note), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.onlyfiles_uninstall_warning), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { onChooseFolder(name.trim(), password) }, enabled = valid) {
                    Text(stringResource(R.string.onlyfiles_choose_folder))
                }
                TextButton(onClick = { onCreateAppPrivate(name.trim(), password) }, enabled = valid) {
                    Text(stringResource(R.string.onlyfiles_use_app_storage))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}
