package dev.qtremors.arcile.core.ui.lists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import dev.qtremors.arcile.core.ui.R

@Composable
fun Modifier.fileItemSemantics(
    file: FileModel,
    isSelected: Boolean,
    formattedDate: String,
    folderStatsText: String?,
    isInSelectionMode: Boolean,
    fileSizeText: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenDirectly: () -> Unit,
    onToggleSelectionDirectly: () -> Unit
): Modifier {
    val nameText = if (file.name.startsWith(".")) {
        stringResource(R.string.file_item_hidden_name, file.name)
    } else {
        file.name
    }
    val typeText = stringResource(if (file.isDirectory) R.string.folder_label else R.string.file_item_type_file)
    val modifiedText = stringResource(R.string.file_item_modified, formattedDate)
    val openLabel = stringResource(if (file.isDirectory) R.string.file_item_open_folder else R.string.open_file)
    val toggleSelectionLabel = stringResource(R.string.file_item_toggle_selection)
    val selectionOptionsLabel = stringResource(R.string.file_item_selection_options)
    val selectLabel = stringResource(R.string.file_item_select)
    val unselectLabel = stringResource(R.string.file_item_unselect)
    return this.semantics(mergeDescendants = true) {
        selected = isSelected
        role = Role.Button
        val sizeOrStats = if (file.isDirectory) {
            folderStatsText ?: ""
        } else {
            fileSizeText ?: ""
        }

        contentDescription = buildString {
            append(nameText)
            append(", ")
            append(typeText)
            if (sizeOrStats.isNotEmpty()) {
                append(", ")
                append(sizeOrStats)
            }
            append(", ")
            append(modifiedText)
        }

        onClick(label = if (isInSelectionMode) toggleSelectionLabel else openLabel) {
            onClick()
            true
        }

        onLongClick(label = selectionOptionsLabel) {
            onLongClick()
            true
        }

        customActions = if (isInSelectionMode) {
            listOf(
                CustomAccessibilityAction(
                    label = if (isSelected) unselectLabel else selectLabel,
                    action = {
                        onToggleSelectionDirectly()
                        true
                    }
                ),
                CustomAccessibilityAction(
                    label = openLabel,
                    action = {
                        onOpenDirectly()
                        true
                    }
                )
            )
        } else {
            listOf(
                CustomAccessibilityAction(
                    label = selectLabel,
                    action = {
                        onToggleSelectionDirectly()
                        true
                    }
                )
            )
        }
    }
}
