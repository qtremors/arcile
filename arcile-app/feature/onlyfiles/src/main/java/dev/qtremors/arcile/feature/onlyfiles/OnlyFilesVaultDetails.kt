package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultStorageUsage
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dev.qtremors.arcile.core.ui.R as UiR

@Composable
internal fun VaultStorageUsageDialog(
    vault: VaultId,
    loadUsage: suspend (VaultId) -> Result<VaultStorageUsage>,
    onDismiss: () -> Unit
) {
    var result by remember(vault) { mutableStateOf<Result<VaultStorageUsage>?>(null) }
    LaunchedEffect(vault) { result = loadUsage(vault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_vault_storage_usage)) },
        text = {
            val usage = result?.getOrNull()
            when {
                usage != null -> VaultStorageUsageContent(usage)
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Text(stringResource(R.string.onlyfiles_vault_storage_unavailable))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_close)) } }
    )
}

@Composable
private fun VaultStorageUsageContent(usage: VaultStorageUsage) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val bytes = usage.storedBytes
        val formatted = if (bytes == null) stringResource(R.string.onlyfiles_vault_storage_unavailable)
        else formatBytes(context, bytes)
        Text(
            stringResource(
                if (usage.storedBytesPartial) R.string.onlyfiles_vault_storage_at_least else R.string.onlyfiles_vault_storage_bytes,
                formatted
            ),
            style = MaterialTheme.typography.titleSmall
        )
        if (usage.folderCount > 0) {
            Text(stringResource(R.string.onlyfiles_vault_storage_folder_sizes))
            if (usage.foldersMeasured < usage.folderCount) {
                Text(
                    stringResource(R.string.onlyfiles_vault_storage_partial, usage.foldersMeasured, usage.folderCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            LazyColumn(Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(usage.largestFolders, key = { it.id.value }) { folder ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(folder.name, Modifier.weight(1f))
                        Text(formatBytes(context, folder.totalBytes))
                    }
                }
            }
        }
    }
}

@Composable
internal fun VaultExternalGrantsDialog(
    vault: VaultId,
    mount: Long,
    viewModel: OnlyFilesViewModel,
    onDismiss: () -> Unit
) {
    var grants by remember(vault, mount) { mutableStateOf<List<VaultExternalGrant>?>(null) }
    var revoking by remember(vault, mount) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    LaunchedEffect(vault, mount) {
        while (isActive) {
            val refreshed = viewModel.vaultExternalGrants(vault, mount)
            if (revoking == null) grants = refreshed
            delay(2_000L)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onlyfiles_vault_external_access)) },
        text = {
            val active = grants
            when {
                active == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                active.isEmpty() -> Text(stringResource(R.string.onlyfiles_vault_no_grants))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(active, key = VaultExternalGrant::token) { grant ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(grant.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(
                                        R.string.onlyfiles_vault_grant_expires, formatter.format(Date(grant.expiresAtMillis))
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                enabled = revoking == null,
                                onClick = {
                                    revoking = grant.token
                                    scope.launch {
                                        try {
                                            viewModel.revokeVaultExternalGrant(vault, mount, grant.token)
                                            grants = viewModel.vaultExternalGrants(vault, mount)
                                        } finally {
                                            revoking = null
                                        }
                                    }
                                }
                            ) { Text(stringResource(R.string.onlyfiles_vault_revoke_grant)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_close)) } }
    )
}
