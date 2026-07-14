package dev.qtremors.arcile.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.home.ui.HomeContentIntents
import dev.qtremors.arcile.feature.home.ui.HomeNavigationIntents
import dev.qtremors.arcile.feature.home.ui.HomeScreen

sealed interface HomeDestination {
    data object BrowseRoot : HomeDestination
    data class BrowsePath(val path: String) : HomeDestination
    data class OpenFile(val path: String, val context: List<FileModel>) : HomeDestination
    data class BrowseCategory(val name: String) : HomeDestination
    data object Settings : HomeDestination
    data object Tools : HomeDestination
    data object About : HomeDestination
    data object Trash : HomeDestination
    data object RecentFiles : HomeDestination
    data object QuickAccess : HomeDestination
    data class ExternalFolder(val uri: String) : HomeDestination
    data class StorageDashboard(val volumeId: String?) : HomeDestination
    data object Cleaner : HomeDestination
    data object ActivityLog : HomeDestination
    data object OnlyFiles : HomeDestination
    data class ShareRecentFile(val path: String, val context: List<FileModel>) : HomeDestination
}

@Composable
fun HomeRoute(
    onDestination: (HomeDestination) -> Unit
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val preferencesViewModel = hiltViewModel<HomePreferencesViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeRecentCarouselLimit by preferencesViewModel.recentCarouselLimit.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        navigationIntents = HomeNavigationIntents(
            openFileBrowser = { onDestination(HomeDestination.BrowseRoot) },
            navigateToPath = { onDestination(HomeDestination.BrowsePath(it)) },
            openFileWithContext = { path, context ->
                onDestination(HomeDestination.OpenFile(path, context))
            },
            categoryClick = { onDestination(HomeDestination.BrowseCategory(it)) },
            settingsClick = { onDestination(HomeDestination.Settings) },
            navigateToTools = { onDestination(HomeDestination.Tools) },
            navigateToAbout = { onDestination(HomeDestination.About) },
            navigateToTrash = { onDestination(HomeDestination.Trash) },
            navigateToRecentFiles = { onDestination(HomeDestination.RecentFiles) },
            navigateToQuickAccess = { onDestination(HomeDestination.QuickAccess) },
            navigateToExternalFolder = { onDestination(HomeDestination.ExternalFolder(it)) },
            openStorageDashboard = { onDestination(HomeDestination.StorageDashboard(it)) },
            navigateToCleaner = { onDestination(HomeDestination.Cleaner) },
            navigateToActivity = { onDestination(HomeDestination.ActivityLog) },
            navigateToOnlyFiles = { onDestination(HomeDestination.OnlyFiles) }
        ),
        contentIntents = HomeContentIntents(
            refresh = { viewModel.loadHomeData(HomeRefreshMode.MANUAL) },
            resumeRefresh = { viewModel.loadHomeData(HomeRefreshMode.SILENT) },
            shareRecentFile = { path ->
                onDestination(HomeDestination.ShareRecentFile(path, state.displayState.todayRecentFiles))
            },
            setVolumeClassification = viewModel::setVolumeClassification,
            hideClassificationPrompt = viewModel::hideClassificationPrompt
        ),
        homeRecentCarouselLimit = homeRecentCarouselLimit
    )
}
