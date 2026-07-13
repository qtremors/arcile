package dev.qtremors.arcile.feature.storagecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import dev.qtremors.arcile.core.presentation.formatFileSize

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
            if (ignoredPaths.isEmpty()) {
                Text(
                    text = stringResource(R.string.cleaner_no_ignored_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ignoredPaths.sorted(), key = { it }) { path ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = path,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { onUnignorePath(path) },
                                shape = ExpressiveShapes.medium
                            ) {
                                Text(stringResource(R.string.cleaner_restore_item))
                            }
                        }
                    }
                }
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
