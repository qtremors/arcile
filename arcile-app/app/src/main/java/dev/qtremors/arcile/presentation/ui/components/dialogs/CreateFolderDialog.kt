package dev.qtremors.arcile.presentation.ui.components.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    existingNames: Set<String> = emptySet(),
    destinationPath: String? = null
) {
    var folderName by remember { mutableStateOf("") }
    val validation = remember(folderName, existingNames) {
        validateFileName(folderName, existingNames)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_create_folder)) },
        shape = MaterialTheme.shapes.extraLarge,
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                FileNameInput(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = stringResource(R.string.label_folder_name),
                    existingNames = existingNames,
                    onDone = {
                        if (validation.isValid) {
                            onConfirm(validation.sanitizedName)
                        }
                    }
                )
                if (!destinationPath.isNullOrBlank() && validation.isValid) {
                    Text(
                        text = stringResource(
                            R.string.create_destination_preview,
                            destinationPath.trimEnd('/', '\\') + "/" + validation.sanitizedName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(validation.sanitizedName) },
                enabled = validation.isValid
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
