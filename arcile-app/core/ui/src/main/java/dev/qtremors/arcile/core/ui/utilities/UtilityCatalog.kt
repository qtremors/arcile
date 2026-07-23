package dev.qtremors.arcile.core.ui.utilities

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import dev.qtremors.arcile.core.ui.R

enum class UtilityAction {
    Trash,
    Cleaner,
    Activity,
    OnlyFiles
}

data class UtilityDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    val icon: ImageVector,
    val action: UtilityAction
)

val ArcileUtilityCatalog: List<UtilityDefinition> = listOf(
    UtilityDefinition("trash", R.string.trash_bin, Icons.Outlined.Delete, UtilityAction.Trash),
    UtilityDefinition("cleaner", R.string.tool_clean, Icons.Outlined.CleaningServices, UtilityAction.Cleaner),
    UtilityDefinition("activity", R.string.activity_log_title, Icons.Outlined.History, UtilityAction.Activity),
    UtilityDefinition("onlyfiles", R.string.tool_onlyfiles, Icons.Outlined.Lock, UtilityAction.OnlyFiles)
).distinctBy { it.id }

val HomeUtilityCatalog: List<UtilityDefinition> = ArcileUtilityCatalog
