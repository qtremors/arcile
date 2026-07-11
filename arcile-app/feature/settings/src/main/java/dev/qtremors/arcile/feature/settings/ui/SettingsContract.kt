package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.feature.settings.SettingsPreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState

internal data class SettingsScreenState(
    val theme: ThemeState,
    val preferences: SettingsPreferences,
    val backup: PreferencesBackupUiState,
    val externalCache: SettingsExternalCacheState = SettingsExternalCacheState()
)

internal data class SettingsExternalCacheState(
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val isBusy: Boolean = true
)

internal data class SettingsNavigationActions(
    val navigateBack: () -> Unit,
    val openStorageManagement: () -> Unit,
    val navigateToPlugins: () -> Unit,
    val navigateToAbout: () -> Unit
)

internal data class SettingsPreferenceActions(
    val themeChange: (ThemeState) -> Unit,
    val showThumbnailsChange: (Boolean) -> Unit,
    val homeRecentCarouselLimitChange: (Int) -> Unit,
    val showHiddenFilesChange: (Boolean) -> Unit,
    val browserScrollbarEnabledChange: (Boolean) -> Unit,
    val galleryScrollbarEnabledChange: (Boolean) -> Unit
)

internal data class SettingsBackupActions(
    val requestExport: () -> Unit,
    val requestRestore: () -> Unit
)

internal data class SettingsStorageActions(
    val clearExternalCache: () -> Unit
)
