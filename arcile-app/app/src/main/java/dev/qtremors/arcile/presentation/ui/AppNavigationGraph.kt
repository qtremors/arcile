package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.archive.registerArchiveViewerRoute
import dev.qtremors.arcile.feature.activitylog.registerActivityLogRoute
import dev.qtremors.arcile.feature.browser.ui.BrowserScreen
import dev.qtremors.arcile.feature.imagegallery.registerImageGalleryRoute
import dev.qtremors.arcile.feature.imagegallery.registerImageViewerRoute
import dev.qtremors.arcile.feature.trash.registerTrashRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.feature.browser.BrowserScrollPosition
import dev.qtremors.arcile.feature.browser.BrowserViewModel
import dev.qtremors.arcile.feature.browser.scrollPositionKey
import dev.qtremors.arcile.feature.home.HomeDestination
import dev.qtremors.arcile.feature.home.HomeRoute
import dev.qtremors.arcile.feature.quickaccess.QuickAccessViewModel
import dev.qtremors.arcile.feature.quickaccess.registerQuickAccessRoute
import dev.qtremors.arcile.feature.plugins.registerPluginsRoute
import dev.qtremors.arcile.feature.recentfiles.registerRecentFilesRoute
import dev.qtremors.arcile.feature.storagecleaner.registerStorageCleanerRoute
import dev.qtremors.arcile.feature.storageusage.StorageDashboardDestination
import dev.qtremors.arcile.feature.storageusage.registerStorageDashboardRoute
import dev.qtremors.arcile.feature.storageusage.registerStorageManagementRoute
import dev.qtremors.arcile.feature.settings.SettingsDestination
import dev.qtremors.arcile.feature.settings.registerSettingsRoute
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.ui.theme.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.OnboardingPreferencesStore
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.plugin.android.PluginFileResolution
import dev.qtremors.arcile.core.plugin.android.PluginManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource

