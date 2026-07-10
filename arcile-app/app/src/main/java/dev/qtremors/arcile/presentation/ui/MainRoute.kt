package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.browser.BrowserDestination
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRoute
import dev.qtremors.arcile.feature.browser.BrowserRouteStatus
import dev.qtremors.arcile.feature.home.HomeDestination
import dev.qtremors.arcile.feature.home.HomeRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val BROWSER_VIEWER_RETURN_PENDING_KEY = "browserViewerReturnPending"

@Composable
internal fun MainRoute(
    backStackEntry: NavBackStackEntry,
    mainArgs: AppRoutes.Main,
    hasPreviousRoute: Boolean,
    coroutineScope: CoroutineScope,
    onHomeDestination: (HomeDestination) -> Unit,
    onBrowserDestination: (BrowserDestination) -> Unit,
    onShareBrowserFiles: suspend (List<String>, List<FileModel>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val showBrowserPageRequest by backStackEntry.savedStateHandle
        .getStateFlow("showBrowserPage", false)
        .collectAsStateWithLifecycle()
    val pendingBrowserPageReturn =
        backStackEntry.savedStateHandle.get<Boolean>("showBrowserPage") == true ||
            backStackEntry.savedStateHandle.get<Boolean>(BROWSER_VIEWER_RETURN_PENDING_KEY) == true
    val requestedInitialPage = if (pendingBrowserPageReturn || mainArgs.initialPage == BROWSER_PAGE) {
        BROWSER_PAGE
    } else {
        mainArgs.initialPage
    }
    var savedMainPagerPage by rememberSaveable(backStackEntry.id) {
        mutableStateOf(requestedInitialPage)
    }
    val pagerState = androidx.compose.runtime.key(backStackEntry.id) {
        rememberPagerState(
            initialPage = savedMainPagerPage,
            pageCount = { MAIN_PAGE_COUNT }
        )
    }
    var requestId by remember(backStackEntry.id) { mutableLongStateOf(0L) }
    var browserEntryRequest by remember(backStackEntry.id) {
        mutableStateOf(mainArgs.initialBrowserEntry(requestId))
    }
    var browserStatus by remember { mutableStateOf(BrowserRouteStatus()) }

    fun requestBrowser(entry: BrowserEntry, focusPath: String? = null) {
        requestId += 1
        browserEntryRequest = BrowserEntryRequest(requestId, entry, focusPath)
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = BROWSER_PAGE,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            savedMainPagerPage = page
        }
    }

    LaunchedEffect(showBrowserPageRequest) {
        if (showBrowserPageRequest) {
            pagerState.scrollToPage(BROWSER_PAGE)
            backStackEntry.savedStateHandle["showBrowserPage"] = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == BROWSER_PAGE) {
            backStackEntry.savedStateHandle[BROWSER_VIEWER_RETURN_PENDING_KEY] = false
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !(pagerState.currentPage == BROWSER_PAGE && browserStatus.isCategoryScreen),
        beyondViewportPageCount = 1
    ) { page ->
        when (page) {
            HOME_PAGE -> HomeRoute(
                onDestination = { destination ->
                    when (destination) {
                        HomeDestination.BrowseRoot -> requestBrowser(
                            BrowserEntry.Root(restorePersistentLocation = false)
                        )
                        is HomeDestination.BrowsePath -> requestBrowser(
                            BrowserEntry.Path(destination.path)
                        )
                        is HomeDestination.BrowseCategory -> {
                            if (destination.name == FileCategories.Images.name) {
                                onHomeDestination(destination)
                            } else {
                                requestBrowser(BrowserEntry.Category(destination.name))
                            }
                        }
                        else -> onHomeDestination(destination)
                    }
                }
            )
            BROWSER_PAGE -> BrowserRoute(
                entryRequest = browserEntryRequest,
                isVisible = pagerState.currentPage == BROWSER_PAGE,
                hasPreviousRoute = hasPreviousRoute,
                onStatusChange = { browserStatus = it },
                onDestination = { destination ->
                    if (destination == BrowserDestination.ExitToHome) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = HOME_PAGE,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    } else {
                        onBrowserDestination(destination)
                    }
                },
                onShareSelected = onShareBrowserFiles,
                onFeedback = onFeedback
            )
        }
    }
}

internal fun AppRoutes.Main.initialBrowserEntry(requestId: Long): BrowserEntryRequest? {
    if (initialPage != BROWSER_PAGE) return null
    val requestedArchivePath = archivePath
    val requestedPath = path
    val requestedCategory = category
    val entry = when {
        !requestedArchivePath.isNullOrEmpty() -> BrowserEntry.Archive(requestedArchivePath)
        !requestedPath.isNullOrEmpty() -> BrowserEntry.Path(
            path = requestedPath,
            seedInitialPathHistory = seedInitialPathHistory
        )
        !requestedCategory.isNullOrEmpty() -> BrowserEntry.Category(requestedCategory, volumeId)
        else -> BrowserEntry.Root(restorePersistentLocation)
    }
    return BrowserEntryRequest(
        id = requestId,
        entry = entry,
        focusPath = focusPath
    )
}

private const val HOME_PAGE = 0
private const val BROWSER_PAGE = 1
private const val MAIN_PAGE_COUNT = 2
