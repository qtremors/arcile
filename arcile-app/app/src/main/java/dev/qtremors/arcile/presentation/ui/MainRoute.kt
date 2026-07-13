package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.browser.BrowserDestination
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRoute
import dev.qtremors.arcile.feature.home.HomeDestination
import dev.qtremors.arcile.feature.home.HomeRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent

@Composable
internal fun MainRoute(
    backStackEntry: NavBackStackEntry,
    mainArgs: AppRoutes.Main,
    hasPreviousRoute: Boolean,
    onHomeDestination: (HomeDestination) -> Unit,
    onBrowserDestination: (BrowserDestination) -> Unit,
    onShareBrowserFiles: suspend (List<String>, List<FileModel>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val coordinator = rememberMainShellCoordinator(backStackEntry, mainArgs, coroutineScope)
    val showBrowserPageRequests = backStackEntry.savedStateHandle
        .getStateFlow("showBrowserPage", false)
    LaunchedEffect(coordinator, showBrowserPageRequests) {
        coordinator.coordinate(showBrowserPageRequests)
    }

    HorizontalPager(
        state = coordinator.pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !(
            coordinator.pagerState.currentPage == BROWSER_PAGE &&
                coordinator.browserStatus.isCategoryScreen
        ),
        beyondViewportPageCount = 1
    ) { page ->
        when (page) {
            HOME_PAGE -> HomeRoute(
                onDestination = { destination ->
                    when (destination) {
                        HomeDestination.BrowseRoot -> coordinator.requestBrowser(
                            BrowserEntry.Root(restorePersistentLocation = false)
                        )
                        is HomeDestination.BrowsePath -> coordinator.requestBrowser(
                            BrowserEntry.Path(destination.path)
                        )
                        is HomeDestination.BrowseCategory -> {
                            if (destination.name == FileCategories.Images.name) {
                                onHomeDestination(destination)
                            } else {
                                coordinator.requestBrowser(BrowserEntry.Category(destination.name))
                            }
                        }
                        else -> onHomeDestination(destination)
                    }
                }
            )
            BROWSER_PAGE -> BrowserRoute(
                entryRequest = coordinator.browserEntryRequest,
                isVisible = coordinator.pagerState.currentPage == BROWSER_PAGE,
                hasPreviousRoute = hasPreviousRoute,
                onStatusChange = coordinator::updateBrowserStatus,
                onDestination = { destination ->
                    if (destination == BrowserDestination.ExitToHome) {
                        coordinator.showHome()
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
