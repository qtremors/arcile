package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.ui.ExpressiveSelectorCard
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes

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
    var locationKind by remember { mutableStateOf(VaultLocationKind.APP_PRIVATE) }
    
    val mismatch = confirm.isNotEmpty() && password != confirm
    val valid = name.isNotBlank() && password.isNotEmpty() && confirm.isNotEmpty() && password == confirm
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.onlyfiles_name)) },
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                PasswordField(password, { password = it }, reveal, { reveal = !reveal })
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.onlyfiles_confirm_password)) },
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = mismatch,
                    singleLine = true,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) Text(stringResource(R.string.onlyfiles_password_mismatch), color = MaterialTheme.colorScheme.error)
                if (password.isNotEmpty() && password.length < 12) {
                    Text(stringResource(R.string.onlyfiles_short_password), color = MaterialTheme.colorScheme.tertiary)
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.onlyfiles_storage_locations),
                    style = MaterialTheme.typography.titleSmall
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveSelectorCard(
                        selected = locationKind == VaultLocationKind.APP_PRIVATE,
                        onClick = { locationKind = VaultLocationKind.APP_PRIVATE },
                        icon = Icons.Default.Lock,
                        title = stringResource(R.string.onlyfiles_app_storage),
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveSelectorCard(
                        selected = locationKind == VaultLocationKind.USER_FOLDER,
                        onClick = { locationKind = VaultLocationKind.USER_FOLDER },
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.onlyfiles_user_folder),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.onlyfiles_folder_storage_note), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.onlyfiles_uninstall_warning), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (locationKind == VaultLocationKind.APP_PRIVATE) {
                        onCreateAppPrivate(name.trim(), password)
                    } else {
                        onChooseFolder(name.trim(), password)
                    }
                },
                enabled = valid,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = valid) {
                    if (locationKind == VaultLocationKind.APP_PRIVATE) {
                        onCreateAppPrivate(name.trim(), password)
                    } else {
                        onChooseFolder(name.trim(), password)
                    }
                }
            ) {
                Text(
                    stringResource(
                        if (locationKind == VaultLocationKind.APP_PRIVATE) R.string.onlyfiles_create_action
                        else R.string.onlyfiles_choose_folder
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.onlyfiles_cancel))
            }
        }
    )
}
