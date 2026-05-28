package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.feature.browser.R
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.shared.ui.dialogs.FileNameInput
import dev.qtremors.arcile.shared.ui.dialogs.validateFileName

@Composable
internal fun CreateArchiveDialog(
    defaultName: String,
    selectedCount: Int,
    destinationPath: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, ArchiveFormat, String?) -> Unit
) {
    var archiveName by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
    var format by rememberSaveable { mutableStateOf(ArchiveFormat.ZIP) }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val archiveBase = remember(archiveName, format) {
        archiveBaseName(archiveName)
    }
    val existingArchiveBaseNames = remember(existingNames, format) {
        existingNames.map { archiveBaseName(it) }.toSet()
    }
    val nameValidation = remember(archiveBase, existingArchiveBaseNames) {
        validateFileName(archiveBase, existingArchiveBaseNames)
    }
    val passwordError = usePassword && password != confirmPassword
    val outputFileName = "${nameValidation.sanitizedName}.${format.extension}"
    val destinationPreview = remember(destinationPath, outputFileName) {
        destinationPath.trimEnd('/', '\\') + "/" + outputFileName
    }
    val canCreate = nameValidation.isValid && (!usePassword || (password.isNotEmpty() && !passwordError))

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.archive_create_summary, selectedCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                FileNameInput(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = stringResource(R.string.archive_name),
                    existingNames = existingArchiveBaseNames,
                    validationValue = archiveBase,
                    onDone = {
                        if (canCreate) {
                            onConfirm(nameValidation.sanitizedName, format, password.takeIf { usePassword && it.isNotEmpty() })
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.archive_filename_preview, outputFileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column {
                    ArchiveFormatChoice(ArchiveFormat.ZIP, format, onSelect = { format = it })
                    ArchiveFormatChoice(ArchiveFormat.SEVEN_Z, format, onSelect = { format = it })
                }
                InputChip(
                    selected = usePassword,
                    onClick = { usePassword = !usePassword },
                    label = { Text(stringResource(R.string.archive_password_protect)) }
                )
                if (usePassword) {
                    PasswordInputField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.archive_password),
                        passwordVisible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible }
                    )
                    PasswordInputField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = stringResource(R.string.archive_confirm_password),
                        isError = passwordError,
                        supportingText = if (passwordError) {
                            stringResource(R.string.archive_password_mismatch)
                        } else {
                            null
                        },
                        passwordVisible = confirmPasswordVisible,
                        onToggleVisibility = { confirmPasswordVisible = !confirmPasswordVisible }
                    )
                }
                Text(
                    text = stringResource(R.string.archive_destination, destinationPreview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    onConfirm(nameValidation.sanitizedName, format, password.takeIf { usePassword && it.isNotEmpty() })
                }
            ) {
                Text(stringResource(R.string.archive_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ArchiveFormatChoice(
    option: ArchiveFormat,
    selected: ArchiveFormat,
    onSelect: (ArchiveFormat) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onSelect(option) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = option == selected, onClick = { onSelect(option) })
        Text(option.displayName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ExtractArchiveDialog(
    archiveName: String,
    onDismiss: () -> Unit,
    onExtractHere: (String?) -> Unit,
    onExtractToFolder: (String?) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val archivePassword = password.takeIf { usePassword && it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_extract_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = archiveName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                InputChip(
                    selected = usePassword,
                    onClick = { usePassword = !usePassword },
                    label = { Text(stringResource(R.string.archive_has_password)) }
                )
                if (usePassword) {
                    PasswordInputField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.archive_password),
                        supportingText = stringResource(R.string.archive_password_hint),
                        passwordVisible = passwordVisible,
                        onToggleVisibility = { passwordVisible = !passwordVisible }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExtractToFolder(archivePassword) }) {
                Text(stringResource(R.string.archive_extract_to_folder))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = { onExtractHere(archivePassword) }) {
                    Text(stringResource(R.string.archive_extract_here))
                }
            }
        }
    )
}

private fun archiveBaseName(name: String): String {
    val trimmed = name.trim()
    val supportedSuffix = ArchiveFormat.entries
        .map { ".${it.extension}" }
        .firstOrNull { trimmed.endsWith(it, ignoreCase = true) }
    return if (supportedSuffix != null) {
        trimmed.dropLast(supportedSuffix.length)
    } else {
        trimmed
    }
}

@Composable
private fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { text ->
            { Text(text) }
        },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
            val contentDescription = stringResource(
                if (passwordVisible) {
                    R.string.archive_password_hide
                } else {
                    R.string.archive_password_show
                }
            )
            IconButton(onClick = onToggleVisibility) {
                Icon(icon, contentDescription = contentDescription)
            }
        }
    )
}
