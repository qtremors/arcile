package dev.qtremors.arcile.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.feature.settings.ui.SettingsBackupActions
import dev.qtremors.arcile.feature.settings.ui.SettingsBackupDialogs
import dev.qtremors.arcile.feature.settings.ui.SettingsNavigationActions
import dev.qtremors.arcile.feature.settings.ui.SettingsPreferenceActions
import dev.qtremors.arcile.feature.settings.ui.SettingsScreen
import dev.qtremors.arcile.feature.settings.ui.SettingsScreenState
import dev.qtremors.arcile.feature.settings.ui.SettingsStorageActions

@Composable
internal fun SettingsRoute(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onNavigateBack: () -> Unit,
    onDestination: (SettingsDestination) -> Unit,
    onRestartApp: () -> Unit
) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val preferences by viewModel.browserPreferences.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val externalCache by viewModel.externalCache.collectAsStateWithLifecycle()

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportPreferences(uri)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.previewRestore(uri)
    }

    SettingsBackupDialogs(
        state = backupState,
        onApplyRestore = viewModel::restorePreferences,
        onClear = viewModel::clearBackupState,
        onRestart = onRestartApp
    )

    SettingsScreen(
        state = SettingsScreenState(
            theme = currentThemeState,
            preferences = preferences,
            backup = backupState,
            externalCache = externalCache
        ),
        navigationActions = SettingsNavigationActions(
            navigateBack = onNavigateBack,
            openStorageManagement = { onDestination(SettingsDestination.StorageManagement) },
            navigateToPlugins = { onDestination(SettingsDestination.Plugins) },
            navigateToOnlyFiles = { onDestination(SettingsDestination.OnlyFiles) },
            navigateToAbout = { onDestination(SettingsDestination.About) }
        ),
        preferenceActions = SettingsPreferenceActions(
            themeChange = onThemeChange,
            showThumbnailsChange = viewModel::updateShowThumbnails,
            homeRecentCarouselLimitChange = viewModel::updateHomeRecentCarouselLimit,
            showHiddenFilesChange = viewModel::updateShowHiddenFiles,
            browserScrollbarEnabledChange = viewModel::updateBrowserScrollbarEnabled,
            galleryScrollbarEnabledChange = viewModel::updateGalleryScrollbarEnabled
        ),
        backupActions = SettingsBackupActions(
            requestExport = { exportBackupLauncher.launch("arcile-settings-backup.json") },
            requestRestore = {
                restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
        ),
        storageActions = SettingsStorageActions(
            clearExternalCache = viewModel::clearExternalCache
        )
    )
}
