package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.R
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.browser.BrowserViewModel
import dev.qtremors.arcile.presentation.home.HomeRefreshMode
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.quickaccess.QuickAccessViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import dev.qtremors.arcile.presentation.trash.TrashViewModel
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.ui.theme.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.qtremors.arcile.domain.BrowserPreferences
import dev.qtremors.arcile.domain.ArchiveFormat
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.data.OnboardingPreferencesStore
import dev.qtremors.arcile.presentation.archive.ArchiveViewerViewModel

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val browserPreferencesStore: BrowserPreferencesStore,
    private val onboardingPreferencesStore: OnboardingPreferencesStore
) : ViewModel() {
    val browserPreferences = browserPreferencesStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowserPreferences())

    fun updateShowThumbnails(show: Boolean) {
        viewModelScope.launch {
            val current = browserPreferences.value.globalPresentation
            browserPreferencesStore.updateGlobalPresentation(current.copy(showThumbnails = show))
        }
    }

    suspend fun resetOnboarding() {
        onboardingPreferencesStore.resetOnboarding()
    }
}

internal enum class BrowserBackFallback {
    PopAppBackStack,
    ShowHomePager
}

internal fun browserBackFallback(hasPreviousBackStackEntry: Boolean): BrowserBackFallback =
    if (hasPreviousBackStackEntry) {
        BrowserBackFallback.PopAppBackStack
    } else {
        BrowserBackFallback.ShowHomePager
    }

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit,
    onRestartApp: () -> Unit
) {
    val context = LocalContext.current
    val openPath: (String) -> Unit = { path ->
        if (ArchiveFormat.isSupported(path)) {
            navController.navigate(AppRoutes.ArchiveViewer(path))
        } else {
            onOpenFile(path)
        }
    }
    NavHost(
                    navController = navController,
                    startDestination = AppRoutes.Main(),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
                    exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
                    popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
                ) {
                composable<AppRoutes.Main> { backStackEntry ->
                    val mainArgs = backStackEntry.toRoute<AppRoutes.Main>()
                    val homeViewModel = hiltViewModel<HomeViewModel>()
                    val browserViewModel = hiltViewModel<BrowserViewModel>()
                    val quickAccessViewModel = hiltViewModel<QuickAccessViewModel>()
                    val browserState by browserViewModel.state.collectAsStateWithLifecycle()
                    val showBrowserPageRequest by backStackEntry.savedStateHandle
                        .getStateFlow("showBrowserPage", false)
                        .collectAsStateWithLifecycle()

                    val pagerState = rememberPagerState(
                        initialPage = mainArgs.initialPage,
                        pageCount = { 2 }
                    )
                    val coroutineScope = rememberCoroutineScope()
                    var pendingExplicitBrowserEntry by remember { mutableStateOf(mainArgs.initialPage == 1) }
                    val navigateBackFromBrowser: () -> Unit = {
                        if (!browserViewModel.navigateBack()) {
                            when (browserBackFallback(navController.previousBackStackEntry != null)) {
                                BrowserBackFallback.PopAppBackStack -> navController.popBackStack()
                                BrowserBackFallback.ShowHomePager -> coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        }
                    }

                    // Handle incoming arguments for browser or deep links
                    androidx.compose.runtime.LaunchedEffect(mainArgs) {
                        if (mainArgs.initialPage == 1) {
                            pendingExplicitBrowserEntry = true
                            when {
                                !mainArgs.path.isNullOrEmpty() -> browserViewModel.navigateToSpecificFolder(
                                    mainArgs.path,
                                    seedInitialPathHistory = mainArgs.seedInitialPathHistory
                                )
                                !mainArgs.category.isNullOrEmpty() -> browserViewModel.navigateToCategory(mainArgs.category, mainArgs.volumeId)
                                else -> browserViewModel.openFileBrowser(restorePersistentLocation = mainArgs.restorePersistentLocation)
                            }
                            pagerState.scrollToPage(1)
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(showBrowserPageRequest) {
                        if (showBrowserPageRequest) {
                            pendingExplicitBrowserEntry = true
                            pagerState.scrollToPage(1)
                            backStackEntry.savedStateHandle["showBrowserPage"] = false
                        }
                    }

                    // Plain swipes are quick access into the persisted folder. Explicit
                    // browser entries (category/path/root taps) keep their requested target.
                    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage == 1) {
                            if (pendingExplicitBrowserEntry) {
                                pendingExplicitBrowserEntry = false
                            } else {
                                browserViewModel.openFileBrowser(restorePersistentLocation = true)
                            }
                        }
                    }

                    BackHandler(enabled = pagerState.currentPage == 1) {
                        navigateBackFromBrowser()
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !(pagerState.currentPage == 1 && browserState.isCategoryScreen),
                        beyondViewportPageCount = 0
                    ) { page ->
                        when (page) {
                            0 -> {
                                val state by homeViewModel.state.collectAsStateWithLifecycle()
                                HomeScreen(
                                    state = state,
                                    onOpenFileBrowser = {
                                        pendingExplicitBrowserEntry = true
                                        browserViewModel.openFileBrowser(restorePersistentLocation = false)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    onSwipeToBrowser = {
                                        // Handled by Pager
                                    },
                                    onNavigateToPath = { path ->
                                        pendingExplicitBrowserEntry = true
                                        browserViewModel.navigateToSpecificFolder(path)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    onOpenFile = openPath,
                                    onCategoryClick = { categoryName ->
                                        pendingExplicitBrowserEntry = true
                                        browserViewModel.navigateToCategory(categoryName)
                                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    onSettingsClick = {
                                        navController.navigate(AppRoutes.Settings)
                                    },
                                    onNavigateToTools = {
                                        navController.navigate(AppRoutes.Tools) {
                                            popUpTo<AppRoutes.Main> { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToAbout = {
                                        navController.navigate(AppRoutes.About)
                                    },
                                    onNavigateToTrash = {
                                        navController.navigate(AppRoutes.Trash) {
                                            popUpTo<AppRoutes.Main> { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToRecentFiles = {
                                        navController.navigate(AppRoutes.RecentFiles()) {
                                            popUpTo<AppRoutes.Main> { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToSaf = { uriString ->
                                        if (!ExternalFileAccessHelper.openInFilesApp(context, uriString)) {
                                            android.widget.Toast.makeText(context, context.getString(R.string.could_not_open_folder_files_app), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onNavigateToQuickAccess = {
                                        navController.navigate(AppRoutes.QuickAccess)
                                    },
                                    onOpenStorageDashboard = { volumeId ->
                                        navController.navigate(AppRoutes.StorageDashboard(volumeId)) {
                                            popUpTo<AppRoutes.Main> { saveState = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onSearchFiltersChange = { homeViewModel.updateSearchFilters(it) },
                                    onToggleSearchFilterMenu = { homeViewModel.toggleSearchFilterMenu(it) },
                                    onRefresh = { homeViewModel.loadHomeData(HomeRefreshMode.MANUAL) },
                                    onResumeRefresh = { homeViewModel.loadHomeData(HomeRefreshMode.SILENT) },
                                    onSetVolumeClassification = { storageKey, kind -> homeViewModel.setVolumeClassification(storageKey, kind) },
                                    onHideClassificationPrompt = { storageKey -> homeViewModel.hideClassificationPrompt(storageKey) }
                                )
                            }
                            1 -> {
                                BrowserScreen(
                                    state = browserState,
                                    onNavigateBack = navigateBackFromBrowser,
                                    onNavigateTo = { browserViewModel.navigateToFolder(it) },
                                    onOpenFile = openPath,
                                    onToggleSelection = { browserViewModel.toggleSelection(it) },
                                    onSelectMultiple = { browserViewModel.selectMultiple(it) },
                                    onClearSelection = { browserViewModel.clearSelection() },
                                    onCreateFolder = { browserViewModel.createFolder(it) },
                                    onCreateFile = { browserViewModel.createFile(it) },
                                    onCreateFakeFile = { name, size -> browserViewModel.createFakeFile(name, size) },
                                    onRequestDeleteSelected = { browserViewModel.requestDeleteSelected() },
                                    onConfirmDelete = { browserViewModel.confirmDeleteSelected() },
                                    onTogglePermanentDelete = { browserViewModel.togglePermanentDelete() },
                                    onDismissDeleteConfirmation = { browserViewModel.dismissDeleteConfirmation() },
                                    onRenameFile = { path, newName -> browserViewModel.renameFile(path, newName) },
                                    onSearchQueryChange = { browserViewModel.updateBrowserSearchQuery(it) },
                                    onClearSearch = { browserViewModel.updateBrowserSearchQuery("") },
                                    onPresentationChange = { presentation, applyToSubfolders ->
                                        browserViewModel.updateBrowserPresentation(presentation, applyToSubfolders)
                                    },
                                    onClearError = { browserViewModel.clearError() },
                                    onCopySelected = { browserViewModel.copySelectedToClipboard() },
                                    onCutSelected = { browserViewModel.cutSelectedToClipboard() },
                                    onPasteFromClipboard = { browserViewModel.pasteFromClipboard() },
                                    onCancelClipboard = { browserViewModel.cancelClipboard() },
                                    onShareSelected = {
                                        coroutineScope.launch {
                                            if (dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, browserState.selectedFiles.toList())) {
                                                browserViewModel.clearSelection()
                                            }
                                        }
                                    },
                                    onClearFileOperationStatusMessage = { browserViewModel.clearFileOperationStatusMessage() },
                                    onOpenProperties = { browserViewModel.openPropertiesForSelection() },
                                    onDismissProperties = { browserViewModel.dismissProperties() },
                                    onClearActiveFileOperation = { browserViewModel.clearActiveFileOperation() },
                                    isRefreshing = browserState.isPullToRefreshing,
                                    onRefresh = { browserViewModel.refresh(pullToRefresh = true) },
                                    onSearchFiltersChange = { browserViewModel.updateSearchFilters(it) },
                                    onToggleSearchFilterMenu = { browserViewModel.toggleSearchFilterMenu(it) },
                                    onResolvingConflicts = { browserViewModel.resolveConflicts(it) },
                                    onDismissConflictDialog = { browserViewModel.dismissConflictDialog() },
                                    onPinToQuickAccess = { path, label -> quickAccessViewModel.addCustomFolder(path, label) },
                                    onNativeRequestResult = { confirmed -> browserViewModel.handleNativeActionResult(confirmed) },
                                    onSelectAll = { browserViewModel.selectAll(it) },
                                    onInvertSelection = { browserViewModel.invertSelection(it) },
                                    onRemoveFromClipboard = { browserViewModel.removeFromClipboard(it) },
                                    onSelectFolderTab = { browserViewModel.selectFolderTab(it) },
                                    onExtractSelectedArchive = { password -> browserViewModel.extractSelectedArchiveHere(password) },
                                    onExtractSelectedArchiveToFolder = { password -> browserViewModel.extractSelectedArchiveToFolder(password) },
                                    onCreateZipFromSelection = { browserViewModel.createZipFromSelection() },
                                    onCreateArchiveFromSelection = { name, format, password ->
                                        browserViewModel.createArchiveFromSelection(name, format, password)
                                    },
                                    nativeRequestFlow = browserViewModel.nativeRequestFlow
                                )
                            }
                        }
                    }
                }
                composable<AppRoutes.StorageDashboard> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Main>()
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
                            navController.navigate(AppRoutes.Main(initialPage = 1, category = categoryName, volumeId = scopedVolumeId))
                        },
                        onOpenPath = { path ->
                            navController.navigate(AppRoutes.Main(initialPage = 1, path = path))
                        },
                        onOpenFile = openPath
                    )
                }
                composable<AppRoutes.Explorer> { backStackEntry ->
                    val explorer = backStackEntry.toRoute<AppRoutes.Explorer>()
                    navController.navigate(AppRoutes.Main(
                        initialPage = 1,
                        path = explorer.path,
                        category = explorer.category,
                        volumeId = explorer.volumeId,
                        restorePersistentLocation = explorer.restorePersistentLocation
                    )) {
                        popUpTo<AppRoutes.Main> { inclusive = true }
                    }
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
                        onSortChange = { viewModel.updateSortOption(it) },
                        onFilterChange = { viewModel.updateFilter(it) },
                        onOpenProperties = { viewModel.openPropertiesForSelection() },
                        onDismissProperties = { viewModel.dismissProperties() },
                        onClearSnackbarMessage = { viewModel.clearSnackbarMessage() },
                        nativeRequestFlow = viewModel.nativeRequestFlow
                    )

                }
                composable<AppRoutes.RecentFiles> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Main>()
                    }
                    val browserViewModel = hiltViewModel<BrowserViewModel>(parentEntry)
                    val viewModel = hiltViewModel<RecentFilesViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val recentFilesCoroutineScope = rememberCoroutineScope()
                    RecentFilesScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenFile = openPath,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onClearSelection = { viewModel.clearSelection() },
                        onRequestDeleteSelected = { viewModel.requestDeleteSelected() },
                        onConfirmDelete = { viewModel.confirmDeleteSelected() },
                        onTogglePermanentDelete = { viewModel.togglePermanentDelete() },
                        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
                        onShareSelected = {
                            recentFilesCoroutineScope.launch {
                                if (dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, state.selectedFiles.toList())) {
                                    viewModel.clearSelection()
                                }
                            }
                        },
                        onSelectAll = { viewModel.selectAll() },
                        onRefresh = { viewModel.loadRecentFiles() },
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onClearSearch = { viewModel.updateSearchQuery("") },
                        onSearchFiltersChange = { viewModel.updateSearchFilters(it) },
                        onPresentationChange = { viewModel.updatePresentation(it) },
                        onSelectMultiple = { viewModel.selectMultiple(it) },
                        onLoadMore = { viewModel.loadMore() },
                        onClearError = { viewModel.clearError() },
                        onOpenProperties = { viewModel.openPropertiesForSelection() },
                        onDismissProperties = { viewModel.dismissProperties() },
                        onOpenContainingFolder = { path ->
                            browserViewModel.navigateToSpecificFolder(path, seedInitialPathHistory = false)
                            parentEntry.savedStateHandle["showBrowserPage"] = true
                            navController.popBackStack<AppRoutes.Main>(inclusive = false)
                        },
                        nativeRequestFlow = viewModel.nativeRequestFlow
                    )
                }
                composable<AppRoutes.Tools> {
                    ToolsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable<AppRoutes.Settings> {
                    val viewModel = hiltViewModel<SettingsViewModel>()
                    val browserPrefs by viewModel.browserPreferences.collectAsStateWithLifecycle()
                    SettingsScreen(
                        currentThemeState = currentThemeState,
                        showThumbnails = browserPrefs.globalPresentation.showThumbnails,
                        onShowThumbnailsChange = { viewModel.updateShowThumbnails(it) },
                        onNavigateBack = { navController.popBackStack() },
                        onThemeChange = onThemeChange,
                        onOpenStorageManagement = { navController.navigate(AppRoutes.StorageManagement) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.About) },
                        onRunOnboardingAgain = { viewModel.resetOnboarding() },
                        onRestartApp = onRestartApp
                    )
                }
                composable<AppRoutes.StorageManagement> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Main>()
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
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate(AppRoutes.Licenses) }
                    )
                }
                composable<AppRoutes.Licenses> {
                    LicensesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable<AppRoutes.QuickAccess> { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Main>()
                    }
                    val browserViewModel = hiltViewModel<BrowserViewModel>(parentEntry)
                    val viewModel = hiltViewModel<QuickAccessViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    QuickAccessScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToPath = { path ->
                            browserViewModel.navigateToSpecificFolder(path, seedInitialPathHistory = false)
                            parentEntry.savedStateHandle["showBrowserPage"] = true
                            navController.popBackStack<AppRoutes.Main>(inclusive = false)
                        },
                        onNavigateToSaf = { uriString ->
                            ExternalFileAccessHelper.openInFilesApp(context, uriString)
                        },
                        onTogglePin = { viewModel.togglePin(it) },
                        onRemoveItem = { viewModel.removeCustomItem(it) },
                        onAddCustomFolder = { path, label -> viewModel.addCustomFolder(path, label) },
                        onAddSafFolder = { uri, label ->
                            if (label == "Android/data" || label == "Android/obb") {
                                viewModel.addExternalHandoffFolder(uri, label)
                            } else {
                                viewModel.addSafFolder(uri, label)
                            }
                        }
                    )
                }
                composable<AppRoutes.ArchiveViewer> {
                    val viewModel = hiltViewModel<ArchiveViewerViewModel>()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    ArchiveViewerScreen(
                        state = state,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateUpInArchive = { viewModel.navigateBack() },
                        onOpenFolder = { viewModel.openFolder(it) },
                        onExtractAll = { password -> viewModel.extractAll(password) },
                        onExtractCurrentFolder = { password -> viewModel.extractCurrentFolder(password) },
                        onSubmitPassword = { viewModel.submitPassword(it) },
                        onClearError = { viewModel.clearError() },
                        onCancelExtraction = { viewModel.cancelExtraction() },
                        onClearOperationStatusMessage = { viewModel.clearOperationStatusMessage() },
                        onClearActiveOperation = { viewModel.clearActiveOperation() }
                    )
                }
            }
}
