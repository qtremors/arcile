package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    state: OnlyFilesUiState,
    viewModel: OnlyFilesViewModel,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onExport: () -> Unit,
    onMoveOut: () -> Unit
) {
    var more by remember { mutableStateOf(false) }
    val menuActions = remember(state.selectedNodes) {
        listOf<@Composable () -> Unit>(
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_rename)) },
                    enabled = state.selectedNodes.size == 1,
                    onClick = { more = false; onRename() }
                )
            },
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_open_with)) },
                    enabled = state.selectedNodes.singleOrNull()?.isDirectory == false,
                    onClick = { more = false; onOpenWith() }
                )
            },
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_export)) },
                    onClick = { more = false; onExport() }
                )
            },
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_move_out)) },
                    onClick = { more = false; onMoveOut() }
                )
            },
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_invert_selection)) },
                    onClick = { more = false; viewModel.invertSelection() }
                )
            },
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_properties)) },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    onClick = { more = false; viewModel.showProperties(state.selectedNodes) }
                )
            },
            {
                ArcileDropdownMenuItem(
                    text = { Text(stringResource(R.string.onlyfiles_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { more = false; onDelete() }
                )
            }
        )
    }

    LargeTopAppBar(
        title = {
            Text(pluralStringResource(
                R.plurals.onlyfiles_selected_count,
                state.selectedNodeIds.size,
                state.selectedNodeIds.size
            ))
        },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable { viewModel.clearSelection() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.onlyfiles_close))
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable { viewModel.selectAll() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SelectAll, stringResource(R.string.onlyfiles_select_all))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable { viewModel.copy(state.selectedNodes) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.onlyfiles_copy))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable { viewModel.move(state.selectedNodes) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ContentCut, stringResource(R.string.onlyfiles_move))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable { onShare() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, stringResource(R.string.onlyfiles_share))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bounceClickable { more = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.onlyfiles_more))
            }

            DropdownMenu(
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                expanded = more,
                onDismissRequest = { more = false }
            ) {
                menuActions.forEachIndexed { index, action ->
                    val shape = when {
                        menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                        index == 0 -> MaterialTheme.shapes.menuGroupFirst
                        index == menuActions.size - 1 -> MaterialTheme.shapes.menuGroupLast
                        else -> MaterialTheme.shapes.menuGroupMiddle
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        action()
                    }
                }
            }
        },
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
