package dev.qtremors.arcile.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dev.qtremors.arcile.core.plugin.android.PluginManager
import dev.qtremors.arcile.plugin.api.PluginCompatibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val storageVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pluginExtensions by remember { mutableStateOf(emptySet<String>()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            pluginExtensions = withContext(Dispatchers.IO) {
                PluginManager(context).getInstalledPlugins()
                    .filter { it.compatibility == PluginCompatibility.COMPATIBLE }
                    .flatMapTo(linkedSetOf()) { it.supportedExtensions }
            }
        }
    }

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
        pluginExtensions = pluginExtensions,
        state = SettingsScreenState(
            theme = currentThemeState,
            preferences = preferences,
            backup = backupState,
            externalCache = externalCache,
            storageVolumes = storageVolumes
        ),
        navigationActions = SettingsNavigationActions(
            navigateBack = onNavigateBack,
            navigateToPlugins = { onDestination(SettingsDestination.Plugins) },
            navigateToAbout = { onDestination(SettingsDestination.About) }
        ),
        preferenceActions = SettingsPreferenceActions(
            themeChange = onThemeChange,
            showThumbnailsChange = viewModel::updateShowThumbnails,
            homeRecentCarouselLimitChange = viewModel::updateHomeRecentCarouselLimit,
            showHiddenFilesChange = viewModel::updateShowHiddenFiles,
            appStartPageChange = viewModel::updateAppStartPage,
            browserTabsEnabledChange = viewModel::updateBrowserTabsEnabled,
            rememberLastFolderChange = viewModel::updateRememberLastFolder,
            expandableAppBarChange = viewModel::updateExpandableAppBar,
            activityRecordingChange = viewModel::updateActivityRecording,
            browserScrollbarEnabledChange = viewModel::updateBrowserScrollbarEnabled,
            galleryScrollbarEnabledChange = viewModel::updateGalleryScrollbarEnabled,
            fileOpenBehaviorChange = viewModel::updateFileOpenBehavior,
            fileOpenBehaviorRemove = viewModel::removeFileOpenBehavior
        ),
        backupActions = SettingsBackupActions(
            requestExport = { exportBackupLauncher.launch("arcile-settings-backup.json") },
            requestRestore = {
                restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
        ),
        storageActions = SettingsStorageActions(
            clearExternalCache = viewModel::clearExternalCache,
            setVolumeClassification = viewModel::setVolumeClassification,
            resetVolumeClassification = viewModel::resetVolumeClassification
        )
    )
}
