package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryQueue
import java.text.DateFormat
import java.util.Date

@Composable
internal fun OnlyFilesRecoveryDialog(
    panel: OnlyFilesRecoveryState,
    onResume: (VaultRecoveryQueue) -> Unit,
    onDiscard: (VaultRecoveryQueue) -> Unit,
    onDismiss: () -> Unit
) {
    var discard by remember { mutableStateOf<VaultRecoveryQueue?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_interrupted_transfers)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (panel.loading) CircularProgressIndicator()
                panel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!panel.loading && panel.error == null && panel.queues.isEmpty()) {
                    Text(stringResource(R.string.onlyfiles_recovery_empty))
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(panel.queues, key = { it.id }) { queue ->
                        RecoveryQueueRow(queue, enabled = !panel.loading, onResume = { onResume(queue) }) {
                            discard = queue
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_recovery_close)) }
        }
    )
    discard?.let { queue ->
        AlertDialog(
            onDismissRequest = { discard = null },
            title = { Text(stringResource(R.string.onlyfiles_recovery_discard)) },
            text = { Text(stringResource(R.string.onlyfiles_recovery_discard_description)) },
            confirmButton = {
                TextButton(onClick = { discard = null; onDiscard(queue) }) {
                    Text(stringResource(R.string.onlyfiles_recovery_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = null }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun RecoveryQueueRow(
    queue: VaultRecoveryQueue,
    enabled: Boolean,
    onResume: () -> Unit,
    onDiscard: () -> Unit
) {
    val title = when (queue.kind) {
        VaultRecoveryKind.IMPORT -> R.string.onlyfiles_recovery_import
        VaultRecoveryKind.EXPORT -> R.string.onlyfiles_recovery_export
        VaultRecoveryKind.PASTE -> R.string.onlyfiles_recovery_paste
        null -> R.string.onlyfiles_recovery_unreadable
    }
    val date = remember(queue.createdAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(queue.createdAtMillis))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(date, style = MaterialTheme.typography.bodySmall)
        if (queue.kind != null) {
            Text(stringResource(R.string.onlyfiles_recovery_counts, queue.settledItems, queue.remainingItems))
        }
        if (queue.hasUncertainItem) Text(stringResource(R.string.onlyfiles_recovery_uncertain, queue.settledItems + 1))
        if (queue.wasMove && queue.remainingItems > 0) Text(stringResource(R.string.onlyfiles_recovery_move))
        if (queue.lockedSourceVaults.isNotEmpty()) Text(stringResource(R.string.onlyfiles_recovery_unlock_source))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(enabled = enabled, onClick = onDiscard) {
                Text(stringResource(R.string.onlyfiles_recovery_discard))
            }
            if (queue.remainingItems > 0) {
                TextButton(enabled = enabled && queue.lockedSourceVaults.isEmpty(), onClick = onResume) {
                    Text(stringResource(if (queue.wasMove) R.string.onlyfiles_recovery_resume_copy else R.string.onlyfiles_recovery_resume))
                }
            }
        }
    }
}
