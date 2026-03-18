package dev.qtremors.arcile.presentation.ui

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.browser.BrowserViewModel
import dev.qtremors.arcile.presentation.home.HomeRefreshMode
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import dev.qtremors.arcile.presentation.trash.TrashViewModel
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.ui.theme.ThemeState
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ArcileAppShell(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.Home
    val context = LocalContext.current

    androidx.compose.animation.SharedTransitionLayout {
        Scaffold(
            contentWindowInsets = WindowInsets(0)
        ) { scaffoldPadding ->
            Box(modifier = Modifier.padding(scaffoldPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.Home,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                composable<AppRoutes.Home> {
                    val viewModel = hiltViewModel<HomeViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onOpenFileBrowser = {
                            navController.navigate(AppRoutes.Explorer()) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToPath = { path ->
                            navController.navigate(AppRoutes.Explorer(path = path)) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenFile = onOpenFile,
                        onCategoryClick = { categoryName ->
                            navController.navigate(AppRoutes.Explorer(category = categoryName)) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onSettingsClick = {
                            navController.navigate(AppRoutes.Settings)
                        },
                        onNavigateToTools = {
                            navController.navigate(AppRoutes.Tools) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAbout = {
                            navController.navigate(AppRoutes.About)
                        },
                        onNavigateToTrash = {
                            navController.navigate(AppRoutes.Trash) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToRecentFiles = {
                            navController.navigate(AppRoutes.RecentFiles()) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenStorageDashboard = { volumeId ->
                            navController.navigate(AppRoutes.StorageDashboard(volumeId = volumeId)) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onSearchQueryChange = { viewModel.updateHomeSearchQuery(it) },
                        onSearchFiltersChange = { viewModel.updateSearchFilters(it) },
                        onToggleSearchFilterMenu = { viewModel.toggleSearchFilterMenu(it) },
                        onRefresh = { viewModel.loadHomeData(HomeRefreshMode.MANUAL) },
                        onResumeRefresh = { viewModel.loadHomeData(HomeRefreshMode.SILENT) },
                        onSetVolumeClassification = { storageKey, kind -> viewModel.setVolumeClassification(storageKey, kind) },
                        onHideClassificationPrompt = { storageKey -> viewModel.hideClassificationPrompt(storageKey) }
                    )
                }
                composable<AppRoutes.StorageDashboard> { backStackEntry ->
                    val viewModel = hiltViewModel<HomeViewModel>() // Shares Home logic
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val route = backStackEntry.toRoute<AppRoutes.StorageDashboard>()
                    val volumeId = route.volumeId?.takeIf { it.isNotBlank() }
                    StorageDashboardScreen(
                        state = state,
                        selectedVolumeId = volumeId,
                        onNavigateBack = { navController.popBackStack() },
                        onCategoryClick = { categoryName, scopedVolumeId ->
                            navController.navigate(AppRoutes.Explorer(category = categoryName, volumeId = scopedVolumeId)) {
                                popUpTo<AppRoutes.StorageDashboard> { inclusive = true }
                            }
                        }
                    )
                }
                composable<AppRoutes.Explorer> {
                    val viewModel = hiltViewModel<BrowserViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()

                    FileManagerScreen(
                        state = state,
                        onNavigateBack = {
                            if (!viewModel.navigateBack()) {
                                navController.popBackStack()
                            }
                        },
                        onNavigateTo = { viewModel.navigateToFolder(it) },
                        onOpenFile = onOpenFile,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onSelectMultiple = { viewModel.selectMultiple(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onCreateFolder = { viewModel.createFolder(it) },
                        onCreateFile = { viewModel.createFile(it) },
                        onRequestDeleteSelected = { viewModel.requestDeleteSelected() },
                        onConfirmTrash = { viewModel.moveSelectedToTrash() },
                        onConfirmPermanentDelete = { viewModel.deleteSelectedPermanently() },
                        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
                        onRenameFile = { path, newName -> viewModel.renameFile(path, newName) },
                        onSearchQueryChange = { viewModel.updateBrowserSearchQuery(it) },
                        onClearSearch = { viewModel.updateBrowserSearchQuery("") },
                        onSortOptionChange = { option, applyToSubfolders -> viewModel.updateBrowserSortOption(option, applyToSubfolders) },
                        onGridViewChange = { viewModel.setGridView(it) },
                        onClearError = { viewModel.clearError() },
                        onCopySelected = { viewModel.copySelectedToClipboard() },
                        onCutSelected = { viewModel.cutSelectedToClipboard() },
                        onPasteFromClipboard = { viewModel.pasteFromClipboard() },
                        onCancelClipboard = { viewModel.cancelClipboard() },
                        onShareSelected = { viewModel.shareSelectedFiles(context) },
                        isRefreshing = state.isPullToRefreshing,
                        onRefresh = { viewModel.refresh(pullToRefresh = true) },
                        onSearchFiltersChange = { viewModel.updateSearchFilters(it) },
                        onToggleSearchFilterMenu = { viewModel.toggleSearchFilterMenu(it) },
                        onResolvingConflicts = { viewModel.resolveConflicts(it) },
                        onDismissConflictDialog = { viewModel.dismissConflictDialog() },
                        onDeletePermanentlySelected = { viewModel.deleteSelectedPermanently() },
                        onClearNativeRequest = { viewModel.clearNativeRequest() }
                    )
                }
                composable<AppRoutes.Trash> {
                    val viewModel = hiltViewModel<TrashViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    TrashScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onRestoreSelected = { viewModel.restoreSelectedTrash() },
                        onEmptyTrash = { viewModel.emptyTrash() },
                        onClearError = { viewModel.clearError() },
                        onDismissDestinationPicker = { viewModel.dismissDestinationPicker() },
                        onRestoreToDestination = { ids, path -> viewModel.restoreToDestination(ids, path) },
                        onClearNativeRequest = { viewModel.clearNativeRequest() }
                    )
                }
                composable<AppRoutes.RecentFiles> {
                    val viewModel = hiltViewModel<RecentFilesViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    RecentFilesScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenFile = onOpenFile,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onRequestDeleteSelected = { viewModel.requestDeleteSelected() },
                        onConfirmTrash = { viewModel.moveSelectedToTrash() },
                        onConfirmPermanentDelete = { viewModel.deleteSelectedPermanently() },
                        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
                        onShareSelected = { viewModel.shareSelectedFiles(context) },
                        onRefresh = { viewModel.loadRecentFiles() },
                        onClearNativeRequest = { viewModel.clearNativeRequest() }
                    )
                }
                composable<AppRoutes.Tools> {
                    ToolsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable<AppRoutes.Settings> {
                    SettingsScreen(
                        currentThemeState = currentThemeState,
                        onNavigateBack = { navController.popBackStack() },
                        onThemeChange = onThemeChange,
                        onOpenStorageManagement = { navController.navigate(AppRoutes.StorageManagement) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.About) }
                    )
                }
                composable<AppRoutes.StorageManagement> {
                    val viewModel = hiltViewModel<HomeViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    StorageManagementScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onSetVolumeClassification = { storageKey, kind -> viewModel.setVolumeClassification(storageKey, kind) },
                        onResetVolumeClassification = { storageKey -> viewModel.resetVolumeClassification(storageKey) }
                    )
                }
                composable<AppRoutes.About> {
                    AboutScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}


