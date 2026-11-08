package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileListSurface
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.settings.SettingsChoiceHeader
import dev.qtremors.arcile.core.ui.settings.SettingsConnectedChoices
import dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay

/** Short label for each delay, sized to fit side by side in connected buttons. */
internal fun VaultBackgroundLockDelay.label(): Int = when (this) {
    VaultBackgroundLockDelay.IMMEDIATE -> R.string.onlyfiles_lock_immediately
    VaultBackgroundLockDelay.THIRTY_SECONDS -> R.string.onlyfiles_lock_thirty_seconds
    VaultBackgroundLockDelay.ONE_MINUTE -> R.string.onlyfiles_lock_one_minute
    VaultBackgroundLockDelay.FIVE_MINUTES -> R.string.onlyfiles_lock_five_minutes
}

/**
 * How long an unlocked vault stays open after Arcile leaves the screen, as one row of connected
 * buttons in the same style as Arcile's other settings choices.
 */
@Composable
internal fun BackgroundLockDelaySelector(
    current: VaultBackgroundLockDelay,
    onSelect: (VaultBackgroundLockDelay) -> Unit
) {
    val haptics = rememberArcileHaptics()
    val delays = VaultBackgroundLockDelay.entries
    ArcileListSurface {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            SettingsChoiceHeader(
                title = stringResource(R.string.onlyfiles_background_lock),
                description = stringResource(R.string.onlyfiles_background_lock_description),
                icon = Icons.Default.Timer
            )
            SettingsConnectedChoices(
                options = delays.map { stringResource(it.label()) },
                isSelected = { delays[it] == current },
                onSelectionChanged = { index, checked ->
                    if (checked && delays[index] != current) {
                        haptics.toggleMenu()
                        onSelect(delays[index])
                    }
                },
                testTagForIndex = { "lock_delay_${delays[it].name}" },
                dynamicExpand = true,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
        }
    }
}
