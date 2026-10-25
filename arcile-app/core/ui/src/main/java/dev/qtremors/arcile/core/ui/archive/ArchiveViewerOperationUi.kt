package dev.qtremors.arcile.core.ui.archive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.storage.domain.storagePathName

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

@Composable
internal fun ArchiveOperationCard(
    operation: OperationUiState,
    onCancel: () -> Unit
) {
    val progress = operation.totalItems.takeIf { it > 0 }
        ?.let { operation.completedItems.toFloat() / it.toFloat() }
        ?.coerceIn(0f, 1f)
    val title = if (operation.isCancelling) {
        stringResource(R.string.file_operation_cancelling)
    } else {
        stringResource(R.string.file_operation_extracting_archive)
    }
    val currentName = operation.currentPath
        ?.let(::storagePathName)
        ?.takeIf { it.isNotBlank() }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenGutter, vertical = MaterialTheme.spacing.compactGap)
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(
                    enabled = !operation.isCancelling && operation.terminalStatus == null,
                    onClick = onCancel,
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                text = if (operation.totalItems > 0) {
                    stringResource(
                        R.string.file_operation_progress,
                        title,
                        operation.completedItems,
                        operation.totalItems
                    )
                } else {
                    title
                },
                style = MaterialTheme.typography.bodySmall
            )
            currentName?.let {
                Text(
                    text = stringResource(R.string.archive_operation_current_entry, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

