package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.feature.browser.BrowserScrollPosition
import dev.qtremors.arcile.feature.browser.BrowserUiState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun BrowserScrollEffects(
    state: BrowserUiState,
    scroll: BrowserScrollBindings,
    resumeRestoreTick: Int
) {
    val displayedFiles = state.displayState.visibleFiles
    val pendingRevealIndex = remember(
        scroll.pendingRevealFilePath,
        scroll.pendingRevealReady,
        displayedFiles
    ) {
        scroll.pendingRevealFilePath
            ?.takeIf { scroll.pendingRevealReady }
            ?.let { revealPath -> displayedFiles.indexOfFirst { it.absolutePath == revealPath } }
            ?.takeIf { it >= 0 }
    }
    if (scroll.pendingRevealReady && pendingRevealIndex != null) {
        if (state.browserViewMode == FileViewMode.GRID) {
            scroll.gridState.requestScrollToItem(pendingRevealIndex)
        } else {
            scroll.listState.requestScrollToItem(pendingRevealIndex)
        }
    }

    var restoredScrollKey by remember { mutableStateOf<String?>(null) }
    var lastScrollResetKey by rememberSaveable { mutableStateOf(scroll.positionKey) }
    var pendingScrollReset by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scroll.positionKey) {
        if (scroll.positionKey != lastScrollResetKey) {
            pendingScrollReset = true
            lastScrollResetKey = scroll.positionKey
        }
    }
    LaunchedEffect(scroll.positionKey, displayedFiles.size) {
        if (pendingScrollReset && displayedFiles.isNotEmpty()) {
            scroll.listState.scrollToItem(0)
            scroll.gridState.scrollToItem(0)
            scroll.onClearPosition(scroll.positionKey)
            restoredScrollKey = scroll.positionKey
            pendingScrollReset = false
        }
    }
    LaunchedEffect(
        scroll.positionKey,
        resumeRestoreTick,
        displayedFiles.size,
        state.displayState.visibleListRows.size,
        state.displayState.visibleGridRows.size,
        pendingScrollReset
    ) {
        if (!pendingScrollReset && displayedFiles.isNotEmpty()) {
            val shouldRestore = restoredScrollKey != scroll.positionKey || resumeRestoreTick > 0
            if (shouldRestore) scroll.savedPositionProvider(scroll.positionKey)?.let { position ->
                scroll.restorePosition(state, position)
            }
            restoredScrollKey = scroll.positionKey
        }
    }
    LaunchedEffect(
        scroll.pendingRevealFilePath,
        scroll.pendingRevealReady,
        displayedFiles.size,
        state.browserViewMode
    ) {
        val revealPath = scroll.pendingRevealFilePath
        val index = pendingRevealIndex
        if (scroll.pendingRevealReady && !revealPath.isNullOrBlank() && index != null) {
            if (state.browserViewMode == FileViewMode.GRID) {
                scroll.gridState.scrollToItem(index)
            } else {
                scroll.listState.scrollToItem(index)
            }
            scroll.onSavePosition(
                scroll.positionKey,
                BrowserScrollPosition(index, 0, index, 0)
            )
            scroll.onConsumePendingReveal(revealPath)
            restoredScrollKey = scroll.positionKey
        }
    }
    LaunchedEffect(
        scroll.positionKey,
        state.displayState.visibleListRows.size,
        state.displayState.visibleGridRows.size,
        restoredScrollKey,
        scroll.listState,
        scroll.gridState
    ) {
        snapshotFlow {
            BrowserScrollCapture(
                hasRows = state.displayState.visibleListRows.isNotEmpty() ||
                    state.displayState.visibleGridRows.isNotEmpty(),
                isScrollInProgress = scroll.listState.isScrollInProgress ||
                    scroll.gridState.isScrollInProgress,
                position = BrowserScrollPosition(
                    listIndex = scroll.listState.firstVisibleItemIndex,
                    listOffset = scroll.listState.firstVisibleItemScrollOffset,
                    gridIndex = scroll.gridState.firstVisibleItemIndex,
                    gridOffset = scroll.gridState.firstVisibleItemScrollOffset
                )
            )
        }.distinctUntilChanged().collect { capture ->
            if (capture.hasRows && restoredScrollKey == scroll.positionKey) {
                if (
                    (!capture.isScrollInProgress && !capture.position.isAtTop()) ||
                    (capture.isScrollInProgress && capture.position.isAtTop())
                ) {
                    scroll.onSavePosition(scroll.positionKey, capture.position)
                } else {
                    scroll.savedPositionProvider(scroll.positionKey)
                        ?.takeUnless { it.isAtTop() }
                        ?.let { scroll.restorePosition(state, it) }
                }
            }
        }
    }
}

private suspend fun BrowserScrollBindings.restorePosition(
    state: BrowserUiState,
    position: BrowserScrollPosition
) {
    val listIndex = position.listIndex.coerceAtMost(
        state.displayState.visibleListRows.lastIndex.coerceAtLeast(0)
    )
    val gridIndex = position.gridIndex.coerceAtMost(
        state.displayState.visibleGridRows.lastIndex.coerceAtLeast(0)
    )
    if (state.browserViewMode == FileViewMode.GRID) {
        gridState.scrollToItem(gridIndex, position.gridOffset)
    } else {
        listState.scrollToItem(listIndex, position.listOffset)
    }
}

private data class BrowserScrollCapture(
    val hasRows: Boolean,
    val isScrollInProgress: Boolean,
    val position: BrowserScrollPosition
)

private fun BrowserScrollPosition.isAtTop(): Boolean =
    listIndex == 0 && listOffset == 0 && gridIndex == 0 && gridOffset == 0
