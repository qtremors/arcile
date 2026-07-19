package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.feature.settings.SettingsPreferences
import dev.qtremors.arcile.feature.settings.VaultSecurityUiState

internal data class SettingsScreenState(
    val theme: ThemeState,
    val preferences: SettingsPreferences,
    val backup: PreferencesBackupUiState,
    val externalCache: SettingsExternalCacheState = SettingsExternalCacheState(),
    val vaultSecurity: VaultSecurityUiState = VaultSecurityUiState()
)

internal data class SettingsVaultActions(
    val setScreenshotProtection: (Boolean) -> Unit,
    val clearEncryptedThumbnails: () -> Unit,
    val revokeAllExternalAccess: () -> Unit,
    val showDisclosure: () -> Unit
)

internal data class SettingsExternalCacheState(
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val isBusy: Boolean = true
)
