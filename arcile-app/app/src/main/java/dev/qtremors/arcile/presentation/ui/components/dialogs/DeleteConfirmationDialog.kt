package dev.qtremors.arcile.presentation.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R

@Composable
fun DeleteConfirmationDialog(
    selectedCount: Int,
    isPermanentDeleteChecked: Boolean,
    isPermanentDeleteToggleEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePermanentDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isPermanentDeleteChecked)
                    stringResource(R.string.delete_permanent_title, selectedCount)
                else
                    stringResource(R.string.delete_items_title, selectedCount)
            )
        },
        text = {
            Column {
                Text(
                    if (isPermanentDeleteChecked)
                        stringResource(R.string.delete_permanent_description)
                    else
                        stringResource(R.string.delete_items_description)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = isPermanentDeleteToggleEnabled) { onTogglePermanentDelete() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPermanentDeleteChecked,
                        onCheckedChange = { onTogglePermanentDelete() },
                        enabled = isPermanentDeleteToggleEnabled
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.permanently_delete_checkbox),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isPermanentDeleteToggleEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = stringResource(R.string.permanently_delete_checkbox_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPermanentDeleteToggleEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
