package dev.qtremors.arcile.core.ui.category

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CategoryShellDefaults {
    val TopContentClearance: Dp = 72.dp
    val BottomContentClearance: Dp = 96.dp
    val HiddenTopOffset: Dp = (-120).dp
    const val ScrollRevealThreshold: Float = 15f
}

@Stable
class CategoryLibraryShellState internal constructor() {
    var isChromeVisible by mutableStateOf(true)
        private set

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            when {
                available.y < -CategoryShellDefaults.ScrollRevealThreshold -> {
                    isChromeVisible = false
                }
                available.y > CategoryShellDefaults.ScrollRevealThreshold -> {
                    isChromeVisible = true
                }
            }
            return Offset.Zero
        }
    }

    fun revealChrome() {
        isChromeVisible = true
    }
}

@Composable
fun rememberCategoryLibraryShellState(): CategoryLibraryShellState =
    remember { CategoryLibraryShellState() }

/**
 * Shared image-gallery-derived shell for every dedicated category library.
 *
 * Features own their pager content, labels, item representation, and optional
 * supporting surfaces. This shell owns the geometry, scroll reveal behavior,
 * chrome motion, content insets, and predictive-back transforms.
 */
@Composable
fun CategoryLibraryShell(
    state: CategoryLibraryShellState,
    selectionMode: Boolean,
    searchVisible: Boolean,
    exitBackProgress: Float,
    chromeBackProgress: Float,
    extraBottomContentPadding: Dp = 0.dp,
    horizontalContentPadding: Dp = 0.dp,
    topChrome: @Composable () -> Unit,
    bottomChrome: @Composable BoxScope.(isChromeVisible: Boolean) -> Unit,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit
) {
    val topBarVisible = state.isChromeVisible || searchVisible || selectionMode
    val topBarOffset by animateDpAsState(
        targetValue = if (topBarVisible) 0.dp else CategoryShellDefaults.HiddenTopOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "categoryShellTopOffset"
    )
    val topBarAlpha by animateFloatAsState(
        targetValue = if (topBarVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "categoryShellTopAlpha"
    )
    val contentPadding = PaddingValues(
        start = horizontalContentPadding,
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            CategoryShellDefaults.TopContentClearance,
        end = horizontalContentPadding,
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            CategoryShellDefaults.BottomContentClearance +
            extraBottomContentPadding
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (exitBackProgress > 0f) {
                        val scale = 1f - exitBackProgress * 0.08f
                        scaleX = scale
                        scaleY = scale
                        translationX = exitBackProgress * 100.dp.toPx()
                        alpha = 1f - exitBackProgress * 0.4f
                    }
                }
        ) {
            content(contentPadding)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = topBarOffset.toPx()
                    alpha = topBarAlpha
                    if (chromeBackProgress > 0f) {
                        val scale = 1f - chromeBackProgress * 0.15f
                        scaleX = scale
                        scaleY = scale
                        translationY =
                            topBarOffset.toPx() - chromeBackProgress * 40.dp.toPx()
                        alpha = topBarAlpha * (1f - chromeBackProgress)
                    }
                }
        ) {
            topChrome()
        }

        bottomChrome(state.isChromeVisible)
    }
}
