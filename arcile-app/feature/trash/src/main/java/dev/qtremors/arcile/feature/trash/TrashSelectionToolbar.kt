package dev.qtremors.arcile.feature.trash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.storage.domain.FileModel

@Composable
internal fun TrashSelectionToolbar(
    isVisible: Boolean,
    isBackPredicting: Boolean,
    backProgress: Float,
    contentPadding: PaddingValues,
    selectedItems: List<FileModel>,
    actions: TrashSelectionToolbarActions
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .graphicsLayer {
                if (isBackPredicting && isVisible) {
                    translationY = backProgress * 150.dp.toPx()
                    alpha = 1f - backProgress
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingSelectionToolbar(
            isVisible = isVisible,
            actions = listOf(
                ToolbarAction(
                    icon = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                    onClick = actions.selectAll
                ),
                ToolbarAction(
                    icon = Icons.Default.Restore,
                    contentDescription = stringResource(R.string.restore),
                    onClick = actions.restore
                ),
                ToolbarAction(
                    icon = Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.delete_permanently),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = actions.deletePermanently
                )
            ),
            moreContent = {
                TrashSelectionMoreMenu(
                    selectedItems = selectedItems,
                    actions = actions
                )
            }
        )
    }
}

internal data class TrashSelectionToolbarActions(
    val selectAll: () -> Unit,
    val restore: () -> Unit,
    val deletePermanently: () -> Unit,
    val open: (FileModel) -> Unit,
    val openWith: (FileModel) -> Unit,
    val share: () -> Unit,
    val openProperties: () -> Unit
)

@Composable
private fun TrashSelectionMoreMenu(
    selectedItems: List<FileModel>,
    actions: TrashSelectionToolbarActions
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            val singleFile = selectedItems.singleOrNull()?.takeUnless { it.isDirectory }
            if (singleFile != null) {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        actions.open(singleFile)
                    }
                )
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.image_gallery_open_with)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        actions.openWith(singleFile)
                    }
                )
            }
            if (selectedItems.isNotEmpty() && selectedItems.none(FileModel::isDirectory)) {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = {
                        expanded = false
                        actions.share()
                    }
                )
            }
            ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.properties_title)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        expanded = false
                        actions.openProperties()
                    }
                )
        }
    }
}
