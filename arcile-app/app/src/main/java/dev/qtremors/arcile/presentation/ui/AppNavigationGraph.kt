package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.browser.BrowserViewModel
import dev.qtremors.arcile.presentation.home.HomeRefreshMode
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import dev.qtremors.arcile.presentation.trash.TrashViewModel
import dev.qtremors.arcile.ui.theme.ThemeState

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
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
                        onShareSelected = {
                            if (dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, state.selectedFiles.toList())) {
                                viewModel.clearSelection()
                            }
                        },
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
                        onShareSelected = {
                            if (dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, state.selectedFiles.toList())) {
                                viewModel.clearSelection()
                            }
                        },
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
