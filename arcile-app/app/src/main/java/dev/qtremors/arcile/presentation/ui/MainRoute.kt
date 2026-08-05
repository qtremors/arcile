package dev.qtremors.arcile.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.feature.browser.BrowserDestination
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRoute
import dev.qtremors.arcile.feature.home.HomeDestination
import dev.qtremors.arcile.feature.home.HomeRoute
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.R

@Composable
internal fun MainRoute(
    backStackEntry: NavBackStackEntry,
    mainArgs: AppRoutes.Main,
    hasPreviousRoute: Boolean,
    onHomeDestination: (HomeDestination) -> Unit,
    onBrowserDestination: (BrowserDestination) -> Unit,
    onShareBrowserFiles: suspend (List<String>, List<FileModel>) -> Boolean,
    appStartPage: AppStartPage,
    onAppStartPageChange: (AppStartPage) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val browserTabsViewModel = hiltViewModel<BrowserTabsViewModel>(backStackEntry)
    val restoredPinnedTabs by browserTabsViewModel.restoredTabs.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val coordinator = rememberMainShellCoordinator(
        backStackEntry = backStackEntry,
        mainArgs = mainArgs,
        coroutineScope = coroutineScope
    )
    val primaryTabs = rememberBrowserTabsCoordinator(
        browserPage = BROWSER_PAGE,
        backStackEntry = backStackEntry,
        requestedEntry = mainArgs.initialBrowserEntry(requestId = 0L),
        persistedPinnedTabs = restoredPinnedTabs,
        onPersistentTabsChanged = browserTabsViewModel::persistPinnedTabs
    )
    val secondaryTabs = rememberBrowserTabsCoordinator(
        browserPage = SECONDARY_BROWSER_PAGE,
        backStackEntry = backStackEntry,
        requestedEntry = null,
        persistedPinnedTabs = restoredPinnedTabs,
        onPersistentTabsChanged = browserTabsViewModel::persistPinnedTabs
    )
    LaunchedEffect(restoredPinnedTabs) {
        restoredPinnedTabs?.let { tabs ->
            primaryTabs.restorePersistentTabs(tabs)
            secondaryTabs.restorePersistentTabs(tabs)
        }
    }
    val showBrowserPageRequests = backStackEntry.savedStateHandle
        .getStateFlow("showBrowserPage", false)
    LaunchedEffect(coordinator, showBrowserPageRequests) {
        coordinator.coordinate(showBrowserPageRequests)
    }

    HorizontalPager(
        state = coordinator.pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true,
        beyondViewportPageCount = 1
    ) { page ->
        when (page) {
            HOME_PAGE -> HomeRoute(
                appStartPage = appStartPage,
                onAppStartPageChange = onAppStartPageChange,
                onDestination = { destination ->
                    when (destination) {
                        HomeDestination.BrowseRoot -> {
                            primaryTabs.requestBrowser(
                                BrowserEntry.Root(restorePersistentLocation = true)
                            )
                            coordinator.showPrimaryBrowser()
                        }
                        is HomeDestination.BrowsePath -> {
                            primaryTabs.requestBrowser(BrowserEntry.Path(destination.path))
                            coordinator.showPrimaryBrowser()
                        }
                        is HomeDestination.BrowseCategory -> onHomeDestination(destination)
                        else -> onHomeDestination(destination)
                    }
                }
            )
            BROWSER_PAGE -> BrowserWorkspacePage(
                browserPage = BROWSER_PAGE,
                coordinator = primaryTabs,
                isPageDisplayed = coordinator.pagerState.currentPage == BROWSER_PAGE,
                isInputActive = coordinator.pagerState.currentPage == BROWSER_PAGE,
                hasPreviousRoute = hasPreviousRoute,
                onExitToHome = coordinator::showHome,
                onExitToPreviousRoute = { tabId ->
                    if (tabId == PRIMARY_BROWSER_TAB_ID && hasPreviousRoute) {
                        onBrowserDestination(BrowserDestination.ExitToPreviousRoute)
                    } else {
                        coordinator.showHome()
                    }
                },
                onBrowserDestination = onBrowserDestination,
                onShareBrowserFiles = onShareBrowserFiles,
                appStartPage = appStartPage,
                onAppStartPageChange = onAppStartPageChange,
                onFeedback = onFeedback
            )
            SECONDARY_BROWSER_PAGE -> BrowserWorkspacePage(
                browserPage = SECONDARY_BROWSER_PAGE,
                coordinator = secondaryTabs,
                isPageDisplayed = coordinator.pagerState.currentPage == SECONDARY_BROWSER_PAGE,
                isInputActive = coordinator.pagerState.currentPage == SECONDARY_BROWSER_PAGE,
                hasPreviousRoute = false,
                onExitToHome = coordinator::showPrimaryBrowser,
                onExitToPreviousRoute = { coordinator.showPrimaryBrowser() },
                onBrowserDestination = onBrowserDestination,
                onShareBrowserFiles = onShareBrowserFiles,
                appStartPage = appStartPage,
                onAppStartPageChange = onAppStartPageChange,
                onFeedback = onFeedback
            )
        }
    }
}

