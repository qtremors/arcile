package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserAccessLossReason
import dev.qtremors.arcile.feature.browser.BrowserBackendAccessState

/**
 * Keeps the browser route composed while the selected privileged backend is unavailable.
 * Navigation, selection, scroll state, and the existing listing remain owned by the route behind
 * this surface until the user reconnects or explicitly chooses the Normal fallback.
 */
@Composable
internal fun BrowserBackendAccessSurface(
    state: BrowserBackendAccessState,
    onReconnect: () -> Unit,
    onUseNormal: () -> Unit,
    onGrantNormalAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state == BrowserBackendAccessState.Available) return
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                BrowserBackendAccessState.Available -> Unit
                is BrowserBackendAccessState.Reconnecting -> ReconnectingContent(
                    state = state,
                    onUseNormal = onUseNormal
                )
                is BrowserBackendAccessState.Lost -> AccessLostContent(
                    state = state,
                    onReconnect = onReconnect,
                    onUseNormal = onUseNormal,
                    onGrantNormalAccess = onGrantNormalAccess
                )
            }
        }
    }
}

@Composable
private fun ReconnectingContent(
    state: BrowserBackendAccessState.Reconnecting,
    onUseNormal: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.browser_access_reconnecting_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (state.normalFallbackPending) {
                    R.string.browser_access_waiting_for_normal
                } else {
                    state.backendId.reconnectingDescription()
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!state.normalFallbackPending && state.backendId != PrivilegeBackendId.NORMAL) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onUseNormal) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.storage_access_use_normal))
            }
        }
    }
}

@Composable
private fun AccessLostContent(
    state: BrowserBackendAccessState.Lost,
    onReconnect: () -> Unit,
    onUseNormal: () -> Unit,
    onGrantNormalAccess: () -> Unit
) {
    val needsNormalPermission = state.normalPermissionRequired &&
        (state.normalFallbackPending || state.backendId == PrivilegeBackendId.NORMAL)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(
                if (needsNormalPermission) {
                    R.string.browser_access_normal_required_title
                } else {
                    R.string.browser_access_lost_title
                }
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (needsNormalPermission) {
                    R.string.browser_access_normal_required_description
                } else {
                    state.reason.descriptionResource()
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(
                R.string.browser_access_context_preserved,
                state.backendId.displayName()
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        state.actionFailure?.localizedMessage?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        if (needsNormalPermission) {
            Button(
                onClick = onGrantNormalAccess,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Storage, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.browser_access_grant_normal))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (state.normalFallbackPending) {
                            R.string.browser_access_return_to_provider
                        } else {
                            R.string.storage_access_reconnect
                        }
                    )
                )
            }
        } else if (state.backendId == PrivilegeBackendId.NORMAL) {
            Button(
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.storage_access_reconnect))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReconnect,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_access_reconnect))
                }
                OutlinedButton(
                    onClick = onUseNormal,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_access_use_normal))
                }
            }
        }
    }
}

private fun PrivilegeBackendId.displayName(): String = when (this) {
    PrivilegeBackendId.ROOT -> "Root"
    PrivilegeBackendId.SHIZUKU -> "Shizuku"
    else -> "Normal"
}

private fun PrivilegeBackendId.reconnectingDescription(): Int = when (this) {
    PrivilegeBackendId.ROOT -> R.string.browser_access_reconnecting_root
    PrivilegeBackendId.SHIZUKU -> R.string.browser_access_reconnecting_shizuku
    else -> R.string.storage_access_connecting_description
}

private fun BrowserAccessLossReason.descriptionResource(): Int = when (this) {
    BrowserAccessLossReason.SERVICE_STOPPED -> R.string.browser_access_service_stopped
    BrowserAccessLossReason.PERMISSION_REQUIRED -> R.string.browser_access_permission_required
    BrowserAccessLossReason.PERMISSION_DENIED -> R.string.browser_access_permission_denied
    BrowserAccessLossReason.BACKEND_UNAVAILABLE -> R.string.browser_access_backend_unavailable
    BrowserAccessLossReason.BACKEND_INCOMPATIBLE -> R.string.browser_access_backend_incompatible
    BrowserAccessLossReason.BACKEND_CHANGED -> R.string.browser_access_backend_changed
    BrowserAccessLossReason.CONNECTION_FAILED -> R.string.browser_access_connection_failed
}
