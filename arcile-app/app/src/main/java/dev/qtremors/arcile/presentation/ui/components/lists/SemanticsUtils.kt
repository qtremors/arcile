package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.utils.formatFileSize

/**
 * A custom modifier that applies accessibility semantics for file/folder items.
 * Merges descendants so TalkBack reads the entire row/card as one unified element.
 */
fun Modifier.fileItemSemantics(
    file: FileModel,
    isSelected: Boolean,
    formattedDate: String,
    folderStatsText: String?,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenDirectly: () -> Unit,
    onToggleSelectionDirectly: () -> Unit
): Modifier = this.semantics(mergeDescendants = true) {
    selected = isSelected
    role = Role.Button
    
    val nameText = if (file.name.startsWith(".")) {
        "${file.name} (Hidden)"
    } else {
        file.name
    }
    
    val typeText = if (file.isDirectory) "Folder" else "File"
    val sizeOrStats = if (file.isDirectory) {
        folderStatsText ?: ""
    } else {
        formatFileSize(file.size)
    }
    
    contentDescription = buildString {
        append(nameText)
        append(", ")
        append(typeText)
        if (sizeOrStats.isNotEmpty()) {
            append(", ")
            append(sizeOrStats)
        }
        append(", Modified ")
        append(formattedDate)
    }

    onClick(label = if (isInSelectionMode) "Toggle selection" else if (file.isDirectory) "Open folder" else "Open file") {
        onClick()
        true
    }

    onLongClick(label = "Show selection options") {
        onLongClick()
        true
    }

    customActions = if (isInSelectionMode) {
        listOf(
            CustomAccessibilityAction(
                label = if (isSelected) "Unselect item" else "Select item",
                action = {
                    onToggleSelectionDirectly()
                    true
                }
            ),
            CustomAccessibilityAction(
                label = if (file.isDirectory) "Open folder" else "Open file",
                action = {
                    onOpenDirectly()
                    true
                }
            )
        )
    } else {
        listOf(
            CustomAccessibilityAction(
                label = "Select item",
                action = {
                    onToggleSelectionDirectly()
                    true
                }
            )
        )
    }
}
