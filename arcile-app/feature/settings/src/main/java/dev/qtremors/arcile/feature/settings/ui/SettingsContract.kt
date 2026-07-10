package dev.qtremors.arcile.feature.settings.ui

import android.net.Uri
import dev.qtremors.arcile.feature.settings.PreferencesBackupUiState
import dev.qtremors.arcile.feature.settings.SettingsPreferences
import dev.qtremors.arcile.core.ui.theme.ThemeState

internal data class SettingsScreenState(
    val theme: ThemeState,
    val preferences: SettingsPreferences,
    val backup: PreferencesBackupUiState
)

internal data class SettingsNavigationActions(
    val navigateBack: () -> Unit,
    val openStorageManagement: () -> Unit,
    val navigateToPlugins: () -> Unit,
    val navigateToAbout: () -> Unit,
    val restartApp: () -> Unit
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
    val export: (Uri) -> Unit,
    val previewRestore: (Uri) -> Unit,
    val applyRestore: (Uri) -> Unit,
    val clearState: () -> Unit
)
