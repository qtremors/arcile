package dev.qtremors.arcile.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qtremors.arcile.core.ui.R

enum class UtilityAction {
    None,
    Trash,
    Cleaner
}

data class UtilityDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    val icon: ImageVector,
    val isImplemented: Boolean = false,
    val showOnHome: Boolean = false,
    val action: UtilityAction = UtilityAction.None
)

val ArcileUtilityCatalog: List<UtilityDefinition> = listOf(
    UtilityDefinition("trash", R.string.trash_bin, Icons.Outlined.Delete, isImplemented = true, showOnHome = true, action = UtilityAction.Trash),
    UtilityDefinition("cleaner", R.string.tool_clean, Icons.Outlined.CleaningServices, isImplemented = true, showOnHome = true, action = UtilityAction.Cleaner),
    UtilityDefinition("ftp", R.string.tool_ftp, Icons.Outlined.WifiTethering),
    UtilityDefinition("manager", R.string.tool_manager, Icons.Outlined.Apps),
    UtilityDefinition("onlyfiles", R.string.tool_onlyfiles, Icons.Outlined.Lock),
    UtilityDefinition("share", R.string.tool_share, Icons.Outlined.Dns)
).distinctBy { it.id }

val HomeUtilityCatalog: List<UtilityDefinition> =
    ArcileUtilityCatalog.filter { it.showOnHome && it.isImplemented }.distinctBy { it.id }
