package dev.qtremors.arcile.presentation.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
    val haptics = dev.qtremors.arcile.presentation.ui.components.rememberArcileHaptics()
    val permanentlyDeleteLabel = stringResource(R.string.permanently_delete_checkbox)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isPermanentDeleteChecked) Icons.Outlined.DeleteForever else Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (isPermanentDeleteChecked)
                    stringResource(R.string.delete_permanent_title, selectedCount)
                else
                    stringResource(R.string.delete_items_title, selectedCount),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPermanentDeleteChecked)
                        stringResource(R.string.delete_permanent_description)
                    else
                        stringResource(R.string.delete_items_description),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isPermanentDeleteChecked) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .semantics(mergeDescendants = true) {
                            if (!isPermanentDeleteToggleEnabled) disabled()
                        }
                        .clickable(enabled = isPermanentDeleteToggleEnabled) {
                            haptics.selectionChanged()
                            onTogglePermanentDelete()
                        }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = permanentlyDeleteLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isPermanentDeleteToggleEnabled) {
                                    if (isPermanentDeleteChecked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.permanently_delete_checkbox_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPermanentDeleteToggleEnabled) {
                                    if (isPermanentDeleteChecked) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = isPermanentDeleteChecked,
                                onCheckedChange = null,
                                enabled = isPermanentDeleteToggleEnabled,
                                modifier = Modifier
                                    .testTag("permanent_delete_switch")
                                    .semantics {
                                        contentDescription = permanentlyDeleteLabel
                                    }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.destructiveConfirm()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
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
