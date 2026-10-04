package dev.qtremors.arcile.core.ui.viewer

import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import dev.qtremors.arcile.core.presentation.UiText

data class ViewerActionState(
    val allowedActions: Set<ViewerFileAction> = emptySet(),
    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showPropertiesDialog: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val isPermanentDeleteOnly: Boolean = false,
    val isPermanentDeleteChecked: Boolean = false,
    val error: UiText? = null
)
