package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
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
private const val DUAL_PANE_MAIN_PAGE_COUNT = 2

@Stable
internal class MainShellCoordinator(
    val pagerState: PagerState,
    private val savedStateHandle: SavedStateHandle,
    private val coroutineScope: CoroutineScope
) {
    fun showHome() {
        coroutineScope.launch { animateTo(HOME_PAGE) }
    }

    fun showPrimaryBrowser() {
        coroutineScope.launch { animateTo(BROWSER_PAGE) }
    }

    fun showRelativeTo(page: Int, direction: Int) {
        val target = (page + direction).coerceIn(HOME_PAGE, pagerState.pageCount - 1)
        if (target != page) coroutineScope.launch { animateTo(target) }
    }

    suspend fun coordinate(showBrowserPageRequests: StateFlow<Boolean>) {
        merge(
            showBrowserPageRequests.map(MainShellEvent::ShowBrowserRequested),
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .map(MainShellEvent::PageSettled)
        ).collect { event ->
            when (event) {
                is MainShellEvent.ShowBrowserRequested -> if (event.requested) {
                    val lastBrowserPage = (pagerState.pageCount - 1)
                        .coerceIn(BROWSER_PAGE, SECONDARY_BROWSER_PAGE)
                    val returnPage = savedStateHandle.get<Int>(MAIN_PAGER_PAGE_KEY)
                        ?.takeIf { it in BROWSER_PAGE..lastBrowserPage }
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
    coroutineScope: CoroutineScope,
    dualPaneEnabled: Boolean
): MainShellCoordinator {
    val savedStateHandle = backStackEntry.savedStateHandle
    val pendingBrowserReturn = savedStateHandle.get<Boolean>(SHOW_BROWSER_PAGE_KEY) == true ||
        savedStateHandle.get<Boolean>(BROWSER_VIEWER_RETURN_PENDING_KEY) == true
    val initialPage = resolveInitialMainPage(
        requestedPage = mainArgs.initialPage,
        savedPage = savedStateHandle[MAIN_PAGER_PAGE_KEY],
        pendingBrowserReturn = pendingBrowserReturn,
        dualPaneEnabled = dualPaneEnabled
    )
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { mainPageCount(dualPaneEnabled) }
    )
    return remember(backStackEntry.id, pagerState) {
        MainShellCoordinator(
            pagerState = pagerState,
            savedStateHandle = savedStateHandle,
            coroutineScope = coroutineScope
        )
    }
}

internal fun resolveInitialMainPage(
    requestedPage: Int,
    savedPage: Int?,
    pendingBrowserReturn: Boolean,
    dualPaneEnabled: Boolean = false
): Int {
    val resolved = when {
        pendingBrowserReturn -> savedPage
        ?.takeIf { it in BROWSER_PAGE..SECONDARY_BROWSER_PAGE }
        ?: BROWSER_PAGE
        requestedPage in BROWSER_PAGE..SECONDARY_BROWSER_PAGE -> requestedPage
        savedPage in HOME_PAGE..SECONDARY_BROWSER_PAGE -> requireNotNull(savedPage)
        else -> requestedPage.coerceIn(HOME_PAGE, SECONDARY_BROWSER_PAGE)
    }
    return if (dualPaneEnabled && resolved == SECONDARY_BROWSER_PAGE) BROWSER_PAGE else resolved
}

internal fun mainPageCount(dualPaneEnabled: Boolean): Int =
    if (dualPaneEnabled) DUAL_PANE_MAIN_PAGE_COUNT else MAIN_PAGE_COUNT

private sealed interface MainShellEvent {
    data class ShowBrowserRequested(val requested: Boolean) : MainShellEvent
    data class PageSettled(val page: Int) : MainShellEvent
}
