package dev.qtremors.arcile.feature.settings.ui

internal data class SettingsAccessActions(
    val shizukuEnabledChange: (Boolean) -> Unit,
    val grantNormalPermission: () -> Unit,
    val openShizukuManager: () -> Unit,
    val protectedWritesChange: (Boolean) -> Unit
)
