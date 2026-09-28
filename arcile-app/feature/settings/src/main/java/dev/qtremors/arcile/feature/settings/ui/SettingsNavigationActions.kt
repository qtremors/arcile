package dev.qtremors.arcile.feature.settings.ui

internal data class SettingsNavigationActions(
    val navigateBack: () -> Unit,
    val navigateToPlugins: () -> Unit,
    val navigateToAbout: () -> Unit
)
