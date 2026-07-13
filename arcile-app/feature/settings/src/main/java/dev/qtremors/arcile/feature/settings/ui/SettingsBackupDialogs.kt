package dev.qtremors.arcile.feature.settings.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItem
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupItemStatus
import dev.qtremors.arcile.core.ui.backup.PreferencesBackupOperationResult
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun SettingsBackupDialogs(
    state: PreferencesBackupUiState,
    onApplyRestore: (Uri) -> Unit,
    onClear: () -> Unit,
    onRestart: () -> Unit
) {
    when (state) {
        PreferencesBackupUiState.Idle,
        PreferencesBackupUiState.Busy -> Unit
        is PreferencesBackupUiState.RestorePreview -> AlertDialog(
            onDismissRequest = onClear,
            title = { Text(stringResource(R.string.settings_backup_restore_preview_title)) },
            text = {
                BackupItemList(
                    description = stringResource(R.string.settings_backup_restore_preview_description),
                    items = state.preview.items
                )
            },
            confirmButton = {
                DialogButton(
                    text = stringResource(R.string.settings_backup_restore),
                    onClick = { onApplyRestore(state.uri) }
                )
            },
            dismissButton = {
                DialogButton(text = stringResource(R.string.cancel), onClick = onClear)
            }
        )
        is PreferencesBackupUiState.Exported -> AlertDialog(
            onDismissRequest = onClear,
            title = { Text(stringResource(R.string.settings_backup_export_complete_title)) },
            text = {
                BackupResultList(
                    description = stringResource(
                        R.string.settings_backup_export_complete_description,
                        state.result.successCount
                    ),
                    result = state.result
                )
            },
            confirmButton = {
                DialogButton(text = stringResource(R.string.ok), onClick = onClear)
            }
        )
        is PreferencesBackupUiState.Restored -> AlertDialog(
            onDismissRequest = onClear,
            title = { Text(stringResource(R.string.settings_backup_restore_complete_title)) },
            text = {
                BackupResultList(
                    description = stringResource(
                        R.string.settings_backup_restore_complete_description,
                        state.result.successCount
                    ),
                    result = state.result
                )
            },
            confirmButton = {
                DialogButton(text = stringResource(R.string.restart_now), onClick = onRestart)
            },
            dismissButton = {
                DialogButton(text = stringResource(R.string.later), onClick = onClear)
            }
        )
        is PreferencesBackupUiState.Failed -> AlertDialog(
            onDismissRequest = onClear,
            title = { Text(stringResource(R.string.settings_backup_failed_title)) },
            text = { Text(state.message.asString(LocalContext.current)) },
            confirmButton = {
                DialogButton(text = stringResource(R.string.ok), onClick = onClear)
            }
        )
    }
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = ExpressiveShapes.medium,
        modifier = Modifier.bounceClickable(onClick = onClick)
    ) {
        Text(text)
    }
}

@Composable
private fun BackupItemList(
    description: String,
    items: List<PreferencesBackupItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { BackupItemRow(it) }
        }
    }
}

@Composable
private fun BackupResultList(
    description: String,
    result: PreferencesBackupOperationResult
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            result.items.forEach { BackupItemRow(it) }
            result.failures.forEach { failure ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            failure.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            failure.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupItemRow(item: PreferencesBackupItem) {
    val status = when (item.status) {
        PreferencesBackupItemStatus.Exported -> R.string.settings_backup_status_exported
        PreferencesBackupItemStatus.WillRestore -> R.string.settings_backup_status_will_restore
        PreferencesBackupItemStatus.WillReset -> R.string.settings_backup_status_will_reset
        PreferencesBackupItemStatus.Restored -> R.string.settings_backup_status_restored
        PreferencesBackupItemStatus.Reset -> R.string.settings_backup_status_reset
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(item.label, style = MaterialTheme.typography.bodyMedium)
            Surface(
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
