package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
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
import dev.qtremors.arcile.core.privilege.PrivilegeBackendState
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAccessSection(
    state: SettingsAccessState,
    actions: SettingsAccessActions
) {
    var confirmProtectedWrites by remember { mutableStateOf(false) }
    val preferredMode = state.preference.mode
    val access = state.access
    val activeName = access.activeBackend?.let { backendDisplayName(it) }

    SettingsSection(title = stringResource(R.string.settings_access_provider_title)) {
        Text(
            text = if (activeName != null) {
                stringResource(R.string.settings_access_active_provider, activeName)
            } else {
                stringResource(R.string.settings_access_no_active_provider)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        PrivilegeMode.entries.forEachIndexed { index, mode ->
            val backend = access.backendFor(mode)
            SettingsAccessModeRow(
                mode = mode,
                selected = preferredMode == mode,
                index = index,
                backend = backend,
                effectiveUid = if (preferredMode == mode) access.identity?.effectiveUid else null,
                enabled = !state.isBusy,
                onClick = { actions.selectMode(mode) }
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
            if (preferredMode != PrivilegeMode.NORMAL && !access.isReady) {
                Button(
                    onClick = actions.reconnect,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("storage_access_reconnect")
                ) {
                    Text(stringResource(R.string.storage_access_reconnect))
                }
            }
            if (
                preferredMode == PrivilegeMode.NORMAL &&
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
            if (preferredMode == PrivilegeMode.SHIZUKU) {
                FilledTonalButton(
                    onClick = actions.openShizukuManager,
                    modifier = Modifier.testTag("storage_access_open_shizuku")
                ) {
                    Text(stringResource(R.string.settings_access_open_shizuku))
                }
            }
            if (preferredMode != PrivilegeMode.NORMAL) {
                OutlinedButton(
                    onClick = actions.useNormal,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("storage_access_use_normal")
                ) {
                    Text(stringResource(R.string.storage_access_use_normal))
                }
            }
        }

        val rootReady = access.isReady && access.identity?.isRoot == true
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

        Text(
            text = stringResource(R.string.settings_access_scope_explanation),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsAccessModeRow(
    mode: PrivilegeMode,
    selected: Boolean,
    index: Int,
    backend: PrivilegeBackendState?,
    effectiveUid: Int?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = expressiveSegmentedShapes(index, PrivilegeMode.entries.size),
        colors = ListItemDefaults.segmentedColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier
            .testTag("storage_access_mode_${mode.name.lowercase()}")
            .height(IntrinsicSize.Min),
        leadingContent = {
            Icon(
                imageVector = when (mode) {
                    PrivilegeMode.AUTOMATIC -> Icons.Default.Settings
                    PrivilegeMode.ROOT -> Icons.Default.Android
                    PrivilegeMode.SHIZUKU -> Icons.Default.Bolt
                    PrivilegeMode.NORMAL -> Icons.Default.Storage
                },
                contentDescription = null
            )
        },
        content = { Text(stringResource(mode.titleResource())) },
        supportingContent = {
            Column {
                Text(stringResource(mode.descriptionResource()))
                Text(
                    text = accessStatus(mode, backend?.connectionState, effectiveUid),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        trailingContent = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        }
    )
}

private fun dev.qtremors.arcile.core.privilege.PrivilegeState.backendFor(
    mode: PrivilegeMode
): PrivilegeBackendState? = when (mode) {
    PrivilegeMode.AUTOMATIC -> activeBackend?.let(backendStates::get)
    PrivilegeMode.ROOT -> backendStates[PrivilegeBackendId.ROOT]
    PrivilegeMode.SHIZUKU -> backendStates[PrivilegeBackendId.SHIZUKU]
    PrivilegeMode.NORMAL -> backendStates[PrivilegeBackendId.NORMAL]
}

@Composable
private fun backendDisplayName(backendId: PrivilegeBackendId): String = when (backendId) {
    PrivilegeBackendId.ROOT -> stringResource(R.string.storage_access_mode_root)
    PrivilegeBackendId.SHIZUKU -> stringResource(R.string.storage_access_mode_shizuku)
    PrivilegeBackendId.NORMAL -> stringResource(R.string.storage_access_mode_normal)
    else -> backendId.value
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
private fun accessStatus(
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
