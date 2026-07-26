package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VisibilityOff
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.presentation.formatFileSize
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IgnoredItemsDialog(
    ignoredPaths: Set<String>,
    onUnignorePath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cleaner_ignored_items)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.cleaner_ignored_items_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ignoredPaths.isEmpty()) {
                    Surface(
                        shape = ExpressiveShapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.cleaner_no_ignored_items),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = stringResource(R.string.cleaner_no_ignored_items_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    val sortedPaths = ignoredPaths.sorted()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        itemsIndexed(sortedPaths, key = { _, path -> path }) { index, path ->
                            val normalizedPath = path.trimEnd('/')
                            val ignoredItem = File(path)
                            val itemName = normalizedPath.substringAfterLast('/').ifBlank { path }
                            val parentPath = normalizedPath.substringBeforeLast('/', "")
                            Surface(
                                shape = expressiveSegmentedShapes(index, sortedPaths.size).shape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (ignoredItem.isDirectory) {
                                                        Icons.Default.Folder
                                                    } else {
                                                        Icons.AutoMirrored.Filled.InsertDriveFile
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = itemName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cleanFilePath(parentPath).ifBlank { parentPath },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { onUnignorePath(path) },
                                        shape = ExpressiveShapes.medium,
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(stringResource(R.string.cleaner_restore_item))
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.cleaner_ignored_patterns_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium
            ) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
internal fun CleanerConfirmContent(
    selectedCandidates: List<CleanerCandidate>,
    hasHighRisk: Boolean,
    highRiskAcknowledged: Boolean,
    onHighRiskAcknowledgedChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.clean_confirm_message, selectedCandidates.size))
        LazyColumn(
            modifier = Modifier.height(180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(selectedCandidates, key = { it.absolutePath }) { candidate ->
                Column {
                    Text(
                        text = candidate.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.cleaner_confirm_file_detail,
                            formatFileSize(candidate.size),
                            cleanerRiskLabel(candidate.riskLevel)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = cleanerRiskColor(candidate.riskLevel)
                    )
                }
            }
        }
        if (hasHighRisk) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = highRiskAcknowledged,
                    onCheckedChange = onHighRiskAcknowledgedChange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cleaner_high_risk_acknowledge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
