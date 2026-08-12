package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.feature.settings.SettingsPreferences
import dev.qtremors.arcile.core.privilege.PrivilegePreferenceState
import dev.qtremors.arcile.core.privilege.PrivilegeState

internal data class SettingsScreenState(
    val theme: ThemeState,
    val preferences: SettingsPreferences,
    val backup: PreferencesBackupUiState,
    val access: SettingsAccessState = SettingsAccessState(),
    val externalCache: SettingsExternalCacheState = SettingsExternalCacheState()
)

internal data class SettingsAccessState(
    val preference: PrivilegePreferenceState = PrivilegePreferenceState(),
    val access: PrivilegeState = PrivilegeState(),
    val isBusy: Boolean = false,
    val actionFailure: UiText? = null
)

internal data class SettingsExternalCacheState(
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val isBusy: Boolean = true
)
