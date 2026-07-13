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
internal fun OnboardingSetupPermissions(
    state: OnboardingUiState,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    restoreState: OnboardingRestoreState,
    onChooseRestoreBackup: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    showOlderAndroidWarning: Boolean
) {
    val haptics = rememberArcileHaptics()
    val storageBgColor by animateColorAsState(
        targetValue = if (state.hasStoragePermission) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        },
        label = "storage_permission_bg"
    )
    val notificationBgColor by animateColorAsState(
        targetValue = if (state.hasNotificationPermission) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        },
        label = "notification_permission_bg"
    )

    OnboardingPage(
        icon = Icons.Default.Settings,
        title = stringResource(R.string.onboarding_configure_title),
        description = stringResource(R.string.onboarding_configure_description),
        hasBodyContent = true
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Appearance Selector Card
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ThemeModeSelector(
                    currentMode = currentThemeState.themeMode,
                    onModeSelected = { onThemeChange(currentThemeState.copy(themeMode = it)) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                AccentColorSelector(
                    currentAccent = currentThemeState.accentColor,
                    onAccentSelected = { onThemeChange(currentThemeState.copy(accentColor = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permissions Card
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_system_access),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Storage Permission Row
                val openStorageClick = {
                    haptics.selectionChanged()
                    onOpenStoragePermissionSettings()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ExpressiveShapes.medium)
                        .background(storageBgColor)
                        .bounceClickable(
                            enabled = !state.hasStoragePermission,
                            onClick = openStorageClick
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.hasStoragePermission) {
                                stringResource(R.string.onboarding_storage_ready_title)
                            } else {
                                stringResource(R.string.onboarding_storage_title)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.hasStoragePermission) {
                                stringResource(R.string.onboarding_storage_ready_description)
                            } else {
                                stringResource(R.string.onboarding_storage_description)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (state.hasStoragePermission) {
                        PermissionStatusChip(
                            label = stringResource(R.string.onboarding_permission_granted),
                            icon = Icons.Default.CheckCircle,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        OnboardingActionChip(
                            onClick = openStorageClick,
                            label = stringResource(R.string.onboarding_permission_grant),
                            icon = Icons.Default.WarningAmber,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Notification Permission Row
                if (state.notificationPermissionRequired) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    val openNotificationsClick = {
                        haptics.selectionChanged()
                        onRequestNotificationPermission()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExpressiveShapes.medium)
                            .background(notificationBgColor)
                            .bounceClickable(
                                enabled = !state.hasNotificationPermission,
                                onClick = openNotificationsClick
                            )
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.onboarding_notifications_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.onboarding_notifications_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (state.hasNotificationPermission) {
                            PermissionStatusChip(
                                label = stringResource(R.string.onboarding_permission_enabled),
                                icon = Icons.Default.CheckCircle,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            OnboardingActionChip(
                                onClick = openNotificationsClick,
                                label = stringResource(R.string.onboarding_enable_notifications),
                                icon = Icons.Default.Notifications,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Restore Backup Button
        val chooseRestoreBackupClick = {
            haptics.selectionChanged()
            onChooseRestoreBackup()
        }
        FilledTonalButton(
            onClick = chooseRestoreBackupClick,
            enabled = restoreState != OnboardingRestoreState.Busy,
            modifier = Modifier
                .fillMaxWidth()
                .bounceClickable(
                    enabled = restoreState != OnboardingRestoreState.Busy,
                    onClick = chooseRestoreBackupClick
                ),
            shape = ExpressiveShapes.medium
        ) {
            Icon(Icons.Default.SettingsBackupRestore, contentDescription = null)
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.small))
            Text(stringResource(R.string.onboarding_restore_backup_action))
        }

        if (showOlderAndroidWarning) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.onboarding_limited_android_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.onboarding_limited_android_description),
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
private fun OnboardingActionChip(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = ExpressiveShapes.medium,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .clip(ExpressiveShapes.medium)
            .bounceClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable
private fun PermissionStatusChip(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun RestoreStatusPill(label: String) {
    Surface(
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
