package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay

internal fun VaultBackgroundLockDelay.label(): Int = when (this) {
    VaultBackgroundLockDelay.IMMEDIATE -> R.string.onlyfiles_lock_immediately
    VaultBackgroundLockDelay.THIRTY_SECONDS -> R.string.onlyfiles_lock_thirty_seconds
    VaultBackgroundLockDelay.ONE_MINUTE -> R.string.onlyfiles_lock_one_minute
    VaultBackgroundLockDelay.FIVE_MINUTES -> R.string.onlyfiles_lock_five_minutes
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnlyFilesLockSettings(
    state: OnlyFilesUiState,
    onDelay: (VaultBackgroundLockDelay) -> Unit,
    onScreenOff: (Boolean) -> Unit,
    onNumeric: (Boolean) -> Unit
) {
    var chooseDelay by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SegmentedListItem(
            onClick = { chooseDelay = true },
            shapes = expressiveSegmentedShapes(0, 3),
            content = { Text(stringResource(R.string.onlyfiles_background_lock)) },
            supportingContent = { Text(stringResource(state.backgroundLockDelay.label())) },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )
        SegmentedListItem(
            onClick = { onScreenOff(!state.lockOnScreenOff) },
            shapes = expressiveSegmentedShapes(1, 3),
            content = { Text(stringResource(R.string.onlyfiles_lock_screen_off)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_lock_screen_off_description)) },
            trailingContent = { Switch(state.lockOnScreenOff, onScreenOff) },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )
        SegmentedListItem(
            onClick = { onNumeric(!state.numericPasswordKeyboard) },
            shapes = expressiveSegmentedShapes(2, 3),
            content = { Text(stringResource(R.string.onlyfiles_numeric_keyboard)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_numeric_keyboard_description)) },
            trailingContent = { Switch(state.numericPasswordKeyboard, onNumeric) },
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )
        Text(stringResource(R.string.onlyfiles_retention_description), style = MaterialTheme.typography.bodySmall)
    }
    if (chooseDelay) AlertDialog(
        onDismissRequest = { chooseDelay = false },
        title = { Text(stringResource(R.string.onlyfiles_background_lock)) },
        text = {
            Column {
                VaultBackgroundLockDelay.entries.forEach { delay ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = delay == state.backgroundLockDelay, role = Role.RadioButton,
                            onClick = { chooseDelay = false; onDelay(delay) }
                        ), verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = delay == state.backgroundLockDelay, onClick = null)
                        Text(stringResource(delay.label()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { chooseDelay = false }) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}
