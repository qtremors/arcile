package dev.qtremors.arcile.feature.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.FloatingSelectionToolbar
import dev.qtremors.arcile.core.ui.ToolbarAction

@Composable
internal fun TrashSelectionToolbar(
    isVisible: Boolean,
    isBackPredicting: Boolean,
    backProgress: Float,
    contentPadding: PaddingValues,
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
                TrashSelectionMoreMenu(onOpenProperties = actions.openProperties)
            }
        )
    }
}

internal data class TrashSelectionToolbarActions(
    val selectAll: () -> Unit,
    val restore: () -> Unit,
    val deletePermanently: () -> Unit,
    val openProperties: () -> Unit
)

@Composable
private fun TrashSelectionMoreMenu(onOpenProperties: () -> Unit) {
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
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.properties_title)) },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onOpenProperties()
                    }
                )
            }
        }
    }
}
