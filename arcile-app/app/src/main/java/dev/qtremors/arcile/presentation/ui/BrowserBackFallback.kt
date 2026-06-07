package dev.qtremors.arcile.presentation.ui

internal enum class BrowserBackFallback {
    PopAppBackStack,
    ShowHomePager
}

internal fun browserBackFallback(hasPreviousBackStackEntry: Boolean): BrowserBackFallback =
    if (hasPreviousBackStackEntry) {
        BrowserBackFallback.PopAppBackStack
    } else {
        BrowserBackFallback.ShowHomePager
    }