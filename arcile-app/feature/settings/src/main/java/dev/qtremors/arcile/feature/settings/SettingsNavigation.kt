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
import dev.qtremors.arcile.core.ui.theme.ThemeState

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
            state = dev.qtremors.arcile.feature.settings.ui.SettingsScreenState(
                theme = currentThemeState,
                preferences = preferences,
                backup = backupState
            ),
            navigationActions = dev.qtremors.arcile.feature.settings.ui.SettingsNavigationActions(
                navigateBack = onNavigateBack,
                openStorageManagement = {
                    onDestination(SettingsDestination.StorageManagement)
                },
                navigateToPlugins = { onDestination(SettingsDestination.Plugins) },
                navigateToAbout = { onDestination(SettingsDestination.About) },
                restartApp = onRestartApp
            ),
            preferenceActions = dev.qtremors.arcile.feature.settings.ui.SettingsPreferenceActions(
                themeChange = onThemeChange,
                showThumbnailsChange = viewModel::updateShowThumbnails,
                homeRecentCarouselLimitChange = viewModel::updateHomeRecentCarouselLimit,
                showHiddenFilesChange = viewModel::updateShowHiddenFiles,
                browserScrollbarEnabledChange = viewModel::updateBrowserScrollbarEnabled,
                galleryScrollbarEnabledChange = viewModel::updateGalleryScrollbarEnabled
            ),
            backupActions = dev.qtremors.arcile.feature.settings.ui.SettingsBackupActions(
                export = viewModel::exportPreferences,
                previewRestore = viewModel::previewRestore,
                applyRestore = viewModel::restorePreferences,
                clearState = viewModel::clearBackupState
            )
        )
    }
}
