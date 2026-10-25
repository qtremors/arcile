package dev.qtremors.arcile.core.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.ui.dialogs.FileNameInput
import dev.qtremors.arcile.core.ui.dialogs.validateFileName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateArchiveDialog(
    defaultName: String,
    selectedCount: Int,
    destinationPath: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, ArchiveFormat, ArchiveCompressionLevel, String?) -> Unit,
    formats: List<ArchiveFormat> = ArchiveFormat.creatableFormats(),
    privateSession: Boolean = false
) {
    require(formats.isNotEmpty() && formats.all { it.canCreate })
    var archiveName by if (privateSession) remember(defaultName) { mutableStateOf(defaultName) }
        else rememberSaveable(defaultName) { mutableStateOf(defaultName) }
    var format by if (privateSession) remember(formats) { mutableStateOf(formats.first()) }
        else rememberSaveable(formats) { mutableStateOf(formats.first()) }
    var compressionLevel by if (privateSession) remember { mutableStateOf(ArchiveCompressionLevel.STORE) }
        else rememberSaveable { mutableStateOf(ArchiveCompressionLevel.STORE) }
    var usePassword by if (privateSession) remember { mutableStateOf(false) }
        else rememberSaveable { mutableStateOf(false) }
    var password by if (privateSession) remember { mutableStateOf("") }
        else rememberSaveable { mutableStateOf("") }
    var confirmPassword by if (privateSession) remember { mutableStateOf("") }
        else rememberSaveable { mutableStateOf("") }
    var passwordVisible by if (privateSession) remember { mutableStateOf(false) }
        else rememberSaveable { mutableStateOf(false) }

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
                    text = androidx.compose.ui.res.pluralStringResource(R.plurals.archive_create_summary, selectedCount, selectedCount),
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ArchiveDropdown(
                        label = stringResource(R.string.archive_type),
                        selectedOption = format.displayName,
                        options = formats.map { it.displayName },
                        onOptionSelected = { selected ->
                            format = formats.first { it.displayName == selected }
                            if (!format.supportsPassword) usePassword = false
                        },
                        leadingIcon = Icons.Default.FolderZip,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ArchiveDropdown(
                        label = stringResource(R.string.archive_compression),
                        selectedOption = compressionLevel.displayName,
                        options = ArchiveCompressionLevel.entries.map { it.displayName },
                        onOptionSelected = { selected ->
                            compressionLevel = ArchiveCompressionLevel.entries.first { it.displayName == selected }
                        },
                        leadingIcon = Icons.Default.Tune,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (format.supportsPassword) {
                    val onTogglePasswordClick = { usePassword = !usePassword }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClickable(onClick = onTogglePasswordClick)
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
            val onConfirmClick = {
                onConfirm(sanitizedArchiveBase, format, compressionLevel, effectivePassword)
            }
            FilledTonalButton(
                enabled = canCreate,
                onClick = onConfirmClick,
                modifier = Modifier.bounceClickable(enabled = canCreate, onClick = onConfirmClick),
                shape = ExpressiveShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.archive_create_action))
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
