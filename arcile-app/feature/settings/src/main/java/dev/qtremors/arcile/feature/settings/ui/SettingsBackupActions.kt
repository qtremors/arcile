package dev.qtremors.arcile.feature.settings.ui

internal data class SettingsBackupActions(
    val requestExport: () -> Unit,
    val requestRestore: () -> Unit
)
