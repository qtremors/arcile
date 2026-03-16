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
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.browser.BrowserViewModel
import dev.qtremors.arcile.presentation.home.HomeRefreshMode
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import dev.qtremors.arcile.presentation.trash.TrashViewModel
import dev.qtremors.arcile.ui.theme.ThemeState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ArcileAppShell(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.HOME
    val context = LocalContext.current

    androidx.compose.animation.SharedTransitionLayout {
        Scaffold(
            contentWindowInsets = WindowInsets(0)
        ) { scaffoldPadding ->
            Box(modifier = Modifier.padding(scaffoldPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.HOME,
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                composable(AppRoutes.HOME) {
                    val viewModel = hiltViewModel<HomeViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onOpenFileBrowser = {
                            navController.navigate(AppRoutes.EXPLORER + "?path=&category=&volumeId=") {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToPath = { path ->
                            navController.navigate(AppRoutes.EXPLORER + "?path=${Uri.encode(path)}") {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenFile = onOpenFile,
                        onCategoryClick = { categoryName ->
                            navController.navigate(AppRoutes.EXPLORER + "?category=${Uri.encode(categoryName)}") {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onSettingsClick = {
                            navController.navigate(AppRoutes.SETTINGS)
                        },
                        onNavigateToTools = {
                            navController.navigate(AppRoutes.TOOLS) {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAbout = {
                            navController.navigate(AppRoutes.ABOUT)
                        },
                        onNavigateToTrash = {
                            navController.navigate(AppRoutes.TRASH) {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToRecentFiles = {
                            navController.navigate(AppRoutes.RECENT_FILES + "?volumeId=") {
                                popUpTo(AppRoutes.HOME) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onOpenStorageDashboard = { volumeId ->
                            val route = if (volumeId != null) {
                                AppRoutes.STORAGE_DASHBOARD + "?volumeId=${Uri.encode(volumeId)}"
                            } else {
                                AppRoutes.STORAGE_DASHBOARD + "?volumeId="
                            }
                            navController.navigate(route) {
                                popUpTo(AppRoutes.HOME) { saveState = true }
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
                composable(AppRoutes.STORAGE_DASHBOARD + "?volumeId={volumeId}") { backStackEntry ->
                    val viewModel = hiltViewModel<HomeViewModel>() // Shares Home logic
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val volumeId = backStackEntry.arguments?.getString("volumeId")?.takeIf { it.isNotBlank() }
                    StorageDashboardScreen(
                        state = state,
                        selectedVolumeId = volumeId,
                        onNavigateBack = { navController.popBackStack() },
                        onCategoryClick = { categoryName, scopedVolumeId ->
                            val route = buildString {
                                append(AppRoutes.EXPLORER)
                                append("?category=")
                                append(Uri.encode(categoryName))
                                if (scopedVolumeId != null) {
                                    append("&volumeId=")
                                    append(Uri.encode(scopedVolumeId))
                                }
                            }
                            navController.navigate(route) {
                                popUpTo(AppRoutes.STORAGE_DASHBOARD) { inclusive = true }
                            }
                        }
                    )
                }
                composable(AppRoutes.EXPLORER + "?path={path}&category={category}&volumeId={volumeId}") {
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
                        onDeletePermanentlySelected = { viewModel.deleteSelectedPermanently() }
                    )
                }
                composable(AppRoutes.TRASH) {
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
                        onRestoreToDestination = { viewModel.restoreToDestination(it) }
                    )
                }
                composable(AppRoutes.RECENT_FILES + "?volumeId={volumeId}") {
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
                        onRefresh = { viewModel.loadRecentFiles() }
                    )
                }
                composable(AppRoutes.TOOLS) {
                    ToolsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoutes.SETTINGS) {
                    SettingsScreen(
                        currentThemeState = currentThemeState,
                        onNavigateBack = { navController.popBackStack() },
                        onThemeChange = onThemeChange,
                        onOpenStorageManagement = { navController.navigate(AppRoutes.STORAGE_MANAGEMENT) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.ABOUT) }
                    )
                }
                composable(AppRoutes.STORAGE_MANAGEMENT) {
                    val viewModel = hiltViewModel<HomeViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    StorageManagementScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onSetVolumeClassification = { storageKey, kind -> viewModel.setVolumeClassification(storageKey, kind) },
                        onResetVolumeClassification = { storageKey -> viewModel.resetVolumeClassification(storageKey) }
                    )
                }
                composable(AppRoutes.ABOUT) {
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
            text = "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This application requires permission to read and manage files on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}


