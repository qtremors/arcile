package dev.qtremors.arcile.core.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

enum class ArchiveExtractionChoice { NAMED_FOLDER, SAME_FOLDER, CUSTOM_FOLDER }

/** Browser's destination choices, with slots for protected folder picking and archive credentials. */
@Composable
fun ExtractArchiveDialog(
    archiveName: String,
    defaultDestinationPath: String,
    onDismiss: () -> Unit,
    onConfirm: (ArchiveExtractionChoice, String?) -> Unit,
    privateSession: Boolean = false,
    customDestinationContent: (@Composable () -> Unit)? = null,
    canConfirmTarget: (ArchiveExtractionChoice) -> Boolean = { true },
    extraContent: @Composable (ArchiveExtractionChoice) -> Unit = {}
) {
    var target by if (privateSession) remember(archiveName) { mutableStateOf(ArchiveExtractionChoice.NAMED_FOLDER) }
        else rememberSaveable(archiveName) { mutableStateOf(ArchiveExtractionChoice.NAMED_FOLDER) }
    var customDestination by if (privateSession) remember(defaultDestinationPath) { mutableStateOf(defaultDestinationPath) }
        else rememberSaveable(defaultDestinationPath) { mutableStateOf(defaultDestinationPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.archive_extract_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(archiveName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in ArchiveExtractionChoice.entries) {
                        val label = stringResource(when (option) {
                            ArchiveExtractionChoice.NAMED_FOLDER -> R.string.archive_create_subfolder
                            ArchiveExtractionChoice.SAME_FOLDER -> R.string.archive_extract_here
                            ArchiveExtractionChoice.CUSTOM_FOLDER -> R.string.archive_choose_folder
                        })
                        val selected = target == option
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).bounceClickable { target = option }
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = selected, onClick = { target = option })
                                Text(label, style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                if (target == ArchiveExtractionChoice.CUSTOM_FOLDER) {
                    if (customDestinationContent != null) customDestinationContent()
                    else OutlinedTextField(
                        value = customDestination, onValueChange = { customDestination = it },
                        modifier = Modifier.fillMaxWidth().keyboardInputField(), singleLine = true,
                        label = { Text(stringResource(R.string.archive_destination_folder)) },
                        leadingIcon = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        shape = ExpressiveShapes.medium
                    )
                }
                extraContent(target)
            }
        },
        confirmButton = {
            val enabled = canConfirmTarget(target) && (target != ArchiveExtractionChoice.CUSTOM_FOLDER ||
                customDestinationContent != null || customDestination.isNotBlank())
            val confirm = { onConfirm(target, customDestination.takeIf { target == ArchiveExtractionChoice.CUSTOM_FOLDER }) }
            FilledTonalButton(enabled = enabled, onClick = confirm,
                modifier = Modifier.bounceClickable(enabled = enabled, onClick = confirm), shape = ExpressiveShapes.medium) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.archive_extract_archive))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = ExpressiveShapes.medium, modifier = Modifier.bounceClickable(onClick = onDismiss)) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
