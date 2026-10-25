package dev.qtremors.arcile.feature.browser.ui

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.FilledTonalButton
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.core.ui.keyboardInputField

@Composable
internal fun CreateArchiveDialog(
    defaultName: String, selectedCount: Int, destinationPath: String, existingNames: Set<String>,
    onDismiss: () -> Unit, onConfirm: (String, ArchiveFormat, ArchiveCompressionLevel, String?) -> Unit
) = dev.qtremors.arcile.core.ui.archive.CreateArchiveDialog(
    defaultName, selectedCount, destinationPath, existingNames, onDismiss, onConfirm
)

@Composable
private fun ArchiveFormatChoice(
    option: ArchiveFormat,
    selected: ArchiveFormat,
    onSelect: (ArchiveFormat) -> Unit
) {
    val onChoiceClick = { onSelect(option) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .bounceClickable(onClick = onChoiceClick)
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ArchiveExtractionTarget.entries.forEach { option ->
                        val label = when (option) {
                            ArchiveExtractionTarget.NAMED_FOLDER -> stringResource(R.string.archive_create_subfolder)
                            ArchiveExtractionTarget.SAME_FOLDER -> stringResource(R.string.archive_extract_here)
                            ArchiveExtractionTarget.CUSTOM_FOLDER -> stringResource(R.string.archive_choose_folder)
                        }
                        val isSelected = target == option
                        val onSelectTarget = { target = option }
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .bounceClickable(onClick = onSelectTarget)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { target = option }
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
                if (target == ArchiveExtractionTarget.CUSTOM_FOLDER) {
                    OutlinedTextField(
                        value = customDestination,
                        onValueChange = { customDestination = it },
                        modifier = Modifier.fillMaxWidth().keyboardInputField(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.archive_destination_folder)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = ExpressiveShapes.medium
                    )
                }
            }
        },
        confirmButton = {
            val onConfirmClick = { onConfirm(target, customDestination.takeIf { target == ArchiveExtractionTarget.CUSTOM_FOLDER }) }
            val confirmEnabled = target != ArchiveExtractionTarget.CUSTOM_FOLDER || customDestination.isNotBlank()
            FilledTonalButton(
                enabled = confirmEnabled,
                onClick = onConfirmClick,
                modifier = Modifier.bounceClickable(enabled = confirmEnabled, onClick = onConfirmClick),
                shape = ExpressiveShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.archive_extract_archive))
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
