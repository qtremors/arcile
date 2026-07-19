package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.feature.settings.VaultSecurityUiState
import dev.qtremors.arcile.core.presentation.formatFileSize

@Composable
internal fun SettingsPluginSection(onOpen: () -> Unit) {
    SettingsSection(title = stringResource(R.string.section_plugins)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.plugins_title)) },
            supportingContent = { Text(stringResource(R.string.plugins_settings_description)) },
            leadingContent = {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(onClick = onOpen)
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
        ListItem(
            headlineContent = { Text(stringResource(R.string.tool_onlyfiles)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_settings_description)) },
            leadingContent = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(onClick = onOpen)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.onlyfiles_screenshot_protection)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_screenshot_protection_description)) },
            trailingContent = {
                Switch(state.screenshotProtectionEnabled, actions.setScreenshotProtection)
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.onlyfiles_encrypted_thumbnail_cache)) },
            supportingContent = {
                Text(stringResource(R.string.onlyfiles_encrypted_thumbnail_cache_stats, state.encryptedThumbnailFiles, formatFileSize(state.encryptedThumbnailBytes)))
            },
            trailingContent = { Text(stringResource(R.string.settings_clear_thumbnail_cache)) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(enabled = !state.isBusy, onClick = actions.clearEncryptedThumbnails)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.onlyfiles_active_external_access)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_active_external_access_count, state.activeExternalGrants)) },
            trailingContent = { Text(stringResource(R.string.onlyfiles_revoke_all)) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(
                enabled = state.activeExternalGrants > 0,
                onClick = actions.revokeAllExternalAccess
            )
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.onlyfiles_security_disclosure_title)) },
            supportingContent = { Text(stringResource(R.string.onlyfiles_unaudited_short)) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(onClick = actions.showDisclosure)
        )
    }
}

@Composable
internal fun SettingsBackupSection(
    state: PreferencesBackupUiState,
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    val enabled = state != PreferencesBackupUiState.Busy
    SettingsSection(title = stringResource(R.string.section_setup)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup_export)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_export_description)) },
            leadingContent = {
                Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingContent = {
                if (!enabled) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(2.dp),
                        strokeWidth = 2.dp
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(
                enabled = enabled,
                onClick = onExport
            )
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backup_restore)) },
            supportingContent = { Text(stringResource(R.string.settings_backup_restore_description)) },
            leadingContent = {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier.clip(MaterialTheme.shapes.medium).bounceClickable(
                enabled = enabled,
                onClick = onRestore
            )
        )
    }
}

@Composable
internal fun SettingsAboutSection(onOpen: () -> Unit) {
    SettingsSection(title = stringResource(R.string.section_info)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.about_headline)) },
            supportingContent = { Text(stringResource(R.string.about_description)) },
            leadingContent = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .bounceClickable(onClick = onOpen)
        )
    }
}