@Composable
fun AppNavigationGraph(
    navController: NavHostController,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenFileWith: (String) -> Unit,
    onRestartApp: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val fileOpenResolver = remember(context) {
        AppFileOpenResolver(
            InstalledPluginFileResolutionGateway(PluginManager(context))
        )
    }
    var pluginPrompt by remember { mutableStateOf<PluginFileResolution?>(null) }
    val browserViewerReturnPendingKey = "browserViewerReturnPending"
    val navigateToBrowserRoute: (AppRoutes.Main) -> Unit = { route ->
        navController.navigate(route)
    }
    val openPathWithContext: (String, List<FileModel>, Boolean) -> Unit = { path, surroundingFiles, returnToBrowserPage ->
        coroutineScope.launch {
            when (val resolution = fileOpenResolver.resolve(path, surroundingFiles)) {
                AppFileOpenResolution.Handled -> Unit
                is AppFileOpenResolution.PluginPrompt -> pluginPrompt = resolution.prompt
                is AppFileOpenResolution.Failed -> onFeedback(
                    ArcileFeedbackEvent(
                        message = dev.qtremors.arcile.core.presentation.UiText.Dynamic(
                            context.getString(R.string.cannot_open_file, resolution.error.localizedMessage.orEmpty())
                        ),
                        severity = ArcileFeedbackSeverity.Error
                    )
                )
                is AppFileOpenResolution.BrowseArchive -> navigateToBrowserRoute(
                    AppRoutes.Main(
                        initialPage = 1,
                        archivePath = resolution.path,
                        seedInitialPathHistory = false
                    )
                )
                AppFileOpenResolution.UnsupportedArchive -> onFeedback(
                    ArcileFeedbackEvent(
                        message = dev.qtremors.arcile.core.presentation.UiText.StringResource(R.string.unsupported_archive_format),
                        severity = ArcileFeedbackSeverity.Error
                    )
                )
                is AppFileOpenResolution.ViewImage -> {
                    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
                    if (resolution.contextPaths.size > 1 && resolution.path in resolution.contextPaths) {
                        savedStateHandle?.set(
                            AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY,
                            ArrayList(resolution.contextPaths)
                        )
                    } else {
                        savedStateHandle?.remove<ArrayList<String>>(AppRoutes.IMAGE_VIEWER_CONTEXT_PATHS_KEY)
                    }
                    if (returnToBrowserPage) {
                        savedStateHandle?.set(browserViewerReturnPendingKey, true)
                    }
                    navController.navigate(
                        AppRoutes.ImageViewer(
                            initialPath = resolution.path,
                            returnToBrowserPage = returnToBrowserPage
                        )
                    )
                }
                is AppFileOpenResolution.External -> onOpenFile(resolution.path)
            }
        }
    }
    val openPath: (String) -> Unit = { path -> openPathWithContext(path, emptyList(), false) }
    val openPathWithSurroundingImages: (String, List<FileModel>) -> Unit = { path, files ->
        openPathWithContext(path, files, false)
    }
    val destinationMapper = FeatureDestinationMapper(
        navigateToBrowser = navigateToBrowserRoute,
        openPath = openPath,
        openExternalFolder = { uri -> ExternalFileAccessHelper.openInFilesApp(context, uri) }
    )
    val fileReferenceFor: (FileModel) -> ExternalFileAccessHelper.ExternalFileReference = { file ->
        ExternalFileAccessHelper.ExternalFileReference(
            path = file.absolutePath,
            displayName = file.name,
            sizeBytes = file.size,
            mimeType = file.mimeType,
            nodeRef = file.nodeRef
        )
    }
    val shareFilesWithKnownModels: suspend (List<String>, List<FileModel>) -> Boolean = { paths, files ->
        val byPath = files.associateBy { it.absolutePath }
        val references = paths.map { path ->
            byPath[path]?.let(fileReferenceFor) ?: ExternalFileAccessHelper.ExternalFileReference(path = path)
        }
        dev.qtremors.arcile.presentation.utils.ShareHelper.shareFileReferences(context, references)
    }

    val reducedMotion = LocalReducedMotionEnabled.current

    when (val prompt = pluginPrompt) {
        is PluginFileResolution.Missing -> {
            AlertDialog(
                onDismissRequest = { pluginPrompt = null },
                title = { Text(stringResource(R.string.plugin_missing_title, prompt.catalogEntry.name)) },
                text = { Text(stringResource(R.string.plugin_missing_message, prompt.catalogEntry.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        pluginPrompt = null
                        uriHandler.openUri(PluginManager.RELEASES_URL)
                    }) { Text(stringResource(R.string.install)) }
                },
                dismissButton = {
                    TextButton(onClick = { pluginPrompt = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        is PluginFileResolution.Incompatible -> {
            AlertDialog(
                onDismissRequest = { pluginPrompt = null },
                title = { Text(stringResource(R.string.plugin_incompatible_title)) },
                text = { Text(stringResource(R.string.plugin_incompatible_message, prompt.plugin.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        pluginPrompt = null
                        uriHandler.openUri(PluginManager.RELEASES_URL)
                    }) { Text(stringResource(R.string.view_releases)) }
                },
                dismissButton = {
                    TextButton(onClick = { pluginPrompt = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        else -> Unit
    }

    val detailEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val detailExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 1.04f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val detailPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 1.04f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val detailPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 0.94f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }

    val utilityEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else slideInVertically(initialOffsetY = { it / 8 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val utilityExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else fadeOut(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val utilityPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reducedMotion) fadeIn(tween(0)) else fadeIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val utilityPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reducedMotion) fadeOut(tween(0)) else slideOutVertically(targetOffsetY = { it / 8 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Main(),
        enterTransition = { if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) },
        exitTransition = { if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 1.04f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) },
        popEnterTransition = { if (reducedMotion) fadeIn(tween(0)) else slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 1.04f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) },
        popExitTransition = { if (reducedMotion) fadeOut(tween(0)) else slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 0.94f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    ) {
                composable<AppRoutes.Main> { backStackEntry ->
                    val mainArgs = backStackEntry.toRoute<AppRoutes.Main>()
                    val browserViewModel = hiltViewModel<BrowserViewModel>()
                    val quickAccessViewModel = hiltViewModel<QuickAccessViewModel>()
                    val browserState by browserViewModel.state.collectAsStateWithLifecycle()
                    val showBrowserPageRequest by backStackEntry.savedStateHandle
                        .getStateFlow("showBrowserPage", false)
                        .collectAsStateWithLifecycle()
                    val pendingBrowserPageReturn =
                        backStackEntry.savedStateHandle.get<Boolean>("showBrowserPage") == true ||
                            backStackEntry.savedStateHandle.get<Boolean>(browserViewerReturnPendingKey) == true

                    val requestedInitialMainPage = if (pendingBrowserPageReturn || mainArgs.initialPage == 1) {
                        1
                    } else {
                        mainArgs.initialPage
                    }
                    var savedMainPagerPage by rememberSaveable(backStackEntry.id) {
                        mutableStateOf(requestedInitialMainPage)
                    }
                    val pagerState = androidx.compose.runtime.key(backStackEntry.id) {
                        rememberPagerState(
                            initialPage = savedMainPagerPage,
                            pageCount = { 2 }
                        )
                    }
                    androidx.compose.runtime.LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }.collect { page ->
                            savedMainPagerPage = page
                        }
                    }
                    val browserListState = rememberSaveable(saver = LazyListState.Saver) {
                        LazyListState()
                    }
                    val browserGridState = rememberSaveable(saver = LazyGridState.Saver) {
                        LazyGridState()
                    }
                    val browserScrollPositionKey = browserState.scrollPositionKey()
                    val saveBrowserScrollPosition: () -> Unit = {
                        browserViewModel.saveScrollPosition(
                            browserScrollPositionKey,
                            BrowserScrollPosition(
                                listIndex = browserListState.firstVisibleItemIndex,
                                listOffset = browserListState.firstVisibleItemScrollOffset,
                                gridIndex = browserGridState.firstVisibleItemIndex,
                                gridOffset = browserGridState.firstVisibleItemScrollOffset
                            )
                        )
                    }
                    val hasActiveBrowserLocation =
                        browserState.currentPath.isNotBlank() ||
                            browserState.isVolumeRootScreen ||
                            browserState.isCategoryScreen ||
                            browserState.archiveContext != null
                    val coroutineScope = rememberCoroutineScope()
                    var pendingExplicitBrowserEntry by remember {
                        mutableStateOf(mainArgs.initialPage == 1 || pendingBrowserPageReturn)
                    }
                    var revealedFocusPath by rememberSaveable(mainArgs.focusPath) { mutableStateOf<String?>(null) }
                    val navigateBackFromBrowser: () -> Unit = {
                        val hasPreviousRoute = navController.previousBackStackEntry != null
                        if (!browserViewModel.navigateBack(allowVolumeRootFallback = !hasPreviousRoute)) {
                            when (browserBackFallback(hasPreviousRoute)) {
                                BrowserBackFallback.PopAppBackStack -> navController.popBackStack()
                                BrowserBackFallback.ShowHomePager -> coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = 0,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                    )
                                }
                            }
                        }
                    }

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

                    androidx.compose.runtime.LaunchedEffect(
                        mainArgs.focusPath,
                        pagerState.currentPage,
                        browserState.displayState.visibleFiles
                    ) {
                        val focusPath = mainArgs.focusPath
                        if (
                            focusPath != null &&
                            revealedFocusPath != focusPath &&
                            pagerState.currentPage == 1
                        ) {
                            val index = browserState.displayState.visibleFiles.indexOfFirst { it.absolutePath == focusPath }
                            if (index >= 0) {
                                browserListState.scrollToItem(index)
                                browserGridState.scrollToItem(index)
                                revealedFocusPath = focusPath
                            }
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(showBrowserPageRequest) {
                        if (showBrowserPageRequest) {
                            pendingExplicitBrowserEntry = true
                            pagerState.scrollToPage(1)
                            backStackEntry.savedStateHandle["showBrowserPage"] = false
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage == 1) {
                            if (
                                pendingExplicitBrowserEntry ||
                                backStackEntry.savedStateHandle.get<Boolean>("showBrowserPage") == true ||
                                backStackEntry.savedStateHandle.get<Boolean>(browserViewerReturnPendingKey) == true
                            ) {
                                pendingExplicitBrowserEntry = false
                                backStackEntry.savedStateHandle[browserViewerReturnPendingKey] = false
                            } else if (!hasActiveBrowserLocation) {
                                browserViewModel.openFileBrowser(restorePersistentLocation = true)
                            }
                        }
                    }

                    val isBrowserAtRoot = !hasActiveBrowserLocation
                    var backProgress by remember { mutableStateOf(0f) }
                    var isBackPredicting by remember { mutableStateOf(false) }

                    BackHandler(enabled = pagerState.currentPage == 1 && !isBrowserAtRoot) {
                        navigateBackFromBrowser()
                    }

                    PredictiveBackHandler(enabled = pagerState.currentPage == 1 && isBrowserAtRoot) { progressFlow ->
                        isBackPredicting = true
                        try {
                            progressFlow.collect { backEvent ->
                                backProgress = backEvent.progress
                            }
                            navigateBackFromBrowser()
                        } finally {
                            isBackPredicting = false
                            backProgress = 0f
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !(pagerState.currentPage == 1 && browserState.isCategoryScreen),
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (page) {
                            0 -> {
                                HomeRoute(
                                    onDestination = { destination ->
                                        when (destination) {
                                            HomeDestination.BrowseRoot -> {
                                                pendingExplicitBrowserEntry = true
                                                browserViewModel.openFileBrowser(restorePersistentLocation = false)
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        page = 1,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                            }
                                            is HomeDestination.BrowsePath -> {
                                                pendingExplicitBrowserEntry = true
                                                browserViewModel.navigateToSpecificFolder(destination.path)
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(
                                                        page = 1,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                            }
                                            is HomeDestination.OpenFile -> {
                                                openPathWithSurroundingImages(destination.path, destination.context)
                                            }
                                            is HomeDestination.BrowseCategory -> {
                                                if (destination.name == FileCategories.Images.name) {
                                                    navController.navigate(AppRoutes.ImageGallery()) {
                                                        popUpTo<AppRoutes.Main> { saveState = true }
                                                        launchSingleTop = true
                                                    }
                                                } else {
                                                    pendingExplicitBrowserEntry = true
                                                    browserViewModel.navigateToCategory(destination.name)
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(
                                                            page = 1,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            HomeDestination.Settings -> navController.navigate(AppRoutes.Settings)
                                            HomeDestination.Tools -> navController.navigate(AppRoutes.Tools) {
                                                popUpTo<AppRoutes.Main> { saveState = true }
                                                launchSingleTop = true
                                            }
                                            HomeDestination.About -> navController.navigate(AppRoutes.About)
                                            HomeDestination.Trash -> navController.navigate(AppRoutes.Trash) {
                                                popUpTo<AppRoutes.Main> { saveState = true }
                                                launchSingleTop = true
                                            }
                                            HomeDestination.RecentFiles -> navController.navigate(AppRoutes.RecentFiles()) {
                                                popUpTo<AppRoutes.Main> { saveState = true }
                                                launchSingleTop = true
                                            }
                                            HomeDestination.QuickAccess -> navController.navigate(AppRoutes.QuickAccess)
                                            is HomeDestination.ExternalFolder -> {
                                                if (!ExternalFileAccessHelper.openInFilesApp(context, destination.uri)) {
                                                    onFeedback(
                                                        ArcileFeedbackEvent(
                                                            message = dev.qtremors.arcile.core.presentation.UiText.StringResource(
                                                                R.string.could_not_open_folder_files_app
                                                            ),
                                                            severity = ArcileFeedbackSeverity.Error
                                                        )
                                                    )
                                                }
                                            }
                                            is HomeDestination.StorageDashboard -> {
                                                navController.navigate(AppRoutes.StorageDashboard(destination.volumeId)) {
                                                    popUpTo<AppRoutes.Main> { saveState = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                            HomeDestination.Cleaner -> navController.navigate(AppRoutes.StorageCleaner) {
                                                popUpTo<AppRoutes.Main> { saveState = true }
                                                launchSingleTop = true
                                            }
                                            HomeDestination.ActivityLog -> navController.navigate(AppRoutes.ActivityLog) {
                                                popUpTo<AppRoutes.Main> { saveState = true }
                                                launchSingleTop = true
                                            }
                                            is HomeDestination.ShareRecentFile -> coroutineScope.launch {
                                                shareFilesWithKnownModels(
                                                    listOf(destination.path),
                                                    destination.context
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            1 -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (isBackPredicting && pagerState.currentPage == 1) {
                                                val scale = 1f - (backProgress * 0.08f)
                                                scaleX = scale
                                                scaleY = scale
                                                translationX = backProgress * size.width.toFloat()
                                                alpha = 1f - (backProgress * 0.4f)
                                            }
                                        }
                                ) {
                                    BrowserScreen(
                                        state = browserState,
                                        onNavigateBack = navigateBackFromBrowser,
                                    onNavigateTo = { browserViewModel.navigateToFolder(it) },
                                    onOpenFile = { path ->
                                        saveBrowserScrollPosition()
                                        if (ArchiveFormat.isSupported(path)) {
                                            browserViewModel.openArchive(path)
                                        } else {
                                            browserViewModel.requestOpenedFileReveal(path)
                                            val browserImageContext = if (browserState.browserSearchQuery.isNotBlank()) {
                                                browserState.searchResults
                                            } else {
                                                browserState.displayState.visibleFiles
                                            }
                                            openPathWithContext(path, browserImageContext, true)
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
                                            val browserShareFiles = if (browserState.browserSearchQuery.isNotBlank()) {
                                                browserState.searchResults
                                            } else {
                                                browserState.displayState.visibleFiles
                                            }
                                            if (shareFilesWithKnownModels(browserState.selectedFiles.toList(), browserShareFiles)) {
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
                                    nativeRequestFlow = browserViewModel.nativeRequestFlow,
                                    listState = browserListState,
                                    gridState = browserGridState,
                                    scrollPositionKey = browserScrollPositionKey,
                                    savedScrollPosition = browserViewModel.savedScrollPosition(browserScrollPositionKey),
                                    savedScrollPositionProvider = browserViewModel::savedScrollPosition,
                                    onSaveScrollPosition = browserViewModel::saveScrollPosition,
                                    onClearScrollPosition = browserViewModel::clearScrollPosition,
                                    pendingRevealFilePath = browserState.pendingRevealFilePath,
                                    pendingRevealReady = browserState.pendingRevealReady,
                                    onArmPendingReveal = browserViewModel::armOpenedFileReveal,
                                    onConsumePendingReveal = browserViewModel::consumeOpenedFileReveal
                                )
                            }
                        }
                    }
                    }
                }
                registerStorageDashboardRoute(
                    enterTransition = detailEnterTransition,
                    exitTransition = detailExitTransition,
                    popEnterTransition = detailPopEnterTransition,
                    popExitTransition = detailPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onDestination = { destination ->
                        when (destination) {
                            is StorageDashboardDestination.Category -> {
                                if (destination.name == FileCategories.Images.name) {
                                    navController.navigate(AppRoutes.ImageGallery(destination.volumeId))
                                } else {
                                    navigateToBrowserRoute(
                                        AppRoutes.Main(
                                            initialPage = 1,
                                            category = destination.name,
                                            volumeId = destination.volumeId
                                        )
                                    )
                                }
                            }
                            is StorageDashboardDestination.Path -> {
                                navigateToBrowserRoute(
                                    AppRoutes.Main(initialPage = 1, path = destination.path)
                                )
                            }
                            is StorageDashboardDestination.File -> openPath(destination.path)
                        }
                    }
                )
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
                registerTrashRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onFeedback = onFeedback
                )
                registerRecentFilesRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenFile = openPathWithSurroundingImages,
                    onShareSelected = { files ->
                        shareFilesWithKnownModels(files.map { it.absolutePath }, files)
                    },
                    onDestination = destinationMapper::fromRecentFiles,
                    onFeedback = onFeedback
                )
                registerImageGalleryRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onDestination = destinationMapper::fromGallery,
                    onShareSelected = { files ->
                        shareFilesWithKnownModels(files.map { it.absolutePath }, files)
                    },
                    onFeedback = onFeedback
                )
                registerImageViewerRoute(
                    navController = navController,
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onShareFile = { path ->
                        coroutineScope.launch {
                            dev.qtremors.arcile.presentation.utils.ShareHelper.shareFiles(context, listOf(path))
                        }
                    },
                    onOpenFileWith = onOpenFileWith
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
                        onNavigateToActivity = { navController.navigate(AppRoutes.ActivityLog) },
                        homeUtilityIds = homeUtilityIds,
                        onUtilityHomeVisibilityChange = viewModel::setUtilityShownOnHome
                    )
                }
                registerActivityLogRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() }
                )
                registerStorageCleanerRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onDestination = destinationMapper::fromStorageCleaner,
                    onFeedback = onFeedback
                )
                registerSettingsRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    currentThemeState = currentThemeState,
                    onThemeChange = onThemeChange,
                    onNavigateBack = { navController.popBackStack() },
                    onDestination = { destination ->
                        when (destination) {
                            SettingsDestination.StorageManagement -> {
                                navController.navigate(AppRoutes.StorageManagement)
                            }
                            SettingsDestination.Plugins -> navController.navigate(AppRoutes.Plugins)
                            SettingsDestination.About -> navController.navigate(AppRoutes.About)
                        }
                    },
                    onRestartApp = onRestartApp
                )
                registerPluginsRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() }
                )
                registerStorageManagementRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() }
                )
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
                registerQuickAccessRoute(
                    enterTransition = utilityEnterTransition,
                    exitTransition = utilityExitTransition,
                    popEnterTransition = utilityPopEnterTransition,
                    popExitTransition = utilityPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onDestination = destinationMapper::fromQuickAccess
                )
                registerArchiveViewerRoute(
                    enterTransition = detailEnterTransition,
                    exitTransition = detailExitTransition,
                    popEnterTransition = detailPopEnterTransition,
                    popExitTransition = detailPopExitTransition,
                    onNavigateBack = { navController.popBackStack() },
                    onDestination = destinationMapper::fromArchive
                )
            }
}
