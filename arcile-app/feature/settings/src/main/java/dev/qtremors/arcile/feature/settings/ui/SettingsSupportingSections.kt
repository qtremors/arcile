@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.feature.settings.VaultSecurityUiState
import dev.qtremors.arcile.core.presentation.formatFileSize

@Composable
internal fun SettingsPluginSection(onOpen: () -> Unit) {
    SettingsSection(title = stringResource(R.string.section_plugins)) {
        SegmentedListItem(
            onClick = onOpen,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 0, count = 1),
            content = { Text(stringResource(R.string.plugins_title)) },
            supportingContent = { Text(stringResource(R.string.plugins_settings_description)) },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
    }
}

@Composable
internal fun SettingsOnlyFilesSection(
    state: VaultSecurityUiState,
    onOpen: () -> Unit,
    actions: SettingsVaultActions
) {
    SettingsSection(title = stringResource(R.string.onlyfiles_settings_section)) {
        SegmentedListItem(
            onClick = onOpen,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 0, count = 5),
            content = { Text(stringResource(R.string.tool_onlyfiles)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_settings_description)) },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
        SegmentedListItem(
            onClick = { actions.setScreenshotProtection(!state.screenshotProtectionEnabled) },
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 1, count = 5),
            content = { Text(stringResource(R.string.onlyfiles_screenshot_protection)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_screenshot_protection_description)) },
            trailingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Switch(state.screenshotProtectionEnabled, actions.setScreenshotProtection)
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
        SegmentedListItem(
            onClick = { if (!state.isBusy) actions.clearEncryptedThumbnails() },
            enabled = !state.isBusy,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 2, count = 5),
            content = { Text(stringResource(R.string.onlyfiles_encrypted_thumbnail_cache)) },
            supportingContent = {
                Text(stringResource(R.string.onlyfiles_encrypted_thumbnail_cache_stats, state.encryptedThumbnailFiles, formatFileSize(state.encryptedThumbnailBytes)))
            },
            trailingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = { if (!state.isBusy) actions.clearEncryptedThumbnails() },
                        enabled = !state.isBusy
                    ) {
                        Text(stringResource(R.string.settings_clear_thumbnail_cache))
                    }
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
        SegmentedListItem(
            onClick = {
                if (state.activeExternalGrants > 0) actions.revokeAllExternalAccess()
            },
            enabled = state.activeExternalGrants > 0,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 3, count = 5),
            content = { Text(stringResource(R.string.onlyfiles_active_external_access)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_active_external_access_count, state.activeExternalGrants)) },
            trailingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (state.activeExternalGrants > 0) actions.revokeAllExternalAccess()
                        },
                        enabled = state.activeExternalGrants > 0
                    ) {
                        Text(stringResource(R.string.onlyfiles_revoke_all))
                    }
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
        SegmentedListItem(
            onClick = actions.showDisclosure,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 4, count = 5),
            content = { Text(stringResource(R.string.onlyfiles_security_disclosure_title)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_unaudited_short)) },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsBackupSection(
    state: PreferencesBackupUiState,
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    val enabled = state != PreferencesBackupUiState.Busy
    SettingsSection(title = stringResource(R.string.section_setup)) {
        SegmentedListItem(
            onClick = { if (enabled) onExport() },
            enabled = enabled,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 0, count = 2),
            content = { Text(stringResource(R.string.settings_backup_export)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_export_description)) },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            trailingContent = {
                if (!enabled) {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
        SegmentedListItem(
            onClick = { if (enabled) onRestore() },
            enabled = enabled,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 1, count = 2),
            content = { Text(stringResource(R.string.settings_backup_restore)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_restore_description)) },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
    }
}

@Composable
internal fun SettingsAboutSection(onOpen: () -> Unit) {
    SettingsSection(title = stringResource(R.string.section_info)) {
        SegmentedListItem(
            onClick = onOpen,
            shapes = dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes(index = 0, count = 1),
            content = { Text(stringResource(R.string.about_headline)) },
            supportingContent = { Text(stringResource(R.string.about_description)) },
            leadingContent = {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.height(IntrinsicSize.Min)
        )
    }
}
