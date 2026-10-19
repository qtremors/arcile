package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata

@Composable
internal fun VaultPackageInstaller(
    node: VaultNodeMetadata,
    viewModel: OnlyFilesViewModel,
    packageInstaller: @Composable (VaultExternalGrant, () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val request = remember { VaultPackageAccess() }
    DisposableEffect(request) {
        onDispose {
            request.closed = true
            request.grant?.let { viewModel.revokeExternalAccess(listOf(it)) }
        }
    }
    val grant = request.grant
    if (grant != null) {
        packageInstaller(grant, onDismiss)
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.onlyfiles_open_installer)) },
            text = {
                if (request.pending) LinearProgressIndicator()
                else Text(stringResource(R.string.onlyfiles_install_vault_package_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        request.pending = true
                        viewModel.issuePackageAccess(node, onReady = { issued ->
                            if (request.closed) viewModel.revokeExternalAccess(listOf(issued))
                            else request.grant = issued
                        }, onFailure = {
                            if (!request.closed) request.pending = false
                        })
                    },
                    enabled = !request.pending
                ) { Text(stringResource(R.string.onlyfiles_continue)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onlyfiles_cancel)) } }
        )
    }
}

private class VaultPackageAccess {
    var closed = false
    var pending by mutableStateOf(false)
    var grant by mutableStateOf<VaultExternalGrant?>(null)
}
