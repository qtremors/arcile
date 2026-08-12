package dev.qtremors.arcile.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeConnectionState
import dev.qtremors.arcile.core.privilege.PrivilegeMode
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.showArcileToast
import dev.qtremors.arcile.feature.settings.ui.SettingsAccessActions
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
    val access by viewModel.accessState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            access = access,
            externalCache = externalCache
        ),
        navigationActions = SettingsNavigationActions(
            navigateBack = onNavigateBack,
            openStorageManagement = { onDestination(SettingsDestination.StorageManagement) },
            navigateToPlugins = { onDestination(SettingsDestination.Plugins) },
            navigateToAbout = { onDestination(SettingsDestination.About) }
        ),
        preferenceActions = SettingsPreferenceActions(
            themeChange = onThemeChange,
            showThumbnailsChange = viewModel::updateShowThumbnails,
            homeRecentCarouselLimitChange = viewModel::updateHomeRecentCarouselLimit,
            showHiddenFilesChange = viewModel::updateShowHiddenFiles,
            browserScrollbarEnabledChange = viewModel::updateBrowserScrollbarEnabled,
            galleryScrollbarEnabledChange = viewModel::updateGalleryScrollbarEnabled,
            fileOpenBehaviorChange = viewModel::updateFileOpenBehavior
        ),
        backupActions = SettingsBackupActions(
            requestExport = { exportBackupLauncher.launch("arcile-settings-backup.json") },
            requestRestore = {
                restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            }
        ),
        storageActions = SettingsStorageActions(
            clearExternalCache = viewModel::clearExternalCache
        ),
        accessActions = SettingsAccessActions(
            selectMode = { mode ->
                viewModel.selectAccessMode(mode)
                if (
                    mode == PrivilegeMode.NORMAL &&
                    access.access.backendStates[PrivilegeBackendId.NORMAL]
                        ?.connectionState != PrivilegeConnectionState.READY
                ) {
                    context.openNormalStorageSettings()
                }
            },
            reconnect = viewModel::reconnectAccess,
            useNormal = viewModel::useNormalAccess,
            grantNormalPermission = context::openNormalStorageSettings,
            openShizukuManager = context::openShizukuManager,
            protectedWritesChange = viewModel::updateProtectedFilesystemWrites
        )
    )
}

private fun Context.openNormalStorageSettings() {
    val appSettings = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.parse("package:$packageName")
    }
    val opened = runCatching { startActivity(appSettings) }.recoverCatching {
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }.isSuccess
    if (!opened) showArcileToast(getString(R.string.storage_access_settings_unavailable))
}

@Suppress("DEPRECATION")
private fun Context.openShizukuManager() {
    val managerPackage = runCatching {
        packageManager.getPackagesHoldingPermissions(
            arrayOf(SHIZUKU_PERMISSION),
            PackageManager.MATCH_DISABLED_COMPONENTS
        ).firstOrNull()?.packageName
    }.getOrNull()
    val intent = managerPackage?.let(packageManager::getLaunchIntentForPackage)
        ?: managerPackage?.let { packageName ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
    if (intent == null || runCatching { startActivity(intent) }.isFailure) {
        showArcileToast(getString(R.string.storage_access_shizuku_manager_unavailable))
    }
}

private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
