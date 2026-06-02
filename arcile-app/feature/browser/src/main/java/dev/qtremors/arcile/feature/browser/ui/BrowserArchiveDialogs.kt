package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.shared.ui.dialogs.FileNameInput
import dev.qtremors.arcile.shared.ui.dialogs.validateFileName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateArchiveDialog(
    defaultName: String,
    selectedCount: Int,
    destinationPath: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, ArchiveFormat, ArchiveCompressionLevel, String?) -> Unit
) {
    var archiveName by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
    var format by rememberSaveable { mutableStateOf(ArchiveFormat.ZIP) }
    var compressionLevel by rememberSaveable { mutableStateOf(ArchiveCompressionLevel.STORE) }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val archiveBase = remember(archiveName) {
        archiveBaseName(archiveName)
    }
    val outputValidationName = remember(archiveBase, format) {
        "${archiveBase.trim()}.${format.extension}"
    }
    val validationValue = remember(archiveBase, outputValidationName) {
        archiveBase.takeIf { it.trim().isBlank() } ?: outputValidationName
    }
    val nameValidation = remember(validationValue, existingNames) {
        validateFileName(validationValue, existingNames)
    }
    val passwordError = usePassword && password != confirmPassword
    val outputFileName = nameValidation.sanitizedName
    val sanitizedArchiveBase = remember(outputFileName, format) {
        outputFileName.substringBeforeLast(".${format.extension}", outputFileName)
    }
    val destinationPreview = remember(destinationPath, outputFileName) {
        destinationPath.trimEnd('/', '\\') + "/" + outputFileName
    }
    val effectivePassword = password.takeIf { usePassword && format.supportsPassword && it.isNotEmpty() }
    val canCreate = nameValidation.isValid && (!usePassword || !format.supportsPassword || (password.isNotEmpty() && !passwordError))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_create_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.archive_create_summary, selectedCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                FileNameInput(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = stringResource(R.string.archive_name),
                    existingNames = existingNames,
                    validationValue = validationValue,
                    onDone = {
                        if (canCreate) {
                            onConfirm(sanitizedArchiveBase, format, compressionLevel, effectivePassword)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArchiveDropdown(
                        label = "Type",
                        selectedOption = format.displayName,
                        options = ArchiveFormat.creatableFormats().map { it.displayName },
                        onOptionSelected = { selected ->
                            format = ArchiveFormat.creatableFormats().first { it.displayName == selected }
                            if (!format.supportsPassword) usePassword = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ArchiveDropdown(
                        label = "Compression",
                        selectedOption = compressionLevel.displayName,
                        options = ArchiveCompressionLevel.entries.map { it.displayName },
                        onOptionSelected = { selected ->
                            compressionLevel = ArchiveCompressionLevel.entries.first { it.displayName == selected }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (format.supportsPassword) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { usePassword = !usePassword }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = usePassword,
                            onCheckedChange = { usePassword = it }
                        )
                        Text(
                            text = stringResource(R.string.archive_password_protect),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                if (usePassword && format.supportsPassword) {
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
                        passwordVisible = passwordVisible,
                        onToggleVisibility = null
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
                    onConfirm(sanitizedArchiveBase, format, compressionLevel, effectivePassword)
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
    defaultDestinationPath: String,
    onDismiss: () -> Unit,
    onConfirm: (ArchiveExtractionTarget, String?) -> Unit
) {
    var target by rememberSaveable { mutableStateOf(ArchiveExtractionTarget.NAMED_FOLDER) }
    var customDestination by rememberSaveable(defaultDestinationPath) { mutableStateOf(defaultDestinationPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_extract_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = archiveName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                ArchiveExtractionTarget.entries.forEach { option ->
                    val label = when (option) {
                        ArchiveExtractionTarget.NAMED_FOLDER -> stringResource(R.string.archive_create_subfolder)
                        ArchiveExtractionTarget.SAME_FOLDER -> stringResource(R.string.archive_extract_here)
                        ArchiveExtractionTarget.CUSTOM_FOLDER -> "Choose folder"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = target == option, onClick = { target = option })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (target == ArchiveExtractionTarget.CUSTOM_FOLDER) {
                    OutlinedTextField(
                        value = customDestination,
                        onValueChange = { customDestination = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Destination folder") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != ArchiveExtractionTarget.CUSTOM_FOLDER || customDestination.isNotBlank(),
                onClick = { onConfirm(target, customDestination.takeIf { target == ArchiveExtractionTarget.CUSTOM_FOLDER }) }
            ) {
                Text(stringResource(R.string.archive_extract_archive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun archiveBaseName(name: String): String {
    val trimmed = name.trim()
    val supportedSuffix = ArchiveFormat.entries
        .map { ".${it.extension}" }
        .sortedByDescending { it.length }
        .firstOrNull { trimmed.endsWith(it, ignoreCase = true) }
    return if (supportedSuffix != null) {
        trimmed.dropLast(supportedSuffix.length)
    } else {
        trimmed
    }
}

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
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onConfirm(password) }
            ) {
                Text(stringResource(R.string.open))
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
private fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: (() -> Unit)?,
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
        trailingIcon = if (onToggleVisibility != null) {
            {
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
        } else {
            null
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveDropdown(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
