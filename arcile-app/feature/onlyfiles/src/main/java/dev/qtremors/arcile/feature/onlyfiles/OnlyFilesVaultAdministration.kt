package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileMenuQuickAction
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import androidx.compose.material.icons.filled.Tab
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import kotlinx.coroutines.launch

/**
 * The open vault's overflow menu. The list holds actions for the current folder and anything
 * that needs attention; the icon row at the bottom holds the everyday toggles. Vault
 * administration lives on the vault settings page.
 */
@Composable
internal fun VaultActionsMenu(
    expanded: Boolean,
    viewModel: OnlyFilesViewModel,
    hiddenFilesShown: Boolean,
    hasExactFolderView: Boolean,
    workspaceTabsEnabled: Boolean,
    hasExportRetry: Boolean,
    onDismiss: () -> Unit
) {
    fun closeAnd(action: () -> Unit) { onDismiss(); action() }
    val items = buildList<@Composable () -> Unit> {
        if (hasExportRetry) add {
            ArcileDropdownMenuItem(
                text = { Text(stringResource(R.string.onlyfiles_resume_partial_export)) },
                leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                onClick = { closeAnd(viewModel::resumeLocalExport) }
            )
        }
        add {
            ArcileDropdownMenuItem(
                text = { Text(stringResource(R.string.onlyfiles_interrupted_transfers)) },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                onClick = { closeAnd(viewModel::openRecovery) }
            )
        }
        if (hasExactFolderView) add {
            ArcileDropdownMenuItem(
                text = { Text(stringResource(R.string.onlyfiles_reset_folder_view)) },
                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                onClick = { closeAnd(viewModel::resetFolderView) }
            )
        }
        add {
            ArcileDropdownMenuItem(
                text = { Text(stringResource(R.string.onlyfiles_vault_settings)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = { closeAnd { viewModel.openSettings(OnlyFilesSettingsPage.VAULT) } }
            )
        }
    }
    val quickActions = listOf(
        ArcileMenuQuickAction(Icons.Default.Lock, stringResource(R.string.onlyfiles_lock)) {
            closeAnd(viewModel::lockCurrent)
        },
        ArcileMenuQuickAction(
            if (hiddenFilesShown) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            stringResource(dev.qtremors.arcile.core.ui.R.string.settings_show_hidden_files),
            selected = hiddenFilesShown
        ) { closeAnd(viewModel::toggleHiddenFiles) },
        ArcileMenuQuickAction(
            Icons.Default.Tab, stringResource(R.string.onlyfiles_workspace_tabs), selected = workspaceTabsEnabled
        ) { closeAnd { viewModel.setWorkspaceTabsEnabled(!workspaceTabsEnabled) } }
    )
    ArcileDropdownMenu(expanded = expanded, onDismissRequest = onDismiss, quickActions = quickActions, items = items)
}

@Composable
internal fun VaultUnlockDialog(
    vault: VaultSummary,
    biometricEnrolled: Boolean,
    viewModel: OnlyFilesViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(vault.name) },
        text = { PasswordField(password, { password = it }, reveal, { reveal = !reveal }) },
        confirmButton = {
            Button(
                onClick = { onDismiss(); viewModel.unlock(vault.id, password) },
                enabled = password.isNotEmpty(),
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = password.isNotEmpty()) {
                    onDismiss()
                    viewModel.unlock(vault.id, password)
                }
            ) {
                Text(stringResource(R.string.onlyfiles_unlock))
            }
        },
        dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (biometricEnrolled) {
                TextButton(
                    onClick = {
                        viewModel.prepareBiometricUnlock(vault.id) { challenge ->
                            val activity = context.findActivity() ?: run { challenge.close(); return@prepareBiometricUnlock }
                            showBiometricPrompt(activity, challenge) { result ->
                                if (result.isSuccess) { onDismiss(); viewModel.biometricCompleted(vault.id) }
                            }
                        }
                    },
                    shape = ExpressiveShapes.medium,
                    modifier = Modifier.bounceClickable {
                        viewModel.prepareBiometricUnlock(vault.id) { challenge ->
                            val activity = context.findActivity() ?: run { challenge.close(); return@prepareBiometricUnlock }
                            showBiometricPrompt(activity, challenge) { result ->
                                if (result.isSuccess) { onDismiss(); viewModel.biometricCompleted(vault.id) }
                            }
                        }
                    }
                ) { Text(stringResource(R.string.onlyfiles_use_biometric)) }
            }
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        } }
    )
}

@Composable
internal fun ChangeVaultPasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String, Boolean) -> Unit) {
    var current by remember { mutableStateOf("") }; var replacement by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf("") }; var weakConfirmed by remember { mutableStateOf(false) }
    val weak = rememberPasswordWeak(replacement)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_change_password)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PasswordField(current, { current = it }, reveal, { reveal = !reveal }, label = R.string.onlyfiles_current_password)
            PasswordField(replacement, { replacement = it }, reveal, { reveal = !reveal }, label = R.string.onlyfiles_new_password, showKeyboardSwitch = false)
            PasswordField(repeated, { repeated = it }, reveal, { reveal = !reveal }, label = R.string.onlyfiles_repeat_password, showKeyboardSwitch = false)
            if (weak) ShortPasswordConsent(weakConfirmed) { weakConfirmed = it }
        } },
        confirmButton = {
            val enabled = current.isNotEmpty() && replacement.isNotEmpty() && replacement == repeated && (!weak || weakConfirmed)
            Button(
                onClick = { onSubmit(current, replacement, weakConfirmed) },
                enabled = enabled,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = enabled) { onSubmit(current, replacement, weakConfirmed) }
            ) { Text(stringResource(R.string.onlyfiles_change_password)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

@Composable
internal fun DeleteVaultDialog(name: String, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_delete_vault)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.onlyfiles_delete_vault_confirm, name))
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(name) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        } },
        confirmButton = {
            val enabled = confirmation == name
            Button(
                onClick = { onDelete(confirmation) },
                enabled = enabled,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(enabled = enabled) { onDelete(confirmation) }
            ) { Text(stringResource(R.string.onlyfiles_delete)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

@Composable
internal fun ConfirmationDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable { onConfirm() }
            ) { Text(stringResource(R.string.onlyfiles_continue)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.bounceClickable(onClick = onDismiss)
            ) { Text(stringResource(R.string.onlyfiles_cancel)) }
        }
    )
}

internal fun showBiometricPrompt(activity: Activity, challenge: VaultBiometricChallenge, onComplete: (Result<Unit>) -> Unit) {
    val crypto = challenge.platformCryptoObject as? BiometricPrompt.CryptoObject
    if (crypto == null) {
        challenge.close(); onComplete(Result.failure(IllegalStateException("Biometric challenge is unavailable"))); return
    }
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle(activity.getString(R.string.onlyfiles_biometric_prompt))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButton(activity.getString(R.string.onlyfiles_cancel), activity.mainExecutor) { _, _ -> challenge.close() }
        .build()
    prompt.authenticate(crypto, CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            (activity as? ComponentActivity)?.lifecycleScope?.launch {
                try {
                    onComplete(challenge.completeAfterAuthentication())
                } finally {
                    challenge.close()
                }
            } ?: challenge.close()
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            challenge.close()
            onComplete(Result.failure(IllegalStateException(errString?.toString() ?: "Biometric authentication failed")))
        }
    })
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
