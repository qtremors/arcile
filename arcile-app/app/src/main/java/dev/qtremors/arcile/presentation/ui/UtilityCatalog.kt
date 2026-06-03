package dev.qtremors.arcile.presentation.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.ZoomIn
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
    UtilityDefinition("trash", R.string.trash_bin, Icons.Default.Delete, isImplemented = true, showOnHome = true, action = UtilityAction.Trash),
    UtilityDefinition("cleaner", R.string.tool_clean, Icons.Default.CleaningServices, isImplemented = true, showOnHome = true, action = UtilityAction.Cleaner),
    UtilityDefinition("ftp", R.string.tool_ftp, Icons.Default.WifiTethering),
    UtilityDefinition("analyze", R.string.tool_analyze, Icons.Default.PieChart),
    UtilityDefinition("duplicates", R.string.tool_duplicates, Icons.Default.FilterNone),
    UtilityDefinition("large", R.string.tool_large, Icons.Default.ZoomIn),
    UtilityDefinition("manager", R.string.tool_manager, Icons.Default.Apps),
    UtilityDefinition("onlyfiles", R.string.tool_onlyfiles, Icons.Default.Lock),
    UtilityDefinition("share", R.string.tool_share, Icons.Default.Dns)
).distinctBy { it.id }

val HomeUtilityCatalog: List<UtilityDefinition> =
    ArcileUtilityCatalog.filter { it.showOnHome && it.isImplemented }.distinctBy { it.id }
