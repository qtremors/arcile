package dev.qtremors.arcile.shared.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.DeleteDestination
import dev.qtremors.arcile.utils.formatFileSize

@Composable
fun DeleteConfirmationDialog(
    selectedCount: Int,
    isPermanentDeleteChecked: Boolean,
    isPermanentDeleteToggleEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    decision: DeleteDecision? = null,
    isShredChecked: Boolean = false,
    onToggleShred: (() -> Unit)? = null
) {
    val haptics = dev.qtremors.arcile.shared.ui.rememberArcileHaptics()
    val permanentlyDeleteLabel = stringResource(R.string.permanently_delete_checkbox)
    val resolvedDecision = decision ?: DeleteDecision(
        destination = if (isPermanentDeleteChecked) DeleteDestination.Permanent else DeleteDestination.Trash,
        selectedCount = selectedCount,
        totalBytes = 0L,
        fileCount = selectedCount,
        folderCount = 0,
        irreversible = isPermanentDeleteChecked
    )
    val irreversible = resolvedDecision.irreversible || isPermanentDeleteChecked
    val effectiveDestination = when {
        resolvedDecision.destination == DeleteDestination.MixedBlocked -> DeleteDestination.MixedBlocked
        isPermanentDeleteChecked -> DeleteDestination.Permanent
        else -> resolvedDecision.destination
    }
    val destinationLabel = when (effectiveDestination) {
        DeleteDestination.Trash -> stringResource(R.string.delete_destination_trash)
        DeleteDestination.Permanent -> stringResource(R.string.delete_destination_permanent)
        DeleteDestination.AndroidSystemConfirmation -> stringResource(R.string.delete_destination_android)
        DeleteDestination.MixedBlocked -> stringResource(R.string.delete_destination_mixed)
    }
    val description = when (effectiveDestination) {
        DeleteDestination.Trash -> stringResource(R.string.delete_decision_trash_description)
        DeleteDestination.Permanent -> stringResource(R.string.delete_decision_permanent_description)
        DeleteDestination.AndroidSystemConfirmation -> stringResource(R.string.delete_decision_android_description)
        DeleteDestination.MixedBlocked -> stringResource(R.string.delete_decision_mixed_description)
    }
    val summary = stringResource(
        R.string.delete_decision_summary,
        resolvedDecision.selectedCount,
        formatFileSize(resolvedDecision.totalBytes),
        resolvedDecision.folderCount
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = when (effectiveDestination) {
                    DeleteDestination.Trash -> Icons.Outlined.Delete
                    DeleteDestination.Permanent -> Icons.Outlined.DeleteForever
                    DeleteDestination.AndroidSystemConfirmation -> Icons.Outlined.Security
                    DeleteDestination.MixedBlocked -> Icons.Outlined.DeleteSweep
                },
                contentDescription = null,
                tint = if (irreversible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = destinationLabel,
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
                    text = description,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = summary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (irreversible) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.delete_irreversible_warning),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                if (resolvedDecision.destination != DeleteDestination.MixedBlocked) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                                            MaterialTheme.colorScheme.onSurface
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
                                            MaterialTheme.colorScheme.onSurfaceVariant
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

                        if (isPermanentDeleteChecked && onToggleShred != null) {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable {
                                        haptics.selectionChanged()
                                        onToggleShred()
                                    }
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = stringResource(R.string.shred_permanently_checkbox),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = stringResource(R.string.shred_permanently_checkbox_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = {
                                        Checkbox(
                                            checked = isShredChecked,
                                            onCheckedChange = null
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (resolvedDecision.destination != DeleteDestination.MixedBlocked) {
                Button(
                    onClick = {
                        if (irreversible) haptics.destructiveConfirm() else haptics.selectionChanged()
                        onConfirm()
                    },
                    colors = if (irreversible) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(
                        if (resolvedDecision.destination == DeleteDestination.Trash && !isPermanentDeleteChecked)
                            stringResource(R.string.move_to_trash)
                        else
                            stringResource(R.string.delete)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
