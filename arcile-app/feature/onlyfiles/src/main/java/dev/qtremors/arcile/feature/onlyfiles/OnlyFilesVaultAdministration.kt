package dev.qtremors.arcile.feature.onlyfiles

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultPasswordPolicy
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ExpressiveSwitch
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle
import kotlinx.coroutines.launch

@Composable
internal fun VaultActionsMenu(
    expanded: Boolean,
    vault: VaultSummary,
    viewModel: OnlyFilesViewModel,
    onDismiss: () -> Unit
) {
    var changePassword by remember { mutableStateOf(false) }
    var enrollBiometric by remember { mutableStateOf(false) }
    var confirmRemoveBiometric by remember { mutableStateOf(false) }
    var confirmRemoveRegistration by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun closeAnd(action: () -> Unit) { onDismiss(); action() }

    val menuActions = remember(vault) {
        mutableListOf<@Composable () -> Unit>().apply {
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_quick_health_check)) },
                    leadingIcon = { Icon(Icons.Default.HealthAndSafety, null) },
                    onClick = { closeAnd { viewModel.verifyHealth(VaultHealthMode.QUICK) } }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_full_health_check)) },
                    leadingIcon = { Icon(Icons.Default.HealthAndSafety, null) },
                    onClick = { closeAnd { viewModel.verifyHealth(VaultHealthMode.FULL) } }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_change_password)) },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    onClick = { closeAnd { changePassword = true } }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_enable_biometric)) },
                    leadingIcon = { Icon(Icons.Default.Fingerprint, null) },
                    onClick = { closeAnd { enrollBiometric = true } }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_remove_biometric)) },
                    leadingIcon = { Icon(Icons.Default.Fingerprint, null) },
                    onClick = { closeAnd { confirmRemoveBiometric = true } }
                )
            }
            if (vault.locationKind != VaultLocationKind.APP_PRIVATE) {
                add {
                    ArcileDropdownMenuItem(
                        text = { Text(stringResource(R.string.onlyfiles_remove_registration)) },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
                        onClick = { closeAnd { confirmRemoveRegistration = true } }
                    )
                }
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_delete_vault)) },
                    leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                    onClick = { closeAnd { confirmDelete = true } }
                )
            }
            add {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_lock)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    onClick = { closeAnd(viewModel::lockCurrent) }
                )
            }
        }
    }

    DropdownMenu(
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        menuActions.forEachIndexed { index, action ->
            val shape = when {
                menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                index == 0 -> MaterialTheme.shapes.menuGroupFirst
                index == menuActions.size - 1 -> MaterialTheme.shapes.menuGroupLast
                else -> MaterialTheme.shapes.menuGroupMiddle
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                action()
            }
        }
    }

    if (changePassword) ChangeVaultPasswordDialog(
        { changePassword = false }
    ) { current, replacement, confirmed ->
        changePassword = false
        viewModel.changePassword(current, replacement, confirmed)
    }
    if (enrollBiometric) PasswordDialog(
        stringResource(R.string.onlyfiles_enable_biometric), stringResource(R.string.onlyfiles_continue),
        { enrollBiometric = false }
    ) { password ->
        enrollBiometric = false
        viewModel.prepareBiometricEnrollment(password) { challenge ->
            val activity = context.findActivity() ?: run { challenge.close(); return@prepareBiometricEnrollment }
            showBiometricPrompt(activity, challenge) { if (it.isSuccess) viewModel.biometricCompleted(challenge.vaultId) }
        }
    }
    if (confirmRemoveBiometric) ConfirmationDialog(
        stringResource(R.string.onlyfiles_remove_biometric), stringResource(R.string.onlyfiles_remove_biometric_confirm),
        { confirmRemoveBiometric = false }
    ) { confirmRemoveBiometric = false; viewModel.removeBiometric() }
    if (confirmRemoveRegistration) ConfirmationDialog(
        stringResource(R.string.onlyfiles_remove_registration), stringResource(R.string.onlyfiles_remove_registration_confirm),
        { confirmRemoveRegistration = false }
    ) { confirmRemoveRegistration = false; viewModel.removeRegistration() }
    if (confirmDelete) DeleteVaultDialog(vault.name, { confirmDelete = false }) {
        confirmDelete = false; viewModel.deleteVault(it)
    }
}

@Composable
internal fun VaultUnlockDialog(vault: VaultSummary, viewModel: OnlyFilesViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    val biometricEnrolled = remember(vault.id) {
        val root = java.io.File(context.noBackupFilesDir, "onlyfiles-biometric")
        java.io.File(root, "${vault.id.value}.bio").exists()
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(vault.name) },
        text = { PasswordField(password, { password = it }, false, {}) },
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
private fun ChangeVaultPasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String, Boolean) -> Unit) {
    var current by remember { mutableStateOf("") }; var replacement by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }; var weakConfirmed by remember { mutableStateOf(false) }
    val weak = replacement.isNotEmpty() && VaultPasswordPolicy.isWeak(replacement.toCharArray())
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.onlyfiles_change_password)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it },
                label = { Text(stringResource(R.string.onlyfiles_current_password)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                label = { Text(stringResource(R.string.onlyfiles_new_password)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = repeated,
                onValueChange = { repeated = it },
                label = { Text(stringResource(R.string.onlyfiles_repeat_password)) },
                shape = ExpressiveShapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            if (weak) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .bounceClickable { weakConfirmed = !weakConfirmed }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.onlyfiles_weak_password_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveSwitch(checked = weakConfirmed, onCheckedChange = { weakConfirmed = it })
                }
            }
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
private fun DeleteVaultDialog(name: String, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
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
private fun ConfirmationDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
                val completed = challenge.completeAfterAuthentication()
                challenge.close()
                onComplete(completed)
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
