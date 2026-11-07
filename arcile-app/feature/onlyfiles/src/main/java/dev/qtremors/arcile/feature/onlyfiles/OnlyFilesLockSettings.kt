package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay

internal fun VaultBackgroundLockDelay.label(): Int = when (this) {
    VaultBackgroundLockDelay.IMMEDIATE -> R.string.onlyfiles_lock_immediately
    VaultBackgroundLockDelay.THIRTY_SECONDS -> R.string.onlyfiles_lock_thirty_seconds
    VaultBackgroundLockDelay.ONE_MINUTE -> R.string.onlyfiles_lock_one_minute
    VaultBackgroundLockDelay.FIVE_MINUTES -> R.string.onlyfiles_lock_five_minutes
}

/** Picks how long an unlocked vault stays open after Arcile leaves the screen. */
@Composable
internal fun BackgroundLockDelayDialog(
    current: VaultBackgroundLockDelay,
    onSelect: (VaultBackgroundLockDelay) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_background_lock)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VaultBackgroundLockDelay.entries.forEach { delay ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = delay == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(delay) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = delay == current, onClick = null)
                        Text(stringResource(delay.label()), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
    )
}
