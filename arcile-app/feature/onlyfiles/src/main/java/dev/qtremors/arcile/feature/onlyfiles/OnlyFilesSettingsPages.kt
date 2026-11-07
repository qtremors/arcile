package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.ArcileScreenScaffold
import dev.qtremors.arcile.core.ui.ArcileSnackbarHost
import dev.qtremors.arcile.core.ui.arcileTopAppBarNestedScroll
import dev.qtremors.arcile.core.ui.arcileTopAppBarScrollBehavior
import dev.qtremors.arcile.core.ui.dialogs.AlertDialog
import dev.qtremors.arcile.core.ui.settings.SettingsSection
import dev.qtremors.arcile.core.ui.theme.expressiveSegmentedShapes
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultLocationKind
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.ui.R as CoreR

/**
 * Full-screen OnlyFiles settings. Stands in for the vault browser while open, so it also draws
 * the dialogs its actions lead to: health results, backups, the destination picker, and the
 * security disclosure.
 */
@Composable
internal fun OnlyFilesSettingsHost(
    page: OnlyFilesSettingsPage,
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    snackbarHost: SnackbarHostState,
    severity: () -> ArcileFeedbackSeverity,
    modifier: Modifier = Modifier
) {
    val vault = state.selectedVault
    if (page == OnlyFilesSettingsPage.VAULT && vault == null) {
        // The vault locked (timeout, screen off, storage loss) while its settings were open.
        LaunchedEffect(Unit) { viewModel.closeSettings() }
        return
    }
    LaunchedEffect(page) { viewModel.refreshSettings() }
    // Which vault dialog is open is screen state only; it never outlives this page.
    var vaultDialog by remember(page, vault?.id) { mutableStateOf<VaultSettingDialog?>(null) }
    OnlyFilesSettingsScaffold(
        title = if (vault != null && page == OnlyFilesSettingsPage.VAULT) vault.name
        else stringResource(CoreR.string.onlyfiles_settings_section),
        onBack = viewModel::closeSettings,
        snackbarHost = snackbarHost,
        severity = severity,
        modifier = modifier
    ) {
        if (vault != null && page == OnlyFilesSettingsPage.VAULT) vaultSettingsItems(vault, state, viewModel) { vaultDialog = it }
        else appSettingsItems(state, viewModel)
    }
    if (vault != null && page == OnlyFilesSettingsPage.VAULT) {
        VaultSettingsDialogs(vault, state, viewModel, vaultDialog) { vaultDialog = it }
    }
    state.healthReport?.let {
        HealthDialog(it, state.busy, viewModel::dismissHealthReport, viewModel::recoverVaultTransactions)
    }
    OnlyFilesBackupDialogs(state, viewModel)
    if (state.showSecurityDisclosure) OnlyFilesSecurityDisclosureDialog(onDismiss = viewModel::closeSecurityDisclosure)
    OnlyFilesPickerDialogs(state, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlyFilesSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHost: SnackbarHostState,
    severity: () -> ArcileFeedbackSeverity,
    modifier: Modifier,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ArcileScreenScaffold(
        modifier = modifier.arcileTopAppBarNestedScroll(scrollBehavior),
        snackbarHost = { ArcileSnackbarHost(snackbarHost, severityFor = { severity() }) },
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = arcileTopAppBarScrollBehavior(scrollBehavior),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(CoreR.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                    MaterialTheme.spacing.screenGutter
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content
        )
    }
}

private fun LazyListScope.appSettingsItems(state: OnlyFilesUiState, viewModel: OnlyFilesViewModel) {
    item(key = "locking") {
        var chooseDelay by remember { mutableStateOf(false) }
        SettingsSection(stringResource(R.string.onlyfiles_settings_locking)) {
            SettingsActionRow(
                0, 2, stringResource(R.string.onlyfiles_background_lock),
                stringResource(state.backgroundLockDelay.label()), Icons.Default.Timer
            ) { chooseDelay = true }
            SettingsToggleRow(
                1, 2, stringResource(R.string.onlyfiles_lock_screen_off),
                stringResource(R.string.onlyfiles_lock_screen_off_description), Icons.Default.ScreenLockPortrait,
                state.lockOnScreenOff, viewModel::setLockOnScreenOff
            )
        }
        if (chooseDelay) BackgroundLockDelayDialog(
            current = state.backgroundLockDelay,
            onSelect = { chooseDelay = false; viewModel.setBackgroundLockDelay(it) },
            onDismiss = { chooseDelay = false }
        )
    }
    item(key = "password") {
        SettingsSection(stringResource(R.string.onlyfiles_settings_password_entry)) {
            SettingsToggleRow(
                0, 1, stringResource(R.string.onlyfiles_numeric_keyboard),
                stringResource(R.string.onlyfiles_numeric_keyboard_description), Icons.Default.Dialpad,
                state.numericPasswordKeyboard, viewModel::setNumericPasswordKeyboard
            )
        }
    }
    item(key = "privacy") {
        SettingsSection(stringResource(R.string.onlyfiles_settings_privacy)) {
            SettingsToggleRow(
                0, 3, stringResource(CoreR.string.onlyfiles_screenshot_protection),
                stringResource(CoreR.string.onlyfiles_screenshot_protection_description), Icons.Default.Screenshot,
                state.screenshotProtectionEnabled, viewModel::setScreenshotProtection
            )
            SettingsActionRow(
                1, 3, stringResource(CoreR.string.onlyfiles_active_external_access),
                pluralStringResource(
                    CoreR.plurals.onlyfiles_active_external_access_count,
                    state.activeExternalGrants, state.activeExternalGrants
                ),
                Icons.AutoMirrored.Filled.OpenInNew,
                enabled = state.activeExternalGrants > 0,
                trailing = {
                    FilledTonalButton(
                        onClick = viewModel::revokeAllVaultExternalAccess,
                        enabled = state.activeExternalGrants > 0
                    ) { Text(stringResource(CoreR.string.onlyfiles_revoke_all)) }
                },
                onClick = viewModel::revokeAllVaultExternalAccess
            )
            SettingsActionRow(
                2, 3, stringResource(CoreR.string.onlyfiles_security_disclosure_title),
                stringResource(CoreR.string.onlyfiles_unaudited_short), Icons.Default.Info,
                onClick = viewModel::openSecurityDisclosure
            )
        }
    }
    item(key = "storage") {
        SettingsSection(stringResource(R.string.onlyfiles_settings_storage)) {
            SettingsActionRow(
                0, 1, stringResource(CoreR.string.onlyfiles_encrypted_thumbnail_cache),
                pluralStringResource(
                    CoreR.plurals.onlyfiles_encrypted_thumbnail_cache_stats,
                    state.encryptedThumbnailFiles, state.encryptedThumbnailFiles,
                    formatBytes(LocalContext.current, state.encryptedThumbnailBytes)
                ),
                Icons.Default.Image,
                enabled = !state.isSettingsBusy,
                trailing = {
                    FilledTonalButton(onClick = viewModel::clearVaultThumbnails, enabled = !state.isSettingsBusy) {
                        Text(stringResource(CoreR.string.settings_clear_thumbnail_cache))
                    }
                },
                onClick = viewModel::clearVaultThumbnails
            )
        }
    }
}

private fun LazyListScope.vaultSettingsItems(
    vault: VaultSummary,
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    showDialog: (VaultSettingDialog) -> Unit
) {
    item(key = "security") {
        val enrolled = vault.id in state.biometricVaultIds
        SettingsSection(stringResource(R.string.onlyfiles_settings_security)) {
            SettingsActionRow(
                0, 2, stringResource(R.string.onlyfiles_change_password),
                stringResource(R.string.onlyfiles_change_password_description), Icons.Default.Key
            ) { showDialog(VaultSettingDialog.CHANGE_PASSWORD) }
            SettingsToggleRow(
                1, 2, stringResource(R.string.onlyfiles_fingerprint_unlock),
                stringResource(
                    if (enrolled) R.string.onlyfiles_fingerprint_unlock_on else R.string.onlyfiles_fingerprint_unlock_off
                ),
                Icons.Default.Fingerprint, enrolled
            ) { enable ->
                showDialog(
                    if (enable) VaultSettingDialog.ENROLL_BIOMETRIC else VaultSettingDialog.REMOVE_BIOMETRIC
                )
            }
        }
    }
    item(key = "backup") {
        SettingsSection(stringResource(R.string.onlyfiles_settings_backup)) {
            SettingsActionRow(
                0, 2, stringResource(R.string.onlyfiles_create_backup),
                stringResource(R.string.onlyfiles_create_backup_description), Icons.Default.Backup,
                onClick = viewModel::beginVaultBackup
            )
            SettingsActionRow(
                1, 2, stringResource(R.string.onlyfiles_vault_external_access),
                stringResource(R.string.onlyfiles_vault_external_access_description), Icons.AutoMirrored.Filled.OpenInNew
            ) { showDialog(VaultSettingDialog.EXTERNAL_ACCESS) }
        }
    }
    item(key = "storage") {
        SettingsSection(stringResource(R.string.onlyfiles_settings_storage)) {
            SettingsActionRow(
                0, 2, stringResource(R.string.onlyfiles_vault_storage_usage),
                stringResource(R.string.onlyfiles_vault_storage_usage_description), Icons.Default.PieChart
            ) { showDialog(VaultSettingDialog.STORAGE_USAGE) }
            SettingsActionRow(
                1, 2, stringResource(R.string.onlyfiles_reset_vault_folder_views),
                stringResource(R.string.onlyfiles_reset_vault_folder_views_description), Icons.Default.RestartAlt
            ) { showDialog(VaultSettingDialog.RESET_VIEWS) }
        }
    }
    item(key = "health") {
        SettingsSection(stringResource(R.string.onlyfiles_settings_health)) {
            SettingsActionRow(
                0, 2, stringResource(R.string.onlyfiles_quick_health_check),
                stringResource(R.string.onlyfiles_quick_health_check_description), Icons.Default.HealthAndSafety,
                enabled = !state.busy
            ) { viewModel.verifyHealth(VaultHealthMode.QUICK) }
            SettingsActionRow(
                1, 2, stringResource(R.string.onlyfiles_full_health_check),
                stringResource(R.string.onlyfiles_full_health_check_description), Icons.Default.VerifiedUser,
                enabled = !state.busy
            ) { viewModel.verifyHealth(VaultHealthMode.FULL) }
        }
    }
    item(key = "danger") {
        val removable = vault.locationKind != VaultLocationKind.APP_PRIVATE
        val count = if (removable) 2 else 1
        SettingsSection(stringResource(R.string.onlyfiles_settings_remove)) {
            if (removable) SettingsActionRow(
                0, count, stringResource(R.string.onlyfiles_remove_registration),
                stringResource(R.string.onlyfiles_remove_registration_description), Icons.Default.RemoveCircleOutline
            ) { showDialog(VaultSettingDialog.REMOVE_REGISTRATION) }
            SettingsActionRow(
                count - 1, count, stringResource(R.string.onlyfiles_delete_vault),
                stringResource(R.string.onlyfiles_delete_vault_description), Icons.Default.DeleteForever,
                destructive = true
            ) { showDialog(VaultSettingDialog.DELETE) }
        }
    }
    item(key = "app") {
        SettingsSection(stringResource(CoreR.string.onlyfiles_settings_section), showTitle = false) {
            SettingsActionRow(
                0, 1, stringResource(CoreR.string.onlyfiles_settings_section),
                stringResource(R.string.onlyfiles_settings_app_description), Icons.Default.Settings
            ) { viewModel.openSettings(OnlyFilesSettingsPage.APP) }
        }
    }
}

/** Confirmation and entry dialogs opened from the vault settings page. */
internal enum class VaultSettingDialog {
    CHANGE_PASSWORD, ENROLL_BIOMETRIC, REMOVE_BIOMETRIC, EXTERNAL_ACCESS, STORAGE_USAGE,
    RESET_VIEWS, REMOVE_REGISTRATION, DELETE
}

@Composable
private fun VaultSettingsDialogs(
    vault: VaultSummary,
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    dialog: VaultSettingDialog?,
    onDialog: (VaultSettingDialog?) -> Unit
) {
    val context = LocalContext.current
    val close = { onDialog(null) }
    when (dialog) {
        VaultSettingDialog.CHANGE_PASSWORD -> ChangeVaultPasswordDialog(close) { current, replacement, confirmed ->
            close()
            viewModel.changePassword(current, replacement, confirmed)
        }
        VaultSettingDialog.ENROLL_BIOMETRIC -> PasswordDialog(
            stringResource(R.string.onlyfiles_enable_biometric), stringResource(R.string.onlyfiles_continue), close
        ) { password ->
            close()
            viewModel.prepareBiometricEnrollment(password) { challenge ->
                val activity = context.findActivity() ?: run { challenge.close(); return@prepareBiometricEnrollment }
                showBiometricPrompt(activity, challenge) { if (it.isSuccess) viewModel.biometricCompleted(challenge.vaultId) }
            }
        }
        VaultSettingDialog.REMOVE_BIOMETRIC -> ConfirmationDialog(
            stringResource(R.string.onlyfiles_remove_biometric), stringResource(R.string.onlyfiles_remove_biometric_confirm), close
        ) { close(); viewModel.removeBiometric() }
        VaultSettingDialog.EXTERNAL_ACCESS -> VaultExternalGrantsDialog(vault.id, state.mountGeneration, viewModel, close)
        VaultSettingDialog.STORAGE_USAGE -> VaultStorageUsageDialog(vault.id, viewModel::vaultStorageUsage, close)
        VaultSettingDialog.RESET_VIEWS -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.onlyfiles_reset_vault_folder_views)) },
            text = { Text(stringResource(R.string.onlyfiles_reset_vault_folder_views_message)) },
            confirmButton = {
                Button(onClick = { close(); viewModel.resetVaultFolderViews() }) {
                    Text(stringResource(R.string.onlyfiles_reset_vault_folder_views))
                }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.onlyfiles_cancel)) } }
        )
        VaultSettingDialog.REMOVE_REGISTRATION -> ConfirmationDialog(
            stringResource(R.string.onlyfiles_remove_registration),
            stringResource(R.string.onlyfiles_remove_registration_confirm), close
        ) { close(); viewModel.removeRegistration() }
        VaultSettingDialog.DELETE -> DeleteVaultDialog(vault.name, close) { close(); viewModel.deleteVault(it) }
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsActionRow(
    index: Int,
    count: Int,
    title: String,
    description: String?,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = expressiveSegmentedShapes(index = index, count = count),
        leadingContent = { CenteredIcon(icon, accent) },
        content = {
            Text(title, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = trailing?.let { { Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { it() } } },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.height(IntrinsicSize.Min)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsToggleRow(
    index: Int,
    count: Int,
    title: String,
    description: String?,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = expressiveSegmentedShapes(index = index, count = count),
        leadingContent = { CenteredIcon(icon, MaterialTheme.colorScheme.primary) },
        content = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.height(IntrinsicSize.Min)
    )
}

@Composable
private fun CenteredIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}
