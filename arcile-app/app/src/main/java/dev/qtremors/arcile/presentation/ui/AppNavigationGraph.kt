package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.qtremors.arcile.presentation.quickaccess.QuickAccessViewModel
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
                        onNavigateToSaf = { uriString ->
                            try {
                                val uri = android.net.Uri.parse(uriString)

                                // directory MIME type, read permission flag, targeted at DocumentsUI
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

                                    // Target the system DocumentsUI package
                                    val packageInfos = context.packageManager.getPackagesHoldingPermissions(
                                        arrayOf(android.Manifest.permission.MANAGE_DOCUMENTS), 0
                                    )
                                    val documentsUiPackage = packageInfos.firstOrNull { it.packageName.endsWith(".documentsui") }?.packageName
                                        ?: packageInfos.firstOrNull()?.packageName
                                    documentsUiPackage?.let { setPackage(it) }
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(context, "Could not open folder in Files app", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        onNavigateToQuickAccess = {
                            navController.navigate(AppRoutes.QuickAccess)
                        },
                        onOpenStorageDashboard = { volumeId ->
                            navController.navigate(AppRoutes.StorageDashboard(volumeId)) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onSearchFiltersChange = { viewModel.updateSearchFilters(it) },
                        onToggleSearchFilterMenu = { viewModel.toggleSearchFilterMenu(it) },
                        onRefresh = { viewModel.loadHomeData(HomeRefreshMode.MANUAL) },
                        onResumeRefresh = { viewModel.loadHomeData(HomeRefreshMode.SILENT) },
                        onSetVolumeClassification = { storageKey, kind -> viewModel.setVolumeClassification(storageKey, kind) },
                        onHideClassificationPrompt = { storageKey -> viewModel.hideClassificationPrompt(storageKey) }
                    )
                }
                composable<AppRoutes.StorageDashboard> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Home>()
                    }
                    val viewModel = hiltViewModel<HomeViewModel>(parentEntry)
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
                    val quickAccessViewModel = hiltViewModel<QuickAccessViewModel>()

                    BrowserScreen(
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
                        onConfirmDelete = { viewModel.confirmDeleteSelected() },
                        onTogglePermanentDelete = { viewModel.togglePermanentDelete() },
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
                        onPinToQuickAccess = { path, label -> quickAccessViewModel.addCustomFolder(path, label) },
                        nativeRequestFlow = viewModel.nativeRequestFlow
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
                        onPermanentlyDeleteSelected = { viewModel.deletePermanentlySelected() },
                        onDismissPermanentDelete = { viewModel.dismissPermanentDeleteConfirmation() },
                        onSelectAll = { viewModel.selectAll() },
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onClearSearch = { viewModel.updateSearchQuery("") },
                        nativeRequestFlow = viewModel.nativeRequestFlow
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
                        onConfirmDelete = { viewModel.confirmDeleteSelected() },
                        onTogglePermanentDelete = { viewModel.togglePermanentDelete() },
                        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
                        onShareSelected = {
                            if (dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, state.selectedFiles.toList())) {
                                viewModel.clearSelection()
                            }
                        },
                        onRefresh = { viewModel.loadRecentFiles() },
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onClearSearch = { viewModel.updateSearchQuery("") },
                        onLoadMore = { viewModel.loadMore() },
                        nativeRequestFlow = viewModel.nativeRequestFlow
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
                composable<AppRoutes.StorageManagement> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Home>()
                    }
                    val viewModel = hiltViewModel<HomeViewModel>(parentEntry)
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
                composable<AppRoutes.QuickAccess> {
                    val viewModel = hiltViewModel<QuickAccessViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    QuickAccessScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPath = { path ->
                            navController.navigate(AppRoutes.Explorer(path = path)) {
                                popUpTo(AppRoutes.Home) { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateToSaf = { uriString ->
                            try {
                                val uri = android.net.Uri.parse(uriString)
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

                                    val packageInfos = context.packageManager.getPackagesHoldingPermissions(
                                        arrayOf(android.Manifest.permission.MANAGE_DOCUMENTS), 0
                                    )
                                    val documentsUiPackage = packageInfos.firstOrNull { it.packageName.endsWith(".documentsui") }?.packageName
                                        ?: packageInfos.firstOrNull()?.packageName
                                    documentsUiPackage?.let { setPackage(it) }
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onTogglePin = { viewModel.togglePin(it) },
                        onRemoveItem = { viewModel.removeCustomItem(it) },
                        onAddCustomFolder = { path, label -> viewModel.addCustomFolder(path, label) },
                        onAddSafFolder = { uri, label -> viewModel.addSafFolder(uri, label) }
                    )
                }
            }
}
