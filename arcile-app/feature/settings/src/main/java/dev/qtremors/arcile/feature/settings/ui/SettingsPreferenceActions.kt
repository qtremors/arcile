package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.ui.theme.ThemeState

internal data class SettingsPreferenceActions(
    val themeChange: (ThemeState) -> Unit,
    val showThumbnailsChange: (Boolean) -> Unit,
    val homeRecentCarouselLimitChange: (Int) -> Unit,
    val showHiddenFilesChange: (Boolean) -> Unit,
    val browserScrollbarEnabledChange: (Boolean) -> Unit,
    val galleryScrollbarEnabledChange: (Boolean) -> Unit
)
