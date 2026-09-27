package dev.qtremors.arcile.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity

val LocalKeepAppBarsCollapsed = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.arcileTopAppBarNestedScroll(scrollBehavior: TopAppBarScrollBehavior): Modifier = composed {
    if (LocalKeepAppBarsCollapsed.current) this else nestedScroll(scrollBehavior.nestedScrollConnection)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun arcileTopAppBarScrollBehavior(
    scrollBehavior: TopAppBarScrollBehavior? = null,
    forceCompact: Boolean = false
): TopAppBarScrollBehavior? {
    if (!forceCompact && !LocalKeepAppBarsCollapsed.current) return scrollBehavior

    // Keep the two-row bar's real collapse range so its compact title is fully visible.
    val collapsedOffset = with(LocalDensity.current) {
        -(TopAppBarDefaults.LargeAppBarExpandedHeight - TopAppBarDefaults.TopAppBarExpandedHeight).toPx()
    }
    val state = remember(collapsedOffset) {
        TopAppBarState(collapsedOffset, collapsedOffset, 0f)
    }
    // Pinning also disables direct dragging on the bar, not just nested scrolling.
    return TopAppBarDefaults.pinnedScrollBehavior(state = state, canScroll = { false })
}
