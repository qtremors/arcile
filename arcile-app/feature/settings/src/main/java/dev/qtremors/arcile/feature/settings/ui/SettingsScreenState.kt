package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.theme.UiPreferences
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.feature.settings.SettingsPreferences

internal data class SettingsScreenState(
    val theme: UiPreferences,
    val preferences: SettingsPreferences,
    val backup: PreferencesBackupUiState,
    val externalCache: SettingsExternalCacheState = SettingsExternalCacheState(),
    val storageVolumes: List<StorageVolume>? = null
)

internal data class SettingsExternalCacheState(
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val isBusy: Boolean = true
)
