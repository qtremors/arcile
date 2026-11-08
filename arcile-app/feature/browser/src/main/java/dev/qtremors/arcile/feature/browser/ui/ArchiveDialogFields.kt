package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.archive.PasswordInputField
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun ArchivePasswordPromptDialog(
    archiveName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(archiveName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PasswordInputField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.archive_password),
                    supportingText = stringResource(R.string.archive_password_description),
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible }
                )
            }
        },
        confirmButton = {
            val onConfirmClick = { onConfirm(password) }
            FilledTonalButton(
                enabled = password.isNotEmpty(),
                onClick = onConfirmClick,
                modifier = Modifier.bounceClickable(
                    enabled = password.isNotEmpty(),
                    onClick = onConfirmClick
                ),
                shape = ExpressiveShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
