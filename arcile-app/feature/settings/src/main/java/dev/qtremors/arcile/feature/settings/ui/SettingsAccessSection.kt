package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.settings.SettingsSection

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAccessSection(
    state: SettingsAccessState,
    actions: SettingsAccessActions
) {
    var confirmProtectedWrites by remember { mutableStateOf(false) }
    val access = state.access
    val rootState = access.backendStates[PrivilegeBackendId.ROOT]?.connectionState
    val rootedDevice = rootState != null && rootState != PrivilegeConnectionState.UNAVAILABLE
    val rootReady = access.isReady && access.identity?.isRoot == true
    val shizukuState = access.backendStates[PrivilegeBackendId.SHIZUKU]?.connectionState
    val shizukuEnabled = state.preference.shizukuPreviouslyAuthorized && !rootedDevice

    SettingsSection(title = stringResource(R.string.settings_storage_access_title)) {
        Text(
            text = if (rootedDevice) {
                stringResource(
                    if (rootReady) R.string.settings_rooted_device_active
                    else R.string.settings_rooted_device_detected
                )
            } else if (access.activeBackend == PrivilegeBackendId.SHIZUKU) {
                stringResource(R.string.settings_shizuku_active)
            } else {
                stringResource(R.string.settings_normal_access_active)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        if (!rootedDevice) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_use_shizuku),
                description = stringResource(
                    when (shizukuState) {
                        PrivilegeConnectionState.READY,
                        PrivilegeConnectionState.DISCONNECTED -> R.string.settings_use_shizuku_ready_description
                        PrivilegeConnectionState.INSTALLED_BUT_STOPPED -> R.string.settings_use_shizuku_stopped_description
                        PrivilegeConnectionState.PERMISSION_DENIED -> R.string.settings_use_shizuku_denied_description
                        else -> R.string.settings_use_shizuku_description
                    }
                ),
                checked = shizukuEnabled,
                switchTag = "shizuku_enabled_switch",
                rowTag = "shizuku_enabled_row",
                leadingIcon = Icons.Default.Bolt,
                enabled = !state.isBusy,
                onCheckedChange = actions.shizukuEnabledChange
            )
        }

        state.actionFailure?.let { message ->
            Text(
                text = message.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (
                !rootedDevice &&
                access.backendStates[PrivilegeBackendId.NORMAL]
                    ?.connectionState != PrivilegeConnectionState.READY
            ) {
                Button(
                    onClick = actions.grantNormalPermission,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("storage_access_grant_normal")
                ) {
                    Text(stringResource(R.string.browser_access_grant_normal))
                }
            }
            if (!rootedDevice && shizukuState != PrivilegeConnectionState.UNAVAILABLE) {
                FilledTonalButton(
                    onClick = actions.openShizukuManager,
                    modifier = Modifier.testTag("storage_access_open_shizuku")
                ) {
                    Text(stringResource(R.string.settings_access_open_shizuku))
                }
            }
        }

        SettingsSwitchRow(
            title = stringResource(R.string.settings_protected_writes),
            description = stringResource(
                if (rootReady || state.preference.protectedFilesystemWritesEnabled) {
                    R.string.settings_protected_writes_description
                } else {
                    R.string.settings_protected_writes_requires_root
                }
            ),
            checked = state.preference.protectedFilesystemWritesEnabled,
            switchTag = "protected_writes_switch",
            rowTag = "protected_writes_row",
            leadingIcon = Icons.Default.PrivacyTip,
            enabled = rootReady || state.preference.protectedFilesystemWritesEnabled,
            onCheckedChange = { enabled ->
                if (enabled) confirmProtectedWrites = true
                else actions.protectedWritesChange(false)
            }
        )

    }

    if (confirmProtectedWrites) {
        AlertDialog(
            onDismissRequest = { confirmProtectedWrites = false },
            icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_protected_writes_warning_title)) },
            text = { Text(stringResource(R.string.settings_protected_writes_warning_description)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmProtectedWrites = false
                        actions.protectedWritesChange(true)
                    }
                ) {
                    Text(stringResource(R.string.settings_protected_writes_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmProtectedWrites = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
