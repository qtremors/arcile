package dev.qtremors.arcile.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.feature.onboarding.OnboardingRestoreFailure
import dev.qtremors.arcile.feature.onboarding.OnboardingRestoreItem
import dev.qtremors.arcile.feature.onboarding.OnboardingRestoreState
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.settings.AccentColorSelector
import dev.qtremors.arcile.core.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.rememberArcileHaptics

@Composable
internal fun OnboardingRestoreDialog(
    state: OnboardingRestoreState,
    onApplyRestoreBackup: () -> Unit,
    onDismissRestoreBackup: () -> Unit,
    onRestartApp: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    when (state) {
        OnboardingRestoreState.Idle,
        OnboardingRestoreState.Busy -> Unit
        is OnboardingRestoreState.Preview -> AlertDialog(
            onDismissRequest = onDismissRestoreBackup,
            icon = { Icon(Icons.Default.SettingsBackupRestore, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_backup_restore_preview_title)) },
            text = {
                OnboardingRestoreItemList(
                    description = stringResource(R.string.settings_backup_restore_preview_description),
                    items = state.items,
                    failures = emptyList()
                )
            },
            confirmButton = {
                val applyClick = {
                    haptics.selectionChanged()
                    onApplyRestoreBackup()
                }
                FilledTonalButton(
                    onClick = applyClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = applyClick)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_backup_restore))
                }
            },
            dismissButton = {
                val dismissClick = {
                    haptics.selectionChanged()
                    onDismissRestoreBackup()
                }
                FilledTonalButton(
                    onClick = dismissClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = dismissClick)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        is OnboardingRestoreState.Restored -> AlertDialog(
            onDismissRequest = onDismissRestoreBackup,
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_backup_restore_complete_title)) },
            text = {
                OnboardingRestoreItemList(
                    description = stringResource(R.string.settings_backup_restore_complete_description, state.items.size),
                    items = state.items,
                    failures = state.failures
                )
            },
            confirmButton = {
                val restartClick = {
                    haptics.selectionChanged()
                    onRestartApp()
                }
                FilledTonalButton(
                    onClick = restartClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = restartClick)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.restart_now))
                }
            },
            dismissButton = {
                val dismissClick = {
                    haptics.selectionChanged()
                    onDismissRestoreBackup()
                }
                FilledTonalButton(
                    onClick = dismissClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = dismissClick)
                ) {
                    Text(stringResource(R.string.later))
                }
            }
        )
        is OnboardingRestoreState.Failed -> AlertDialog(
            onDismissRequest = onDismissRestoreBackup,
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_backup_failed_title)) },
            text = { Text(state.message) },
            confirmButton = {
                val dismissClick = {
                    haptics.selectionChanged()
                    onDismissRestoreBackup()
                }
                FilledTonalButton(
                    onClick = dismissClick,
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable(onClick = dismissClick)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
@Composable
private fun OnboardingRestoreItemList(
    description: String,
    items: List<OnboardingRestoreItem>,
    failures: List<OnboardingRestoreFailure>
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            items.forEach { item ->
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
                        RestoreStatusPill(label = item.status)
                    }
                }
            }
            failures.forEach { failure ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(failure.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(failure.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
