package dev.qtremors.arcile.feature.browser

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
