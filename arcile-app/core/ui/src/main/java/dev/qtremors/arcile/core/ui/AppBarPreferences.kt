package dev.qtremors.arcile.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

val LocalKeepAppBarsCollapsed = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun arcileLargeTopAppBarHeight(forceCompact: Boolean = false): Dp =
    if (forceCompact || LocalKeepAppBarsCollapsed.current) {
        TopAppBarDefaults.TopAppBarExpandedHeight
    } else {
        TopAppBarDefaults.LargeAppBarExpandedHeight
    }
