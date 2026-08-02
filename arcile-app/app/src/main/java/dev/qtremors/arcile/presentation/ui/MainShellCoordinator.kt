package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import dev.qtremors.arcile.feature.browser.BrowserEntry
import dev.qtremors.arcile.feature.browser.BrowserEntryRequest
import dev.qtremors.arcile.feature.browser.BrowserRouteStatus
import dev.qtremors.arcile.navigation.AppRoutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

internal const val BROWSER_VIEWER_RETURN_PENDING_KEY = "browserViewerReturnPending"
private const val MAIN_PAGER_PAGE_KEY = "mainPagerPage"
private const val SHOW_BROWSER_PAGE_KEY = "showBrowserPage"
internal const val HOME_PAGE = 0
internal const val BROWSER_PAGE = 1
internal const val SECONDARY_BROWSER_PAGE = 2
private const val MAIN_PAGE_COUNT = 3

@Stable
internal class MainShellCoordinator(
    val pagerState: PagerState,
    initialBrowserEntry: BrowserEntryRequest?,
    private val savedStateHandle: SavedStateHandle,
    private val coroutineScope: CoroutineScope
) {
    private var requestId by mutableLongStateOf(initialBrowserEntry?.id ?: 0L)
    var browserEntryRequest by mutableStateOf(initialBrowserEntry)
        private set
    var browserStatuses by mutableStateOf<Map<Int, BrowserRouteStatus>>(emptyMap())
        private set

    fun requestBrowser(entry: BrowserEntry, focusPath: String? = null) {
        requestId += 1
        browserEntryRequest = BrowserEntryRequest(requestId, entry, focusPath)
        coroutineScope.launch { animateTo(BROWSER_PAGE) }
    }

    fun showHome() {
        coroutineScope.launch { animateTo(HOME_PAGE) }
    }

    fun showPrimaryBrowser() {
        coroutineScope.launch { animateTo(BROWSER_PAGE) }
    }

    fun updateBrowserStatus(page: Int, status: BrowserRouteStatus) {
        browserStatuses = browserStatuses + (page to status)
    }

    suspend fun coordinate(showBrowserPageRequests: StateFlow<Boolean>) {
        merge(
            showBrowserPageRequests
                .map(MainShellEvent::ShowBrowserRequested),
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .map(MainShellEvent::PageSettled)
        ).collect { event ->
            when (event) {
                is MainShellEvent.ShowBrowserRequested -> if (event.requested) {
                    val returnPage = savedStateHandle.get<Int>(MAIN_PAGER_PAGE_KEY)
                        ?.takeIf { it in BROWSER_PAGE..SECONDARY_BROWSER_PAGE }
                        ?: BROWSER_PAGE
                    pagerState.scrollToPage(returnPage)
                    savedStateHandle[SHOW_BROWSER_PAGE_KEY] = false
                }
                is MainShellEvent.PageSettled -> {
                    savedStateHandle[MAIN_PAGER_PAGE_KEY] = event.page
                    if (event.page in BROWSER_PAGE..SECONDARY_BROWSER_PAGE) {
                        savedStateHandle[BROWSER_VIEWER_RETURN_PENDING_KEY] = false
                    }
                }
            }
        }
    }

    private suspend fun animateTo(page: Int) {
        pagerState.animateScrollToPage(
            page = page,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
}

@Composable
internal fun rememberMainShellCoordinator(
    backStackEntry: NavBackStackEntry,
    mainArgs: AppRoutes.Main,
    coroutineScope: CoroutineScope
): MainShellCoordinator {
    val savedStateHandle = backStackEntry.savedStateHandle
    val pendingBrowserReturn = savedStateHandle.get<Boolean>(SHOW_BROWSER_PAGE_KEY) == true ||
        savedStateHandle.get<Boolean>(BROWSER_VIEWER_RETURN_PENDING_KEY) == true
    val initialPage = resolveInitialMainPage(
        requestedPage = mainArgs.initialPage,
        savedPage = savedStateHandle[MAIN_PAGER_PAGE_KEY],
        pendingBrowserReturn = pendingBrowserReturn
    )
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { MAIN_PAGE_COUNT }
    )
    return remember(backStackEntry.id, pagerState) {
        MainShellCoordinator(
            pagerState = pagerState,
            initialBrowserEntry = mainArgs.initialBrowserEntry(requestId = 0L),
            savedStateHandle = savedStateHandle,
            coroutineScope = coroutineScope
        )
    }
}

internal fun resolveInitialMainPage(
    requestedPage: Int,
    savedPage: Int?,
    pendingBrowserReturn: Boolean
): Int = when {
    pendingBrowserReturn -> savedPage
        ?.takeIf { it in BROWSER_PAGE..SECONDARY_BROWSER_PAGE }
        ?: BROWSER_PAGE
    requestedPage in BROWSER_PAGE..SECONDARY_BROWSER_PAGE -> requestedPage
    savedPage in HOME_PAGE..SECONDARY_BROWSER_PAGE -> requireNotNull(savedPage)
    else -> requestedPage.coerceIn(HOME_PAGE, SECONDARY_BROWSER_PAGE)
}

private sealed interface MainShellEvent {
    data class ShowBrowserRequested(val requested: Boolean) : MainShellEvent
    data class PageSettled(val page: Int) : MainShellEvent
}