@Composable
private fun BrowserWorkspacePage(
    browserPage: Int,
    coordinator: BrowserTabsCoordinator,
    isPageDisplayed: Boolean,
    isInputActive: Boolean,
    hasPreviousRoute: Boolean,
    onExitToHome: () -> Unit,
    onExitToPreviousRoute: (Int) -> Unit,
    onBrowserDestination: (BrowserDestination) -> Unit,
    onShareBrowserFiles: suspend (List<String>, List<FileModel>) -> Boolean,
    appStartPage: AppStartPage,
    onAppStartPageChange: (AppStartPage) -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val showTabLimitFeedback = {
        onFeedback(
            ArcileFeedbackEvent(
                message = UiText.StringResource(
                    R.string.browser_tab_limit_reached,
                    listOf(MAX_BROWSER_TABS)
                ),
                severity = ArcileFeedbackSeverity.Info
            )
        )
    }
    Box(modifier = modifier) {
        coordinator.tabs.forEach { tab ->
            val isActiveTab = tab.id == coordinator.activeBrowserTabId
            key(tab.id) {
                BrowserRoute(
                    viewModelKey = browserViewModelKey(browserPage, tab.id),
                    entryRequest = coordinator.browserEntryRequests[tab.id],
                    isVisible = isActiveTab && isInputActive,
                    hasPreviousRoute = isActiveTab &&
                        tab.id == PRIMARY_BROWSER_TAB_ID &&
                        hasPreviousRoute,
                    onStatusChange = { coordinator.updateBrowserStatus(tab.id, it) },
                    onDestination = { destination ->
                        when (destination) {
                            BrowserDestination.ExitToHome -> onExitToHome()
                            BrowserDestination.ExitToPreviousRoute -> onExitToPreviousRoute(tab.id)
                            else -> onBrowserDestination(destination)
                        }
                    },
                    onShareSelected = onShareBrowserFiles,
                    appStartPage = appStartPage,
                    onAppStartPageChange = onAppStartPageChange,
                    onFeedback = onFeedback,
                    workspaceTabs = {
                        val fallbackTitle = stringResource(R.string.browse_title)
                        val restoredTitles = coordinator.browserEntryRequests.mapValues { (_, request) ->
                            browserEntryTabTitle(request.entry, fallbackTitle)
                        }
                        BrowserTabsRow(
                            tabs = coordinator.tabs,
                            activeTabId = coordinator.activeBrowserTabId,
                            titles = restoredTitles + coordinator.browserStatuses.mapValues { it.value.title },
                            isRouteVisible = isInputActive,
                            onSelectTab = coordinator::showTab,
                            onNewTab = {
                                if (!coordinator.addTab()) showTabLimitFeedback()
                            },
                            onSetPinned = coordinator::setTabPinned,
                            onDuplicate = { tabId ->
                                if (!coordinator.duplicateTab(tabId)) showTabLimitFeedback()
                            },
                            onCloseOthers = coordinator::closeOtherTabs,
                            onClose = coordinator::closeTab
                        )
                    },
                    renderContent = isActiveTab,
                    initializeImmediately = isPageDisplayed
                )
            }
        }
    }
}

internal fun browserEntryTabTitle(entry: BrowserEntry, fallback: String): String = when (entry) {
    BrowserEntry.PrimaryStorage,
    is BrowserEntry.Root -> fallback
    is BrowserEntry.Path -> storagePathName(entry.path)
    is BrowserEntry.Category -> entry.name
    is BrowserEntry.Archive -> storagePathName(entry.path)
}

internal fun isGalleryCategory(categoryName: String): Boolean =
    FileCategories.Images.matches(categoryName) ||
        FileCategories.Videos.matches(categoryName)

internal fun isAudioCategory(categoryName: String): Boolean =
    FileCategories.Audio.matches(categoryName)

internal fun isDocumentCategory(categoryName: String): Boolean =
    FileCategories.Documents.matches(categoryName)

internal fun isApkCategory(categoryName: String): Boolean =
    FileCategories.APKs.matches(categoryName)

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
