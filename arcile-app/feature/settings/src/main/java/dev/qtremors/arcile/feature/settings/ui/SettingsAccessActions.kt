package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.privilege.PrivilegeMode

internal data class SettingsAccessActions(
    val selectMode: (PrivilegeMode) -> Unit,
    val reconnect: () -> Unit,
    val useNormal: () -> Unit,
    val grantNormalPermission: () -> Unit,
    val openShizukuManager: () -> Unit,
    val protectedWritesChange: (Boolean) -> Unit
)
