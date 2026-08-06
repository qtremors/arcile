package dev.qtremors.arcile.presentation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
    landscapeDualPaneEnabled: Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val browserTabsViewModel = hiltViewModel<BrowserTabsViewModel>(backStackEntry)
    val restoredPinnedTabs by browserTabsViewModel.restoredTabs.collectAsStateWithLifecycle()
    val tabsEnabled by browserTabsViewModel.tabsEnabled.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val dualPaneEnabled = landscapeDualPaneEnabled &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val coordinator = rememberMainShellCoordinator(
        backStackEntry = backStackEntry,
        mainArgs = mainArgs,
        coroutineScope = coroutineScope,
        dualPaneEnabled = dualPaneEnabled
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
    var focusedBrowserPage by rememberSaveable {
        mutableIntStateOf(BROWSER_PAGE)
    }
    var lastSinglePaneBrowserPage by rememberSaveable {
        mutableIntStateOf(BROWSER_PAGE)
    }
    var dualPaneBrowserWasVisible by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(coordinator.pagerState, dualPaneEnabled) {
        snapshotFlow { coordinator.pagerState.settledPage }.collect { page ->
            if (dualPaneEnabled) {
                dualPaneBrowserWasVisible = page == BROWSER_PAGE
            } else if (page in BROWSER_PAGE..SECONDARY_BROWSER_PAGE) {
                lastSinglePaneBrowserPage = page
            }
        }
    }
    LaunchedEffect(dualPaneEnabled) {
        if (dualPaneEnabled) {
            focusedBrowserPage = lastSinglePaneBrowserPage
            if (coordinator.pagerState.currentPage != HOME_PAGE) {
                coordinator.pagerState.scrollToPage(BROWSER_PAGE)
            }
        } else if (
            dualPaneBrowserWasVisible &&
            coordinator.pagerState.currentPage == BROWSER_PAGE
        ) {
            coordinator.pagerState.scrollToPage(focusedBrowserPage)
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
        beyondViewportPageCount = 1,
        key = { page ->
            when (page) {
                HOME_PAGE -> "home"
                BROWSER_PAGE -> if (dualPaneEnabled) "browser-dual" else "browser-primary"
                else -> "browser-secondary"
            }
        }
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
            BROWSER_PAGE -> if (dualPaneEnabled) {
                val browserSpreadVisible = coordinator.pagerState.currentPage == BROWSER_PAGE
                Row(modifier = Modifier.fillMaxSize()) {
                    BrowserWorkspacePage(
                        browserPage = BROWSER_PAGE,
                        coordinator = primaryTabs,
                        isPageDisplayed = browserSpreadVisible,
                        isInputActive = browserSpreadVisible && focusedBrowserPage == BROWSER_PAGE,
                        hasPreviousRoute = hasPreviousRoute,
                        onPaneFocus = { focusedBrowserPage = BROWSER_PAGE },
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
                        tabsEnabled = tabsEnabled,
                        onTabsEnabledChange = browserTabsViewModel::setTabsEnabled,
                        onFeedback = onFeedback,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    BrowserWorkspacePage(
                        browserPage = SECONDARY_BROWSER_PAGE,
                        coordinator = secondaryTabs,
                        isPageDisplayed = browserSpreadVisible,
                        isInputActive = browserSpreadVisible &&
                            focusedBrowserPage == SECONDARY_BROWSER_PAGE,
                        hasPreviousRoute = false,
                        onPaneFocus = { focusedBrowserPage = SECONDARY_BROWSER_PAGE },
                        onExitToHome = { focusedBrowserPage = BROWSER_PAGE },
                        onExitToPreviousRoute = { focusedBrowserPage = BROWSER_PAGE },
                        onBrowserDestination = onBrowserDestination,
                        onShareBrowserFiles = onShareBrowserFiles,
                        appStartPage = appStartPage,
                        onAppStartPageChange = onAppStartPageChange,
                        tabsEnabled = tabsEnabled,
                        onTabsEnabledChange = browserTabsViewModel::setTabsEnabled,
                        onFeedback = onFeedback,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else BrowserWorkspacePage(
                browserPage = BROWSER_PAGE,
                coordinator = primaryTabs,
                isPageDisplayed = coordinator.pagerState.currentPage == BROWSER_PAGE,
                isInputActive = coordinator.pagerState.currentPage == BROWSER_PAGE,
                hasPreviousRoute = hasPreviousRoute,
                onPaneFocus = {},
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
                tabsEnabled = tabsEnabled,
                onTabsEnabledChange = browserTabsViewModel::setTabsEnabled,
                onFeedback = onFeedback
            )
            SECONDARY_BROWSER_PAGE -> BrowserWorkspacePage(
                browserPage = SECONDARY_BROWSER_PAGE,
                coordinator = secondaryTabs,
                isPageDisplayed = coordinator.pagerState.currentPage == SECONDARY_BROWSER_PAGE,
                isInputActive = coordinator.pagerState.currentPage == SECONDARY_BROWSER_PAGE,
                hasPreviousRoute = false,
                onPaneFocus = {},
                onExitToHome = coordinator::showPrimaryBrowser,
                onExitToPreviousRoute = { coordinator.showPrimaryBrowser() },
                onBrowserDestination = onBrowserDestination,
                onShareBrowserFiles = onShareBrowserFiles,
                appStartPage = appStartPage,
                onAppStartPageChange = onAppStartPageChange,
                tabsEnabled = tabsEnabled,
                onTabsEnabledChange = browserTabsViewModel::setTabsEnabled,
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
    onPaneFocus: () -> Unit,
    onExitToHome: () -> Unit,
    onExitToPreviousRoute: (Int) -> Unit,
    onBrowserDestination: (BrowserDestination) -> Unit,
    onShareBrowserFiles: suspend (List<String>, List<FileModel>) -> Boolean,
    appStartPage: AppStartPage,
    onAppStartPageChange: (AppStartPage) -> Unit,
    tabsEnabled: Boolean,
    onTabsEnabledChange: (Boolean) -> Unit,
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
    Box(
        modifier = modifier.pointerInput(browserPage) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { change -> change.pressed && !change.previousPressed }) {
                        onPaneFocus()
                    }
                }
            }
        }
    ) {
        coordinator.tabs.forEach { tab ->
            val isActiveTab = tab.id == coordinator.activeBrowserTabId
            if (isActiveTab || isPageDisplayed) key(tab.id) {
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
                        if (tabsEnabled) {
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
                                onMoveLeft = { tabId -> coordinator.moveTab(tabId, offset = -1) },
                                onMoveRight = { tabId -> coordinator.moveTab(tabId, offset = 1) },
                                onCloseOthers = coordinator::closeOtherTabs,
                                onClose = coordinator::closeTab
                            )
                        }
                    },
                    workspaceTabsEnabled = tabsEnabled,
                    onWorkspaceTabsEnabledChange = onTabsEnabledChange,
                    renderContent = isActiveTab
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
