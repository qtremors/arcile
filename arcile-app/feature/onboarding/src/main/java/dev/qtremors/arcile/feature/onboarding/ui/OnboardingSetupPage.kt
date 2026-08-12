package dev.qtremors.arcile.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.platform.testTag
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
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingSetupPermissions(
    state: OnboardingUiState,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    restoreState: OnboardingRestoreState,
    onChooseRestoreBackup: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onAccessModeSelected: (PrivilegeMode) -> Unit,
    onReconnectAccess: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    showOlderAndroidWarning: Boolean
) {
    val haptics = rememberArcileHaptics()
    OnboardingPage(
        icon = Icons.Default.Settings,
        title = stringResource(R.string.onboarding_configure_title),
        description = stringResource(R.string.onboarding_configure_description),
        hasBodyContent = true
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            Surface(
                shape = expressiveSegmentedShapes(index = 0, count = 2).shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemeModeSelector(
                    currentMode = currentThemeState.themeMode,
                    onModeSelected = { onThemeChange(currentThemeState.copy(themeMode = it)) }
                )
            }
            Surface(
                shape = expressiveSegmentedShapes(index = 1, count = 2).shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                AccentColorSelector(
                    currentAccent = currentThemeState.accentColor,
                    onAccentSelected = { onThemeChange(currentThemeState.copy(accentColor = it)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection(title = stringResource(R.string.storage_access_provider_title)) {
            PrivilegeMode.entries.forEachIndexed { index, mode ->
                val selected = state.preferredAccessMode == mode
                val backendState = when (mode) {
                    PrivilegeMode.ROOT -> state.accessBackends[PrivilegeBackendId.ROOT]
                    PrivilegeMode.SHIZUKU -> state.accessBackends[PrivilegeBackendId.SHIZUKU]
                    PrivilegeMode.NORMAL -> state.accessBackends[PrivilegeBackendId.NORMAL]
                    PrivilegeMode.AUTOMATIC -> state.activeBackend?.let(state.accessBackends::get)
                }
                StorageAccessProviderRow(
                    mode = mode,
                    index = index,
                    selected = selected,
                    connectionState = backendState?.connectionState,
                    effectiveUid = if (selected) state.accessIdentity?.effectiveUid else null,
                    onClick = {
                        haptics.selectionChanged()
                        if (
                            selected &&
                            mode != PrivilegeMode.NORMAL &&
                            backendState?.connectionState != PrivilegeConnectionState.READY
                        ) {
                            onReconnectAccess()
                        } else {
                            onAccessModeSelected(mode)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val openStorageClick = {
            haptics.selectionChanged()
            onOpenStoragePermissionSettings()
        }
        val openNotificationsClick = {
            haptics.selectionChanged()
            onRequestNotificationPermission()
        }
        val showNormalPermission = state.preferredAccessMode == PrivilegeMode.NORMAL ||
            (state.preferredAccessMode == PrivilegeMode.AUTOMATIC &&
                state.activeBackend != PrivilegeBackendId.ROOT &&
                state.activeBackend != PrivilegeBackendId.SHIZUKU)
        val accessItemCount = (if (showNormalPermission) 1 else 0) +
            (if (state.notificationPermissionRequired) 1 else 0)
        if (accessItemCount > 0) {
            SettingsSection(title = stringResource(R.string.onboarding_system_access)) {
                if (showNormalPermission) {
                    OnboardingPermissionRow(
                        index = 0,
                        count = accessItemCount,
                        title = stringResource(R.string.onboarding_storage_title),
                        description = stringResource(R.string.onboarding_storage_description),
                        icon = Icons.Default.Storage,
                        granted = state.hasStoragePermission ||
                            state.accessBackends[PrivilegeBackendId.NORMAL]
                                ?.connectionState == PrivilegeConnectionState.READY,
                        grantedLabel = stringResource(R.string.onboarding_permission_granted),
                        actionLabel = stringResource(R.string.onboarding_permission_grant),
                        actionIsRequired = true,
                        onClick = openStorageClick
                    )
                }

                if (state.notificationPermissionRequired) {
                    OnboardingPermissionRow(
                        index = if (showNormalPermission) 1 else 0,
                        count = accessItemCount,
                        title = stringResource(R.string.onboarding_notifications_title),
                        description = stringResource(R.string.onboarding_notifications_description),
                        icon = Icons.Default.Notifications,
                        granted = state.hasNotificationPermission,
                        grantedLabel = stringResource(R.string.onboarding_permission_enabled),
                        actionLabel = stringResource(R.string.onboarding_enable_notifications),
                        onClick = openNotificationsClick
                    )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingPermissionRow(
    index: Int,
    count: Int,
    title: String,
    description: String,
    icon: ImageVector,
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onClick: () -> Unit,
    actionIsRequired: Boolean = false
) {
    SegmentedListItem(
        onClick = { if (!granted) onClick() },
        shapes = expressiveSegmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
            }
        },
        trailingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (granted) {
                    PermissionStatusChip(
                        label = grantedLabel,
                        icon = Icons.Default.CheckCircle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    FilledTonalButton(
                        onClick = onClick,
                        shape = ExpressiveShapes.medium,
                        colors = if (actionIsRequired) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StorageAccessProviderRow(
    mode: PrivilegeMode,
    index: Int,
    selected: Boolean,
    connectionState: PrivilegeConnectionState?,
    effectiveUid: Int?,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = expressiveSegmentedShapes(index = index, count = PrivilegeMode.entries.size),
        colors = ListItemDefaults.segmentedColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier
            .testTag("onboarding_access_mode_${mode.name.lowercase()}")
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        content = {
            Text(
                text = stringResource(mode.titleResource()),
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Column {
                Text(stringResource(mode.descriptionResource()))
                Text(
                    text = storageAccessStatus(mode, connectionState, effectiveUid),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = when (mode) {
                    PrivilegeMode.AUTOMATIC -> Icons.Default.Settings
                    PrivilegeMode.ROOT -> Icons.Default.PrivacyTip
                    PrivilegeMode.SHIZUKU -> Icons.Default.Bolt
                    PrivilegeMode.NORMAL -> Icons.Default.Storage
                },
                contentDescription = null
            )
        },
        trailingContent = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        }
    )
}

private fun PrivilegeMode.titleResource(): Int = when (this) {
    PrivilegeMode.AUTOMATIC -> R.string.storage_access_mode_automatic
    PrivilegeMode.ROOT -> R.string.storage_access_mode_root
    PrivilegeMode.SHIZUKU -> R.string.storage_access_mode_shizuku
    PrivilegeMode.NORMAL -> R.string.storage_access_mode_normal
}

private fun PrivilegeMode.descriptionResource(): Int = when (this) {
    PrivilegeMode.AUTOMATIC -> R.string.storage_access_mode_automatic_description
    PrivilegeMode.ROOT -> R.string.storage_access_mode_root_description
    PrivilegeMode.SHIZUKU -> R.string.storage_access_mode_shizuku_description
    PrivilegeMode.NORMAL -> R.string.storage_access_mode_normal_description
}

@Composable
private fun storageAccessStatus(
    mode: PrivilegeMode,
    state: PrivilegeConnectionState?,
    effectiveUid: Int?
): String = when {
    state == PrivilegeConnectionState.READY && effectiveUid == 0 ->
        stringResource(R.string.storage_access_status_connected_root)
    state == PrivilegeConnectionState.READY && effectiveUid == 2000 ->
        stringResource(R.string.storage_access_status_connected_shell)
    state == PrivilegeConnectionState.READY && mode == PrivilegeMode.NORMAL ->
        stringResource(R.string.storage_access_status_granted)
    state == PrivilegeConnectionState.READY ->
        stringResource(R.string.storage_access_status_connected)
    state == PrivilegeConnectionState.CONNECTING ->
        stringResource(R.string.storage_access_status_connecting)
    state == PrivilegeConnectionState.PERMISSION_REQUIRED ->
        stringResource(R.string.storage_access_status_permission_required)
    state == PrivilegeConnectionState.PERMISSION_DENIED ->
        stringResource(R.string.storage_access_status_permission_denied)
    state == PrivilegeConnectionState.INSTALLED_BUT_STOPPED ->
        stringResource(R.string.storage_access_status_stopped)
    state == PrivilegeConnectionState.INCOMPATIBLE ->
        stringResource(R.string.storage_access_status_incompatible)
    state == PrivilegeConnectionState.DISCONNECTED ->
        stringResource(R.string.storage_access_status_disconnected)
    state == PrivilegeConnectionState.FAILED ->
        stringResource(R.string.storage_access_status_failed)
    else -> stringResource(R.string.storage_access_status_unavailable)
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
