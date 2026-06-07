package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.navigation.NavBackStackEntry
import dev.qtremors.arcile.ui.theme.LocalReducedMotionEnabled
import dev.qtremors.arcile.ui.theme.ArcileMotion
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.archive.archiveViewerScreen
import dev.qtremors.arcile.feature.browser.ui.BrowserScreen
import dev.qtremors.arcile.feature.trash.trashScreen
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.feature.browser.BrowserViewModel
import dev.qtremors.arcile.presentation.home.HomeRefreshMode
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.feature.quickaccess.QuickAccessViewModel
import dev.qtremors.arcile.feature.quickaccess.quickAccessScreen
import dev.qtremors.arcile.feature.recentfiles.recentFilesScreen
import dev.qtremors.arcile.feature.storagecleaner.storageCleanerScreen
import dev.qtremors.arcile.presentation.utils.ExternalFileAccessHelper
import dev.qtremors.arcile.ui.theme.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit,
    onRestartApp: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val openPath: (String) -> Unit = { path ->
        val archiveFormat = ArchiveFormat.fromPath(path)
        when {
            archiveFormat?.canBrowse == true -> {
                navController.navigate(AppRoutes.Main(initialPage = 1, archivePath = path, seedInitialPathHistory = false)) {
                    popUpTo<AppRoutes.Main> { inclusive = true }
                }
            }
            archiveFormat != null -> {
                onFeedback(
                    ArcileFeedbackEvent(
                        message = dev.qtremors.arcile.core.ui.UiText.StringResource(R.string.unsupported_archive_format),
                        severity = ArcileFeedbackSeverity.Error
                    )
                )
            }
            else -> {
                onOpenFile(path)
            }
        }
    }

    val reducedMotion = LocalReducedMotionEnabled.current

    // Details Transitions: Horizontal Slide (e.g. StorageDashboard, ArchiveViewer)
    val detailEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val detailExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val detailPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val detailPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    // Utility/Modal Transitions: Vertical Slide + Fade (e.g. Settings, Trash, etc.)
    val utilityEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else slideInVertically(initialOffsetY = { it / 8 }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val utilityExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val utilityPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val utilityPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutVertically(targetOffsetY = { it / 8 }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Main(),
        enterTransition = { if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) },
        exitTransition = { if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) },
        popEnterTransition = { if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) },
        popExitTransition = { if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) }
    ) {
                composable<AppRoutes.Main> { backStackEntry ->
                    val mainArgs = backStackEntry.toRoute<AppRoutes.Main>()
                    val homeViewModel = hiltViewModel<HomeViewModel>()
                    val browserViewModel = hiltViewModel<BrowserViewModel>()
                    val quickAccessViewModel = hiltViewModel<QuickAccessViewModel>()
                    val settingsViewModel = hiltViewModel<SettingsViewModel>()
                    val browserState by browserViewModel.state.collectAsStateWithLifecycle()
                    val browserPrefs by settingsViewModel.browserPreferences.collectAsStateWithLifecycle()
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
                            val path = mainArgs.path
                            val archivePath = mainArgs.archivePath
                            val category = mainArgs.category
                            when {
                                !archivePath.isNullOrEmpty() -> browserViewModel.openArchive(archivePath)
                                !path.isNullOrEmpty() -> browserViewModel.navigateToSpecificFolder(
                                    path,
                                    seedInitialPathHistory = mainArgs.seedInitialPathHistory
                                )
                                !category.isNullOrEmpty() -> browserViewModel.navigateToCategory(category, mainArgs.volumeId)
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
                                    onNavigateToCleaner = {
                                        navController.navigate(AppRoutes.StorageCleaner) {
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
                                            onFeedback(
                                                ArcileFeedbackEvent(
                                                    message = dev.qtremors.arcile.core.ui.UiText.StringResource(R.string.could_not_open_folder_files_app),
                                                    severity = ArcileFeedbackSeverity.Error
                                                )
                                            )
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
                                    onShareRecentFile = { path ->
                                        coroutineScope.launch {
                                            dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, listOf(path))
                                        }
                                    },
                                    homeRecentCarouselLimit = browserPrefs.homeRecentCarouselLimit,
                                    onSetVolumeClassification = { storageKey, kind -> homeViewModel.setVolumeClassification(storageKey, kind) },
                                    onHideClassificationPrompt = { storageKey -> homeViewModel.hideClassificationPrompt(storageKey) }
                                )
                            }
                            1 -> {
                                BrowserScreen(
                                    state = browserState,
                                    onNavigateBack = navigateBackFromBrowser,
                                    onNavigateTo = { browserViewModel.navigateToFolder(it) },
                                    onOpenFile = { path ->
                                        if (ArchiveFormat.isSupported(path)) {
                                            browserViewModel.openArchive(path)
                                        } else {
                                            openPath(path)
                                        }
                                    },
                                    onToggleSelection = { browserViewModel.toggleSelection(it) },
                                    onSelectMultiple = { browserViewModel.selectMultiple(it) },
                                    onClearSelection = { browserViewModel.clearSelection() },
                                    onCreateFolder = { browserViewModel.createFolder(it) },
                                    onCreateFile = { browserViewModel.createFile(it) },
                                    onCreateFakeFile = { name, size -> browserViewModel.createFakeFile(name, size) },
                                    onRequestDeleteSelected = { browserViewModel.requestDeleteSelected() },
                                    onConfirmDelete = { browserViewModel.confirmDeleteSelected() },
                                    onTogglePermanentDelete = { browserViewModel.togglePermanentDelete() },
                                    onToggleShred = { browserViewModel.toggleShred() },
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
                                    onExtractArchive = { target, customDestination ->
                                        browserViewModel.extractArchive(target, customDestination)
                                    },
                                    onExtractSelectedArchiveEntries = { target, customDestination ->
                                        browserViewModel.extractSelectedArchiveEntries(target, customDestination)
                                    },
                                    onExtractCurrentArchiveFolder = { target, customDestination ->
                                        browserViewModel.extractCurrentArchiveFolder(target, customDestination)
                                    },
                                    onCreateZipFromSelection = { browserViewModel.createZipFromSelection() },
                                    onCreateArchiveFromSelection = { name, format, compressionLevel, password ->
                                        browserViewModel.createArchiveFromSelection(name, format, compressionLevel, password)
                                    },
                                    onSubmitArchivePassword = { password ->
                                        if (browserState.archiveContext?.pendingPasswordAction == dev.qtremors.arcile.feature.browser.ArchivePasswordAction.EXTRACT) {
                                            browserViewModel.submitArchiveExtractionPassword(password)
                                        } else {
                                            browserViewModel.submitArchivePassword(password)
                                        }
                                    },
                                    onDismissArchivePassword = { browserViewModel.dismissArchivePasswordPrompt() },
                                    onUndoLastTrashMove = { browserViewModel.undoLastTrashMove() },
                                    onClearPendingTrashUndo = { browserViewModel.clearPendingTrashUndo() },
                                    onUndoLastOperation = { browserViewModel.undoLastOperation() },
                                    onClearPendingUndo = { browserViewModel.clearPendingUndo() },
                                    onRetryRecoveredOperation = { browserViewModel.retryRecoveredOperation(it) },
                                    onCleanupRecoveredOperation = { browserViewModel.cleanupRecoveredOperation(it) },
                                    onDismissRecoveredOperation = { browserViewModel.dismissRecoveredOperation(it) },
                                    onFeedback = onFeedback,
                                    nativeRequestFlow = browserViewModel.nativeRequestFlow
                                )
                            }
                        }
                    }
                }
                composable<AppRoutes.StorageDashboard>(
                    enterTransition = detailEnterTransition,
                    exitTransition = detailExitTransition,
                    popEnterTransition = detailPopEnterTransition,
                    popExitTransition = detailPopExitTransition
                ) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry<AppRoutes.Main>()
                    }
                    val viewModel = hiltViewModel<HomeViewModel>(parentEntry)
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val route = backStackEntry.toRoute<AppRoutes.StorageDashboard>()
                    val volumeId = route.volumeId?.takeIf { it.isNotBlank() }
                    androidx.compose.runtime.LaunchedEffect(volumeId) {
                        viewModel.loadDashboardCategoryBreakdown(volumeId)
                    }
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
                trashScreen(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onFeedback = onFeedback
                )
                recentFilesScreen(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenFile = openPath,
                    onShareSelected = { paths ->
                        dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, paths)
                    },
                    onOpenContainingFolder = { path ->
                        navController.navigate(AppRoutes.Main(initialPage = 1, path = path, seedInitialPathHistory = false)) {
                            popUpTo<AppRoutes.Main> { inclusive = true }
                        }
                    },
                    onFeedback = onFeedback
                )
                composable<AppRoutes.Tools>(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition
                ) {
                    val viewModel = hiltViewModel<UtilityPreferencesViewModel>()
                    val homeUtilityIds by viewModel.homeUtilityIds.collectAsStateWithLifecycle()
                    ToolsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCleaner = { navController.navigate(AppRoutes.StorageCleaner) },
                        onNavigateToTrash = {
                            navController.navigate(AppRoutes.Trash) {
                                popUpTo<AppRoutes.Main> { saveState = true }
                                launchSingleTop = true
                            }
                        },
                        homeUtilityIds = homeUtilityIds,
                        onUtilityHomeVisibilityChange = viewModel::setUtilityShownOnHome
                    )
                }
                storageCleanerScreen(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onFeedback = onFeedback
                )
                composable<AppRoutes.Settings>(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition
                ) {
                    val viewModel = hiltViewModel<SettingsViewModel>()
                    val browserPrefs by viewModel.browserPreferences.collectAsStateWithLifecycle()
                    SettingsScreen(
                        currentThemeState = currentThemeState,
                        showThumbnails = browserPrefs.globalPresentation.showThumbnails,
                        homeRecentCarouselLimit = browserPrefs.homeRecentCarouselLimit,
                        showHiddenFiles = browserPrefs.showHiddenFiles,
                        onShowThumbnailsChange = { viewModel.updateShowThumbnails(it) },
                        onHomeRecentCarouselLimitChange = { viewModel.updateHomeRecentCarouselLimit(it) },
                        onShowHiddenFilesChange = { viewModel.updateShowHiddenFiles(it) },
                        onNavigateBack = { navController.popBackStack() },
                        onThemeChange = onThemeChange,
                        onOpenStorageManagement = { navController.navigate(AppRoutes.StorageManagement) },
                        onNavigateToAbout = { navController.navigate(AppRoutes.About) },
                        onRunOnboardingAgain = { viewModel.resetOnboarding() },
                        onRestartApp = onRestartApp
                    )
                }
                composable<AppRoutes.StorageManagement>(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition
                ) { backStackEntry ->
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
                composable<AppRoutes.About>(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition
                ) {
                    AboutScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate(AppRoutes.Licenses) }
                    )
                }
                composable<AppRoutes.Licenses>(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition
                ) {
                    LicensesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                quickAccessScreen(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPath = { path ->
                        navController.navigate(AppRoutes.Main(initialPage = 1, path = path, seedInitialPathHistory = false)) {
                            popUpTo<AppRoutes.Main> { inclusive = true }
                        }
                    },
                    onNavigateToSaf = { uriString ->
                        ExternalFileAccessHelper.openInFilesApp(context, uriString)
                    }
                )
                archiveViewerScreen(
                    enterTransition = detailEnterTransition,
                    exitTransition = detailExitTransition,
                    popEnterTransition = detailPopEnterTransition,
                    popExitTransition = detailPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenArchiveInBrowser = { archivePath ->
                        navController.navigate(AppRoutes.Main(initialPage = 1, archivePath = archivePath, seedInitialPathHistory = false)) {
                            popUpTo<AppRoutes.Main> { inclusive = true }
                        }
                    }
                )
            }
}
