package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.presentation.UiText

internal sealed interface BrowserInitializationState {
    data object Uninitialized : BrowserInitializationState
    data object Restoring : BrowserInitializationState
    data object Ready : BrowserInitializationState
    data class Failed(val error: UiText) : BrowserInitializationState
}
