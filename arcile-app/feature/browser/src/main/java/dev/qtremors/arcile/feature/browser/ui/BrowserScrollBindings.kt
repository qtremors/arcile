package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import dev.qtremors.arcile.feature.browser.BrowserScrollPosition

@Stable
internal data class BrowserScrollBindings(
    val listState: LazyListState,
    val gridState: LazyGridState,
    val positionKey: String,
    val savedPosition: BrowserScrollPosition?,
    val savedPositionProvider: (String) -> BrowserScrollPosition?,
    val onSavePosition: (String, BrowserScrollPosition) -> Unit,
    val onClearPosition: (String) -> Unit,
    val pendingRevealFilePath: String?,
    val pendingRevealReady: Boolean,
    val onArmPendingReveal: () -> Unit,
    val onConsumePendingReveal: (String) -> Unit
)
