package dev.qtremors.arcile.feature.settings

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.qtremors.arcile.feature.settings.ui.SettingsScreen
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.ui.theme.ThemeState

sealed interface SettingsDestination {
    data object StorageManagement : SettingsDestination
    data object Plugins : SettingsDestination
    data object About : SettingsDestination
}

fun NavGraphBuilder.registerSettingsRoute(
    enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onNavigateBack: () -> Unit,
    onDestination: (SettingsDestination) -> Unit,
    onRestartApp: () -> Unit
) {
    composable<AppRoutes.Settings>(
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) {
        val viewModel = hiltViewModel<SettingsViewModel>()
        val preferences by viewModel.browserPreferences.collectAsStateWithLifecycle()
        val backupState by viewModel.backupState.collectAsStateWithLifecycle()
        SettingsScreen(
            currentThemeState = currentThemeState,
            showThumbnails = preferences.globalPresentation.showThumbnails,
            homeRecentCarouselLimit = preferences.homeRecentCarouselLimit,
            showHiddenFiles = preferences.showHiddenFiles,
            browserScrollbarEnabled = preferences.browserScrollbarEnabled,
            galleryScrollbarEnabled = preferences.galleryScrollbarEnabled,
            onShowThumbnailsChange = viewModel::updateShowThumbnails,
            onHomeRecentCarouselLimitChange = viewModel::updateHomeRecentCarouselLimit,
            onShowHiddenFilesChange = viewModel::updateShowHiddenFiles,
            onBrowserScrollbarEnabledChange = viewModel::updateBrowserScrollbarEnabled,
            onGalleryScrollbarEnabledChange = viewModel::updateGalleryScrollbarEnabled,
            onNavigateBack = onNavigateBack,
            onThemeChange = onThemeChange,
            onOpenStorageManagement = {
                onDestination(SettingsDestination.StorageManagement)
            },
            onNavigateToPlugins = { onDestination(SettingsDestination.Plugins) },
            onNavigateToAbout = { onDestination(SettingsDestination.About) },
            onRestartApp = onRestartApp,
            backupState = backupState,
            onExportSettingsBackup = viewModel::exportPreferences,
            onRestoreSettingsBackup = viewModel::previewRestore,
            onApplySettingsRestore = viewModel::restorePreferences,
            onClearBackupState = viewModel::clearBackupState
        )
    }
}
