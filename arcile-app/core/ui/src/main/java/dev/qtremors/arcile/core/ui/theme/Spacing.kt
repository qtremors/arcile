package dev.qtremors.arcile.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    
    // Intermediate values for finer control if needed, following 4dp grid
    val space12: Dp = 12.dp,
    val space20: Dp = 20.dp,

    // Semantic spacing tokens
    val screenGutter: Dp = medium,
    val listItemHorizontal: Dp = medium,
    val listItemVertical: Dp = small,
    val sheetHorizontal: Dp = space20,
    val toolbarBottomGap: Dp = 80.dp,
    val sectionGap: Dp = large,
    val compactGap: Dp = space12
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
