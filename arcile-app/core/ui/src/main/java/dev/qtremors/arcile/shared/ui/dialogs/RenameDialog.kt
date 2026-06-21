package dev.qtremors.arcile.shared.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    existingNames: Set<String> = emptySet()
) {
    var newName by remember { mutableStateOf(currentName) }
    val validation = remember(newName, existingNames, currentName) {
        validateFileName(newName, existingNames, ignoredName = currentName)
    }
    val hasChanged = validation.sanitizedName != currentName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_rename)) },
        shape = MaterialTheme.shapes.extraLarge,
        text = {
            FileNameInput(
                value = newName,
                onValueChange = { newName = it },
                label = stringResource(R.string.label_new_name),
                existingNames = existingNames,
                ignoredName = currentName,
                onDone = {
                    if (validation.isValid && hasChanged) {
                        onConfirm(validation.sanitizedName)
                    }
                }
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(validation.sanitizedName) },
                enabled = validation.isValid && hasChanged,
                shape = ExpressiveShapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_rename))
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
